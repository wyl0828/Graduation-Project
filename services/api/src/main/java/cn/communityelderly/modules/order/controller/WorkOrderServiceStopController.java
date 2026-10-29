package cn.communityelderly.modules.order.controller;

import cn.communityelderly.common.api.Result;
import cn.communityelderly.common.idempotency.Idempotency;
import cn.communityelderly.common.security.CurrentUser;
import cn.communityelderly.common.security.RequireRoles;
import cn.communityelderly.common.security.UserContext;
import cn.communityelderly.modules.order.dto.ServiceStopRequestDTO;
import cn.communityelderly.modules.order.dto.ServiceStopRequestVO;
import cn.communityelderly.modules.order.dto.ServiceStopReviewDTO;
import cn.communityelderly.modules.order.service.WorkOrderService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/work-orders/{workOrderId}/service-stop-requests")
public class WorkOrderServiceStopController {

    private final WorkOrderService workOrderService;

    public WorkOrderServiceStopController(WorkOrderService workOrderService) {
        this.workOrderService = workOrderService;
    }

    @PostMapping
    @Idempotency(required = true)
    @RequireRoles({"GRID_WORKER", "CARE_WORKER", "CHILD", "ELDER"})
    public Result<Long> requestStop(@PathVariable Long workOrderId,
                                    @Valid @RequestBody ServiceStopRequestDTO dto) {
        CurrentUser user = UserContext.requireCurrentUser();
        return Result.success(workOrderService.requestServiceStop(workOrderId, dto, Long.parseLong(user.getUserId())));
    }

    @GetMapping
    @RequireRoles("COMMUNITY_ADMIN")
    public Result<List<ServiceStopRequestVO>> listRequests(@PathVariable Long workOrderId) {
        return Result.success(workOrderService.listServiceStopRequests(workOrderId));
    }

    @PostMapping("/{stopId}/review")
    @Idempotency(required = true)
    @RequireRoles("COMMUNITY_ADMIN")
    public Result<Void> reviewStop(@PathVariable Long workOrderId,
                                   @PathVariable Long stopId,
                                   @Valid @RequestBody ServiceStopReviewDTO dto) {
        CurrentUser user = UserContext.requireCurrentUser();
        workOrderService.reviewServiceStop(workOrderId, stopId, dto, Long.parseLong(user.getUserId()));
        return Result.success();
    }
}
