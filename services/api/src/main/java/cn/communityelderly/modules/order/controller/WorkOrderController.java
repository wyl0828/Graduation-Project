package cn.communityelderly.modules.order.controller;

import cn.communityelderly.common.api.Result;
import cn.communityelderly.common.idempotency.Idempotency;
import cn.communityelderly.common.security.CurrentUser;
import cn.communityelderly.common.security.RequireRoles;
import cn.communityelderly.common.security.UserContext;
import cn.communityelderly.modules.order.dto.WorkOrderActionDTO;
import cn.communityelderly.modules.order.dto.WorkOrderDispatchDTO;
import cn.communityelderly.modules.order.dto.WorkOrderVO;
import cn.communityelderly.modules.order.service.WorkOrderService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/work-orders")
public class WorkOrderController {

    private final WorkOrderService workOrderService;

    public WorkOrderController(WorkOrderService workOrderService) {
        this.workOrderService = workOrderService;
    }

    @GetMapping("/dispatch-pool")
    @RequireRoles({"COMMUNITY_ADMIN"})
    public Result<List<WorkOrderVO>> getDispatchPool(
            @RequestParam(required = false) Long communityId,
            @RequestParam(required = false) String status) {
        return Result.success(workOrderService.getDispatchPool(communityId, status));
    }

    @GetMapping("/my-tasks")
    @RequireRoles({"GRID_WORKER", "CARE_WORKER"})
    public Result<List<WorkOrderVO>> getMyTasks(@RequestParam(required = false) String status) {
        CurrentUser user = UserContext.requireCurrentUser();
        return Result.success(workOrderService.getMyTasks(Long.parseLong(user.getUserId()), status));
    }

    @GetMapping("/{workOrderId}")
    @RequireRoles({"COMMUNITY_ADMIN", "GRID_WORKER", "CARE_WORKER", "CHILD", "ELDER"})
    public Result<WorkOrderVO> getOrderDetail(@PathVariable Long workOrderId) {
        return Result.success(workOrderService.getOrderDetail(workOrderId));
    }

    @PostMapping("/{workOrderId}/dispatch")
    @Idempotency(required = true)
    @RequireRoles({"COMMUNITY_ADMIN"})
    public Result<Void> dispatchOrder(@PathVariable Long workOrderId,
                                      @Valid @RequestBody WorkOrderDispatchDTO dto) {
        CurrentUser user = UserContext.requireCurrentUser();
        workOrderService.dispatchOrder(workOrderId, dto, Long.parseLong(user.getUserId()));
        return Result.success();
    }

    @PostMapping("/{workOrderId}/accept")
    @Idempotency(required = true)
    @RequireRoles({"GRID_WORKER", "CARE_WORKER"})
    public Result<Void> acceptOrder(@PathVariable Long workOrderId) {
        CurrentUser user = UserContext.requireCurrentUser();
        workOrderService.acceptOrder(workOrderId, Long.parseLong(user.getUserId()));
        return Result.success();
    }

    @PostMapping("/{workOrderId}/reject")
    @Idempotency(required = true)
    @RequireRoles({"GRID_WORKER"})
    public Result<Void> rejectOrder(@PathVariable Long workOrderId,
                                    @Valid @RequestBody WorkOrderActionDTO dto) {
        CurrentUser user = UserContext.requireCurrentUser();
        workOrderService.rejectOrder(workOrderId, dto.getComment(), Long.parseLong(user.getUserId()));
        return Result.success();
    }

    @PostMapping("/{workOrderId}/start")
    @Idempotency(required = true)
    @RequireRoles({"GRID_WORKER", "CARE_WORKER"})
    public Result<Void> startService(@PathVariable Long workOrderId) {
        CurrentUser user = UserContext.requireCurrentUser();
        workOrderService.startService(workOrderId, Long.parseLong(user.getUserId()));
        return Result.success();
    }
}
