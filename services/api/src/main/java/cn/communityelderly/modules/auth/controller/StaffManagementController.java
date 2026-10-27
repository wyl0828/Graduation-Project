package cn.communityelderly.modules.auth.controller;

import cn.communityelderly.common.api.Result;
import cn.communityelderly.common.idempotency.Idempotency;
import cn.communityelderly.common.security.RequireRoles;
import cn.communityelderly.modules.auth.dto.StaffCreateRequest;
import cn.communityelderly.modules.auth.dto.StaffRolesUpdateRequest;
import cn.communityelderly.modules.auth.dto.StaffSummaryVO;
import cn.communityelderly.modules.auth.service.StaffManagementService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Validated
@RequestMapping("/api/v1/admin/staff")
@RequireRoles("COMMUNITY_ADMIN")
public class StaffManagementController {

    private final StaffManagementService staffManagementService;

    public StaffManagementController(StaffManagementService staffManagementService) {
        this.staffManagementService = staffManagementService;
    }

    @GetMapping
    public Result<List<StaffSummaryVO>> listStaff() {
        return Result.success(staffManagementService.listStaff());
    }

    @PostMapping
    @Idempotency(required = true)
    @ResponseStatus(HttpStatus.CREATED)
    public Result<StaffSummaryVO> createStaff(@Valid @RequestBody StaffCreateRequest request) {
        return Result.success(staffManagementService.createStaff(request));
    }

    @PutMapping("/{userId}/roles")
    @Idempotency(required = true)
    public Result<StaffSummaryVO> replaceRoles(@PathVariable @Positive Long userId,
                                               @Valid @RequestBody StaffRolesUpdateRequest request) {
        return Result.success(staffManagementService.replaceRoles(userId, request));
    }
}
