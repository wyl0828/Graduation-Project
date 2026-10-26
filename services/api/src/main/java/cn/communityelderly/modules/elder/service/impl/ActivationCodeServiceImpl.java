package cn.communityelderly.modules.elder.service.impl;

import cn.communityelderly.common.api.ErrorCode;
import cn.communityelderly.common.exception.BusinessException;
import cn.communityelderly.common.exception.ForbiddenException;
import cn.communityelderly.common.security.CurrentUser;
import cn.communityelderly.common.security.ObjectAuthorizationPolicy;
import cn.communityelderly.modules.auth.dto.AuthTokenResponse;
import cn.communityelderly.modules.auth.entity.AuthSession;
import cn.communityelderly.modules.auth.entity.UserAccount;
import cn.communityelderly.modules.auth.entity.UserRole;
import cn.communityelderly.modules.auth.mapper.AuthSessionMapper;
import cn.communityelderly.modules.auth.mapper.UserAccountMapper;
import cn.communityelderly.modules.auth.mapper.UserRoleMapper;
import cn.communityelderly.modules.auth.service.impl.PhoneVerificationChallengeService;
import cn.communityelderly.modules.elder.dto.ActivationCodeIssueDTO;
import cn.communityelderly.modules.elder.dto.ActivationConsumeDTO;
import cn.communityelderly.modules.elder.dto.ActivationTicketVO;
import cn.communityelderly.modules.elder.entity.ElderActivationTicket;
import cn.communityelderly.modules.elder.entity.ElderIdentityLink;
import cn.communityelderly.modules.elder.entity.ElderProfile;
import cn.communityelderly.modules.elder.mapper.ElderActivationTicketMapper;
import cn.communityelderly.modules.elder.mapper.ElderIdentityLinkMapper;
import cn.communityelderly.modules.elder.mapper.ElderProfileMapper;
import cn.communityelderly.modules.elder.service.ActivationCodeService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class ActivationCodeServiceImpl implements ActivationCodeService {

    private final ElderProfileMapper elderProfileMapper;
    private final ElderActivationTicketMapper activationTicketMapper;
    private final ElderIdentityLinkMapper identityLinkMapper;
    private final UserAccountMapper userAccountMapper;
    private final UserRoleMapper userRoleMapper;
    private final AuthSessionMapper authSessionMapper;
    private final PhoneVerificationChallengeService phoneVerificationChallengeService;
    private final ObjectAuthorizationPolicy authorizationPolicy;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final SecureRandom secureRandom = new SecureRandom();

    public ActivationCodeServiceImpl(ElderProfileMapper elderProfileMapper,
                                     ElderActivationTicketMapper activationTicketMapper,
                                     ElderIdentityLinkMapper identityLinkMapper,
                                     UserAccountMapper userAccountMapper,
                                     UserRoleMapper userRoleMapper,
                                     AuthSessionMapper authSessionMapper,
                                     PhoneVerificationChallengeService phoneVerificationChallengeService,
                                     ObjectAuthorizationPolicy authorizationPolicy) {
        this.elderProfileMapper = elderProfileMapper;
        this.activationTicketMapper = activationTicketMapper;
        this.identityLinkMapper = identityLinkMapper;
        this.userAccountMapper = userAccountMapper;
        this.userRoleMapper = userRoleMapper;
        this.authSessionMapper = authSessionMapper;
        this.phoneVerificationChallengeService = phoneVerificationChallengeService;
        this.authorizationPolicy = authorizationPolicy;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ActivationTicketVO issueActivationTicket(Long elderId, ActivationCodeIssueDTO dto) {
        ElderProfile profile = authorizationPolicy.requireElderInCurrentCommunity(elderId, "COMMUNITY_ADMIN");
        Long operatorId = currentUserId();

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime oneDayAgo = now.minusHours(24);

        // 检查 24 小时内签发次数频控 (不超过 3 次)
        Long recentCount = activationTicketMapper.selectCount(
                new LambdaQueryWrapper<ElderActivationTicket>()
                        .eq(ElderActivationTicket::getElderId, elderId)
                        .ge(ElderActivationTicket::getIssuedAt, oneDayAgo)
        );
        if (recentCount != null && recentCount >= 3) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "24小时内重签次数已达上限 (3次)，请明日再试");
        }

        // 同事务内将该老人既有 ISSUED 激活码置为 SUPERSEDED (旧码立即作废)
        List<ElderActivationTicket> oldTickets = activationTicketMapper.selectList(
                new LambdaQueryWrapper<ElderActivationTicket>()
                        .eq(ElderActivationTicket::getElderId, elderId)
                        .eq(ElderActivationTicket::getStatus, "ISSUED")
        );
        for (ElderActivationTicket old : oldTickets) {
            old.setStatus("SUPERSEDED");
            old.setInvalidatedAt(now);
            old.setInvalidationReason("管理员重新签发，旧码自动失效作废");
            activationTicketMapper.updateById(old);
        }

        // 生成 6 位随机纯数字码
        int randomNum = secureRandom.nextInt(1000000);
        String plainCode = String.format("%06d", randomNum);
        byte[] codeMac = sha256(plainCode);

        String ticketId = UUID.randomUUID().toString();
        ElderActivationTicket ticket = new ElderActivationTicket();
        ticket.setTicketId(ticketId);
        ticket.setElderId(elderId);
        ticket.setCodeMac(codeMac);
        ticket.setStatus("ISSUED");
        ticket.setIssuedBy(operatorId);
        ticket.setIssueMode(StringUtils.hasText(dto.getIssueMode()) ? dto.getIssueMode() : "IN_PERSON");
        ticket.setIdentityCheckResult("VERIFIED");
        ticket.setSpecialReason(dto.getSpecialReason());
        ticket.setProxyBasisType(dto.getProxyBasisType());
        ticket.setIssuedAt(now);
        ticket.setExpiresAt(now.plusHours(24));
        ticket.setFailedAttempts(0);

        activationTicketMapper.insert(ticket);

        ActivationTicketVO vo = new ActivationTicketVO();
        vo.setTicketId(ticketId);
        vo.setElderId(elderId);
        vo.setElderName(profile.getName());
        vo.setActivationCodePlain(plainCode);
        vo.setStatus("ISSUED");
        vo.setIssuedAt(now);
        vo.setExpiresAt(ticket.getExpiresAt());
        vo.setRemainingHours(24);
        vo.setRemainingTries(5);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AuthTokenResponse consumeActivationCode(ActivationConsumeDTO dto) {
        validateActivationRequest(dto);
        String ticketId = dto.getTicketId().trim();
        String inputCode = dto.getActivationCode().trim();
        byte[] inputMac = sha256(inputCode);

        ElderActivationTicket ticket = activationTicketMapper.selectById(ticketId);
        if (ticket == null) {
            throw new BusinessException(ErrorCode.ACTIVATION_CODE_INVALID, "未找到有效的激活码凭证，请核对后重试");
        }
        if ("LOCKED".equalsIgnoreCase(ticket.getStatus())) {
            throw new BusinessException(ErrorCode.ACTIVATION_CODE_LOCKED, "该激活码因连续输错5次已被锁死，请联系社区重新签发");
        }
        if ("USED".equalsIgnoreCase(ticket.getStatus())) {
            throw new BusinessException(ErrorCode.ACTIVATION_CODE_USED, "该激活码已被消费使用，不可重复激活");
        }
        if (!"ISSUED".equalsIgnoreCase(ticket.getStatus())) {
            throw new BusinessException(ErrorCode.ACTIVATION_CODE_INVALID, "该激活码已失效，请联系社区重新签发");
        }

        LocalDateTime now = LocalDateTime.now(java.time.ZoneId.of("Asia/Shanghai"));
        if (ticket.getExpiresAt() == null || !now.isBefore(ticket.getExpiresAt())) {
            ticket.setStatus("EXPIRED");
            activationTicketMapper.updateById(ticket);
            throw new BusinessException(ErrorCode.ACTIVATION_CODE_EXPIRED, "激活码已超过24小时有效期，请联系社区重新签发");
        }

        if (ticket.getCodeMac() == null || !MessageDigest.isEqual(ticket.getCodeMac(), inputMac)) {
            int attempts = (ticket.getFailedAttempts() == null ? 0 : ticket.getFailedAttempts()) + 1;
            ticket.setFailedAttempts(attempts);
            if (attempts >= 5) {
                ticket.setStatus("LOCKED");
                activationTicketMapper.updateById(ticket);
                throw new BusinessException(ErrorCode.ACTIVATION_CODE_LOCKED, "连续输错5次，激活码已被锁死，请联系社区重新签发");
            }
            activationTicketMapper.updateById(ticket);
            throw new BusinessException(ErrorCode.ACTIVATION_CODE_INVALID, "激活码输入错误，请核对后重试");
        }

        phoneVerificationChallengeService.verifyAndConsume(
                dto.getPhoneChallengeId(), dto.getPhone(), "ACTIVATION", ticketId, dto.getVerificationCode());

        ElderProfile elder = elderProfileMapper.selectById(ticket.getElderId());
        if (elder == null) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND, "老人档案不存在");
        }

        String loginPhone = dto.getPhone().trim();
        UserAccount elderUser = userAccountMapper.selectOne(
                new LambdaQueryWrapper<UserAccount>()
                        .eq(UserAccount::getPhoneNormalized, loginPhone)
        );
        if (elderUser == null) {
            elderUser = new UserAccount();
            elderUser.setAccountKind("FAMILY");
            elderUser.setPhoneNormalized(loginPhone);
            elderUser.setPasswordHash(passwordEncoder.encode(dto.getPassword()));
            elderUser.setStatus("ACTIVE");
            elderUser.setPasswordChangedAt(now);
            elderUser.setCreatedAt(now);
            elderUser.setUpdatedAt(now);
            userAccountMapper.insert(elderUser);
        } else if (!"ACTIVE".equalsIgnoreCase(elderUser.getStatus())
                || !"FAMILY".equalsIgnoreCase(elderUser.getAccountKind())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS, ErrorCode.INVALID_CREDENTIALS.getDefaultMessage());
        }

        UserRole elderAssignment = userRoleMapper.selectOne(
                new LambdaQueryWrapper<UserRole>()
                        .eq(UserRole::getUserId, elderUser.getUserId())
                        .eq(UserRole::getRoleCode, "ELDER")
                        .eq(UserRole::getCommunityId, elder.getCommunityId())
                        .eq(UserRole::getStatus, "ACTIVE")
                        .last("LIMIT 1")
        );
        if (elderAssignment == null) {
            elderAssignment = new UserRole();
            elderAssignment.setUserId(elderUser.getUserId());
            elderAssignment.setRoleCode("ELDER");
            elderAssignment.setCommunityId(elder.getCommunityId());
            elderAssignment.setStatus("ACTIVE");
            elderAssignment.setGrantedBy(ticket.getIssuedBy());
            elderAssignment.setGrantedAt(now);
            userRoleMapper.insert(elderAssignment);
        }
        if (elderAssignment.getAssignmentId() == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN_ACCESS, "账号未分配当前社区的有效老人角色");
        }

        ElderIdentityLink identityLink = identityLinkMapper.selectOne(
                new LambdaQueryWrapper<ElderIdentityLink>()
                        .eq(ElderIdentityLink::getElderId, elder.getElderId())
        );
        if (identityLink != null && !elderUser.getUserId().equals(identityLink.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN_ACCESS, "该老人身份已关联其他账号");
        }
        if (identityLink == null) {
            identityLink = new ElderIdentityLink();
            identityLink.setElderId(elder.getElderId());
            identityLink.setUserId(elderUser.getUserId());
            identityLink.setLinkedByActivationTicketId(ticket.getTicketId());
            identityLink.setLinkedAt(now);
            identityLinkMapper.insert(identityLink);
        }

        ticket.setStatus("USED");
        ticket.setUsedAt(now);
        ticket.setUsedByUserId(elderUser.getUserId());
        ticket.setUsedIdempotencyKey(UUID.randomUUID().toString());
        activationTicketMapper.updateById(ticket);

        List<UserRole> activeRoles = userRoleMapper.selectList(
                new LambdaQueryWrapper<UserRole>()
                        .eq(UserRole::getUserId, elderUser.getUserId())
                        .eq(UserRole::getStatus, "ACTIVE")
                        .orderByAsc(UserRole::getAssignmentId)
        );
        List<String> roleCodes = activeRoles.stream()
                .map(UserRole::getRoleCode)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        if (!roleCodes.contains("ELDER")) {
            throw new BusinessException(ErrorCode.FORBIDDEN_ACCESS, "账号未分配有效老人角色");
        }

        String rawToken = UUID.randomUUID().toString().replace("-", "");
        AuthSession session = new AuthSession();
        session.setSessionId(UUID.randomUUID().toString());
        session.setUserId(elderUser.getUserId());
        session.setTokenHash(sha256(rawToken));
        session.setClientType("MINIAPP");
        session.setActiveAssignmentId(elderAssignment.getAssignmentId());
        session.setCreatedAt(now);
        session.setLastActivityAt(now);
        session.setAbsoluteExpiresAt(now.plusDays(30));
        authSessionMapper.insert(session);

        return new AuthTokenResponse(
                rawToken,
                elderUser.getUserId(),
                elderUser.getPhoneNormalized(),
                elderUser.getAccountKind(),
                roleCodes,
                "ELDER",
                String.valueOf(elder.getCommunityId()),
                session.getAbsoluteExpiresAt()
        );
    }

    private void validateActivationRequest(ActivationConsumeDTO request) {
        if (request == null
                || !StringUtils.hasText(request.getTicketId())
                || !StringUtils.hasText(request.getPhone())
                || !request.getPhone().trim().matches("^1[3-9]\\d{9}$")
                || !StringUtils.hasText(request.getPhoneChallengeId())
                || !StringUtils.hasText(request.getVerificationCode())
                || !request.getVerificationCode().trim().matches("^\\d{6}$")
                || !StringUtils.hasText(request.getActivationCode())
                || !request.getActivationCode().trim().matches("^\\d{6}$")
                || !StringUtils.hasText(request.getPassword())
                || request.getPassword().codePointCount(0, request.getPassword().length()) < 8
                || request.getPassword().codePointCount(0, request.getPassword().length()) > 128
                || request.getPassword().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "首次激活需要票据、激活码、已验证手机号和符合要求的初始密码");
        }
    }
    @Override
    public ActivationTicketVO getActiveTicket(Long elderId) {
        ElderProfile profile = authorizationPolicy.requireElderInCurrentCommunity(elderId, "COMMUNITY_ADMIN");
        ElderActivationTicket ticket = activationTicketMapper.selectOne(
                new LambdaQueryWrapper<ElderActivationTicket>()
                        .eq(ElderActivationTicket::getElderId, elderId)
                        .eq(ElderActivationTicket::getStatus, "ISSUED")
                        .gt(ElderActivationTicket::getExpiresAt, LocalDateTime.now())
                        .orderByDesc(ElderActivationTicket::getIssuedAt)
                        .last("LIMIT 1")
        );

        if (ticket == null) {
            return null;
        }

        ActivationTicketVO vo = new ActivationTicketVO();
        vo.setTicketId(ticket.getTicketId());
        vo.setElderId(elderId);
        vo.setElderName(profile != null ? profile.getName() : "");
        vo.setStatus(ticket.getStatus());
        vo.setIssuedAt(ticket.getIssuedAt());
        vo.setExpiresAt(ticket.getExpiresAt());
        long hours = Duration.between(LocalDateTime.now(), ticket.getExpiresAt()).toHours();
        vo.setRemainingHours((int) Math.max(0, hours));
        vo.setRemainingTries(5 - (ticket.getFailedAttempts() != null ? ticket.getFailedAttempts() : 0));
        return vo;
    }

    private Long currentUserId() {
        CurrentUser currentUser = authorizationPolicy.requireCurrentUser();
        try {
            return Long.parseLong(currentUser.getUserId());
        } catch (RuntimeException exception) {
            throw new ForbiddenException(ErrorCode.ACCESS_DENIED, "当前登录身份无效");
        }
    }

    private byte[] sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(input.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
}
