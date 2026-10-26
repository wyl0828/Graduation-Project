package cn.communityelderly.modules.elder;

import cn.communityelderly.common.exception.BusinessException;
import cn.communityelderly.common.exception.UnauthorizedException;
import cn.communityelderly.common.security.CurrentUser;
import cn.communityelderly.common.security.ObjectAuthorizationPolicy;
import cn.communityelderly.common.security.UserContext;
import cn.communityelderly.modules.auth.mapper.AuthSessionMapper;
import cn.communityelderly.modules.auth.mapper.UserAccountMapper;
import cn.communityelderly.modules.auth.mapper.UserRoleMapper;
import cn.communityelderly.modules.auth.service.impl.PhoneVerificationChallengeService;
import cn.communityelderly.modules.auth.dto.AuthTokenResponse;
import cn.communityelderly.modules.auth.entity.AuthSession;
import cn.communityelderly.modules.auth.entity.UserAccount;
import cn.communityelderly.modules.auth.entity.UserRole;
import cn.communityelderly.modules.elder.dto.ActivationConsumeDTO;
import cn.communityelderly.modules.elder.dto.ActivationCodeIssueDTO;
import cn.communityelderly.modules.elder.entity.ElderActivationTicket;
import cn.communityelderly.modules.elder.entity.ElderFamilyLink;
import cn.communityelderly.modules.elder.entity.ElderIdentityLink;
import cn.communityelderly.modules.elder.entity.ElderProfile;
import cn.communityelderly.modules.elder.mapper.ElderActivationTicketMapper;
import cn.communityelderly.modules.elder.mapper.ElderIdentityLinkMapper;
import cn.communityelderly.modules.elder.mapper.ElderFamilyLinkMapper;
import cn.communityelderly.modules.elder.mapper.ElderProfileMapper;
import cn.communityelderly.modules.elder.service.impl.ActivationCodeServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import com.baomidou.mybatisplus.core.conditions.Wrapper;

class ActivationCodeServiceSecurityTest {

    private ElderProfileMapper elderProfileMapper;
    private ElderActivationTicketMapper ticketMapper;
    private ElderIdentityLinkMapper identityLinkMapper;
    private ElderFamilyLinkMapper familyLinkMapper;
    private UserAccountMapper accountMapper;
    private UserRoleMapper roleMapper;
    private AuthSessionMapper sessionMapper;
    private PhoneVerificationChallengeService phoneVerificationChallengeService;
    private ActivationCodeServiceImpl service;

    @BeforeEach
    void setUp() {
        elderProfileMapper = mock(ElderProfileMapper.class);
        ticketMapper = mock(ElderActivationTicketMapper.class);
        identityLinkMapper = mock(ElderIdentityLinkMapper.class);
        familyLinkMapper = mock(ElderFamilyLinkMapper.class);
        accountMapper = mock(UserAccountMapper.class);
        roleMapper = mock(UserRoleMapper.class);
        sessionMapper = mock(AuthSessionMapper.class);
        phoneVerificationChallengeService = mock(PhoneVerificationChallengeService.class);
        ObjectAuthorizationPolicy authorizationPolicy = new ObjectAuthorizationPolicy(
                elderProfileMapper, identityLinkMapper, familyLinkMapper);
        service = new ActivationCodeServiceImpl(
                elderProfileMapper, ticketMapper, identityLinkMapper,
                accountMapper, roleMapper, sessionMapper, phoneVerificationChallengeService, authorizationPolicy);
    }

    @AfterEach
    void clearUserContext() {
        UserContext.clear();
    }

    @Test
    @DisplayName("匿名用户不得签发老人激活码")
    void anonymousIssueIsRejectedBeforeReadingElder() {
        assertThrows(UnauthorizedException.class,
                () -> service.issueActivationTicket(10L, new ActivationCodeIssueDTO()));
        verifyNoInteractions(elderProfileMapper, ticketMapper);
    }

    @Test
    @DisplayName("外社区管理员不得为其他社区老人签发激活码")
    void communityAdminCannotIssueTicketForAnotherCommunity() {
        UserContext.set(new CurrentUser("1", "13800000001", "STAFF", List.of("COMMUNITY_ADMIN"),
                "COMMUNITY_ADMIN", "77"));
        ElderProfile elder = new ElderProfile();
        elder.setElderId(10L);
        elder.setCommunityId(88L);
        when(elderProfileMapper.selectById(10L)).thenReturn(elder);

        assertThrows(BusinessException.class,
                () -> service.issueActivationTicket(10L, new ActivationCodeIssueDTO()));
        verify(ticketMapper, never()).selectCount(any(Wrapper.class));
        verify(ticketMapper, never()).insert(any(ElderActivationTicket.class));
    }

