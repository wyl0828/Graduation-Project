package cn.communityelderly.common.security;

import cn.communityelderly.common.api.ErrorCode;
import cn.communityelderly.common.exception.BusinessException;
import cn.communityelderly.common.exception.ForbiddenException;
import cn.communityelderly.modules.elder.entity.ElderFamilyLink;
import cn.communityelderly.modules.elder.entity.ElderIdentityLink;
import cn.communityelderly.modules.elder.entity.ElderProfile;
import cn.communityelderly.modules.elder.mapper.ElderFamilyLinkMapper;
import cn.communityelderly.modules.elder.mapper.ElderIdentityLinkMapper;
import cn.communityelderly.modules.elder.mapper.ElderProfileMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * Resolves object access from the active request identity and persisted elder relationships.
 * Callers must load object ownership from the database and invoke this policy in the same
 * transaction as any write.
 */
@Component
public class ObjectAuthorizationPolicy {

    private final ElderProfileMapper elderProfileMapper;
    private final ElderIdentityLinkMapper identityLinkMapper;
    private final ElderFamilyLinkMapper familyLinkMapper;

    public ObjectAuthorizationPolicy(ElderProfileMapper elderProfileMapper,
                                     ElderIdentityLinkMapper identityLinkMapper,
                                     ElderFamilyLinkMapper familyLinkMapper) {
        this.elderProfileMapper = elderProfileMapper;
        this.identityLinkMapper = identityLinkMapper;
        this.familyLinkMapper = familyLinkMapper;
    }

    public CurrentUser requireCurrentUser() {
        return UserContext.requireCurrentUser();
    }

    /** Checks only the one role selected for the current database-backed session. */
    public void requireRole(String... allowedCurrentRoles) {
        CurrentUser user = requireCurrentUser();
        String currentRole = user.getCurrentRole();
        if (currentRole == null || allowedCurrentRoles == null
                || Arrays.stream(allowedCurrentRoles).noneMatch(role -> role != null && role.equalsIgnoreCase(currentRole))) {
            throw new ForbiddenException(ErrorCode.ACCESS_DENIED, "当前角色无权访问该资源");
        }
    }

    /** Returns the sole elder identity attached to the current ELDER account. */
    public Long requireElderIdentityId() {
        CurrentUser user = requireCurrentUser();
        requireRole("ELDER");
        long userId = parseUserId(user);
        ElderIdentityLink identity = identityLinkMapper.selectOne(
                new LambdaQueryWrapper<ElderIdentityLink>()
                        .eq(ElderIdentityLink::getUserId, userId)
        );
        if (identity == null || identity.getElderId() == null) {
            throw inaccessible();
        }
        return identity.getElderId();
    }

    /** Returns an ACTIVE family relationship for the current CHILD account. */
    public ElderFamilyLink requireActiveChildLink(Long elderId) {
        CurrentUser user = requireCurrentUser();
        requireRole("CHILD");
        if (elderId == null || elderId <= 0) {
            throw inaccessible();
        }
        ElderFamilyLink link = familyLinkMapper.selectOne(
                new LambdaQueryWrapper<ElderFamilyLink>()
                        .eq(ElderFamilyLink::getElderId, elderId)
                        .eq(ElderFamilyLink::getChildUserId, parseUserId(user))
        );
        if (link == null) {
            throw inaccessible();
        }
        if ("FROZEN".equalsIgnoreCase(link.getStatus())) {
            // Documented contract (E-02/E-04): a disputed relationship must answer 403
            // KINSHIP_NOT_ACTIVE rather than pretending the elder does not exist.
            throw new BusinessException(ErrorCode.KINSHIP_NOT_ACTIVE,
                    ErrorCode.KINSHIP_NOT_ACTIVE.getDefaultMessage());
        }
        if (!"ACTIVE".equalsIgnoreCase(link.getStatus())) {
            throw inaccessible();
        }
        return link;
    }

