package cn.communityelderly.modules.elder.controller;

import cn.communityelderly.common.api.Result;
import cn.communityelderly.common.api.ErrorCode;
import cn.communityelderly.common.exception.ForbiddenException;
import cn.communityelderly.common.security.ObjectAuthorizationPolicy;
import cn.communityelderly.common.security.RequireRoles;
import cn.communityelderly.modules.elder.dto.*;
import cn.communityelderly.modules.elder.entity.FamilyLinkApplication;
import cn.communityelderly.modules.elder.service.KinshipService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/family/links")
public class KinshipController {

    private final KinshipService kinshipService;
    private final ObjectAuthorizationPolicy authorizationPolicy;

    public KinshipController(KinshipService kinshipService, ObjectAuthorizationPolicy authorizationPolicy) {
        this.kinshipService = kinshipService;
        this.authorizationPolicy = authorizationPolicy;
    }

    @PostMapping("/apply")
    @RequireRoles({"CHILD"})
    public Result<Long> applyKinship(@Valid @RequestBody KinshipApplyDTO dto) {
        Long appId = kinshipService.applyKinship(dto);
        return Result.success(appId);
    }

    @GetMapping("/my-elders")
    @RequireRoles({"CHILD"})
    public Result<List<BoundElderVO>> getMyBoundElders() {
        authorizationPolicy.requireRole("CHILD");
        String userId = authorizationPolicy.requireCurrentUser().getUserId();
        Long childUserId;
        try {
            childUserId = Long.parseLong(userId);
        } catch (RuntimeException exception) {
            throw new ForbiddenException(ErrorCode.ACCESS_DENIED, "当前登录身份无效");
        }
        return Result.success(kinshipService.listBoundElders(childUserId));
    }

    @GetMapping("/applications")
    @RequireRoles({"COMMUNITY_ADMIN", "GRID_WORKER"})
    public Result<List<KinshipApplicationVO>> listApplications(@RequestParam(required = false) String status) {
        return Result.success(kinshipService.listApplications(status));
    }

    @PostMapping("/applications/{appId}/review")
    @RequireRoles({"COMMUNITY_ADMIN"})
    public Result<Void> reviewApplication(@PathVariable Long appId, @Valid @RequestBody KinshipReviewDTO dto) {
        kinshipService.reviewApplication(appId, dto);
        return Result.success(null);
    }

    @PostMapping("/{linkId}/freeze")
    @RequireRoles({"COMMUNITY_ADMIN"})
    public Result<Void> freezeKinship(@PathVariable Long linkId, @Valid @RequestBody KinshipFreezeDTO dto) {
        kinshipService.freezeKinship(linkId, dto);
        return Result.success(null);
    }

    @PostMapping("/{linkId}/unfreeze")
    @RequireRoles({"COMMUNITY_ADMIN"})
    public Result<Void> unfreezeKinship(@PathVariable Long linkId, @RequestBody(required = false) Map<String, String> body) {
        String reason = body != null ? body.getOrDefault("reason", "社区调解查实，恢复亲情关系") : "社区调解查实，恢复亲情关系";
        kinshipService.unfreezeKinship(linkId, reason);
        return Result.success(null);
    }

    @PostMapping("/{linkId}/unlink")
    @RequireRoles({"COMMUNITY_ADMIN", "CHILD"})
    public Result<Void> unlinkKinship(@PathVariable Long linkId, @RequestBody(required = false) Map<String, String> body) {
        String reason = body != null ? body.getOrDefault("reason", "家属主动解绑") : "家属主动解绑";
        kinshipService.unlinkKinship(linkId, reason);
        return Result.success(null);
    }
}