    @Test
    @DisplayName("首次激活缺少已验证手机挑战或初始密码时必须在查询/消费激活票据前拒绝")
    void activationWithoutPhoneChallengeAndPasswordDoesNotTouchDatabase() {
        ActivationConsumeDTO request = new ActivationConsumeDTO();
        request.setTicketId("ticket-test");
        request.setActivationCode("654321");
        request.setPhone("13000000077");

        assertThrows(BusinessException.class, () -> service.consumeActivationCode(request));

        verifyNoInteractions(ticketMapper, elderProfileMapper, identityLinkMapper,
                accountMapper, roleMapper, sessionMapper, phoneVerificationChallengeService);
    }

    @Test
    @DisplayName("通过一次性手机挑战后以请求初始密码创建 BCrypt 账号并写入数据库会话")
    void verifiedActivationStoresBcryptPasswordAndActiveAssignment() throws Exception {
        String ticketId = "ticket-00000000-0000-0000-0000-000000000001";
        String phone = "13000000077";
        String initialPassword = "synthetic-first-password";
        ElderActivationTicket ticket = new ElderActivationTicket();
        ticket.setTicketId(ticketId);
        ticket.setElderId(10L);
        ticket.setIssuedBy(1L);
        ticket.setStatus("ISSUED");
        ticket.setCodeMac(MessageDigest.getInstance("SHA-256")
                .digest("654321".getBytes(StandardCharsets.UTF_8)));
        ticket.setExpiresAt(LocalDateTime.now().plusHours(1));
        when(ticketMapper.selectById(ticketId)).thenReturn(ticket);
        when(ticketMapper.updateById(any(ElderActivationTicket.class))).thenReturn(1);

        ElderProfile elder = new ElderProfile();
        elder.setElderId(10L);
        elder.setCommunityId(77L);
        when(elderProfileMapper.selectById(10L)).thenReturn(elder);

        when(accountMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        doAnswer(invocation -> {
            UserAccount inserted = invocation.getArgument(0);
            inserted.setUserId(99L);
            return 1;
        }).when(accountMapper).insert(any(UserAccount.class));
        when(roleMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        doAnswer(invocation -> {
            UserRole inserted = invocation.getArgument(0);
            inserted.setAssignmentId(900L);
            return 1;
        }).when(roleMapper).insert(any(UserRole.class));
        when(roleMapper.selectList(any(Wrapper.class))).thenAnswer(invocation -> {
            UserRole current = new UserRole();
            current.setAssignmentId(900L);
            current.setUserId(99L);
            current.setRoleCode("ELDER");
            current.setCommunityId(77L);
            current.setStatus("ACTIVE");
            return List.of(current);
        });
        when(identityLinkMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(identityLinkMapper.insert(any(ElderIdentityLink.class))).thenReturn(1);
        when(sessionMapper.insert(any(AuthSession.class))).thenReturn(1);

        ActivationConsumeDTO request = new ActivationConsumeDTO();
        request.setTicketId(ticketId);
        request.setActivationCode("654321");
        request.setPhone(phone);
        request.setPhoneChallengeId("challenge-00000000-0000-0000-000000000001");
        request.setVerificationCode("112233");
        request.setPassword(initialPassword);

        AuthTokenResponse response = service.consumeActivationCode(request);

        ArgumentCaptor<UserAccount> account = ArgumentCaptor.forClass(UserAccount.class);
        verify(accountMapper).insert(account.capture());
        assertTrue(account.getValue().getPasswordHash().startsWith("$2"));
        assertTrue(new BCryptPasswordEncoder().matches(initialPassword, account.getValue().getPasswordHash()));
        ArgumentCaptor<AuthSession> session = ArgumentCaptor.forClass(AuthSession.class);
        verify(sessionMapper).insert(session.capture());
        assertEquals(900L, session.getValue().getActiveAssignmentId());
        assertNotNull(response.getToken());
        assertEquals("ELDER", response.getActiveRole());
        verify(phoneVerificationChallengeService).verifyAndConsume(
                request.getPhoneChallengeId(), phone, "ACTIVATION", ticketId, request.getVerificationCode());
    }
}