    /**
     * Authorizes an elder-owned object for the current role. The object ID must first be
     * resolved to its elder ID by querying the owning database row.
     */
    public ElderProfile requireElderAccess(Long elderId) {
        CurrentUser user = requireCurrentUser();
        String role = user.getCurrentRole();
        if (!isRole(role, "ELDER", "CHILD", "COMMUNITY_ADMIN", "GRID_WORKER")) {
            throw new ForbiddenException(ErrorCode.ACCESS_DENIED, "当前角色无权访问该资源");
        }
        ElderProfile elder = loadElder(elderId);
        if ("ELDER".equalsIgnoreCase(role)) {
            if (!elder.getElderId().equals(requireElderIdentityId())) {
                throw inaccessible();
            }
            return elder;
        }
        if ("CHILD".equalsIgnoreCase(role)) {
            requireActiveChildLink(elder.getElderId());
            return elder;
        }
        if ("COMMUNITY_ADMIN".equalsIgnoreCase(role) || "GRID_WORKER".equalsIgnoreCase(role)) {
            requireCurrentCommunityOwns(elder, user);
            return elder;
        }
        throw new ForbiddenException(ErrorCode.ACCESS_DENIED, "当前角色无权访问该资源");
    }

    /** Authorizes a domain operation that is limited to specified roles in the current community. */
    public ElderProfile requireElderInCurrentCommunity(Long elderId, String... allowedCurrentRoles) {
        CurrentUser user = requireCurrentUser();
        requireRole(allowedCurrentRoles);
        ElderProfile elder = loadElder(elderId);
        requireCurrentCommunityOwns(elder, user);
        return elder;
    }

    /** Returns database-resolved elder IDs that the current role may access in list queries. */
    public List<Long> listElderIdsForCurrentUser() {
        CurrentUser user = requireCurrentUser();
        String role = user.getCurrentRole();
        if ("ELDER".equalsIgnoreCase(role)) {
            return List.of(requireElderIdentityId());
        }
        if ("CHILD".equalsIgnoreCase(role)) {
            long childUserId = parseUserId(user);
            return familyLinkMapper.selectList(
                            new LambdaQueryWrapper<ElderFamilyLink>()
                                    .eq(ElderFamilyLink::getChildUserId, childUserId)
                                    .eq(ElderFamilyLink::getStatus, "ACTIVE")
                                    .select(ElderFamilyLink::getElderId))
                    .stream()
                    .map(ElderFamilyLink::getElderId)
                    .filter(id -> id != null)
                    .distinct()
                    .toList();
        }
        if ("COMMUNITY_ADMIN".equalsIgnoreCase(role) || "GRID_WORKER".equalsIgnoreCase(role)) {
            long communityId = currentCommunityId(user);
            return elderProfileMapper.selectList(
                            new LambdaQueryWrapper<ElderProfile>()
                                    .eq(ElderProfile::getCommunityId, communityId)
                                    .select(ElderProfile::getElderId))
                    .stream()
                    .map(ElderProfile::getElderId)
                    .filter(id -> id != null)
                    .distinct()
                    .toList();
        }
        throw new ForbiddenException(ErrorCode.ACCESS_DENIED, "当前角色无权访问该资源");
    }

    private ElderProfile loadElder(Long elderId) {
        if (elderId == null || elderId <= 0) {
            throw inaccessible();
        }
        ElderProfile elder = elderProfileMapper.selectById(elderId);
        if (elder == null) {
            throw inaccessible();
        }
        return elder;
    }

    private void requireCurrentCommunityOwns(ElderProfile elder, CurrentUser user) {
        if (elder.getCommunityId() == null || !elder.getCommunityId().equals(currentCommunityId(user))) {
            throw inaccessible();
        }
    }

    private long currentCommunityId(CurrentUser user) {
        String communityId = user.getCommunityId();
        if (communityId == null || communityId.isBlank()) {
            throw new ForbiddenException(ErrorCode.ACCESS_DENIED, "当前角色未分配有效社区范围");
        }
        try {
            return Long.parseLong(communityId);
        } catch (NumberFormatException exception) {
            throw new ForbiddenException(ErrorCode.ACCESS_DENIED, "当前角色未分配有效社区范围");
        }
    }

    private boolean isRole(String currentRole, String... allowedRoles) {
        return currentRole != null
                && Arrays.stream(allowedRoles).anyMatch(role -> role.equalsIgnoreCase(currentRole));
    }

    private long parseUserId(CurrentUser user) {
        try {
            return Long.parseLong(user.getUserId());
        } catch (RuntimeException exception) {
            throw new ForbiddenException(ErrorCode.ACCESS_DENIED, "当前登录身份无效");
        }
    }

    private BusinessException inaccessible() {
        return new BusinessException(ErrorCode.RESOURCE_NOT_FOUND_OR_NOT_ACCESSIBLE);
    }
}
