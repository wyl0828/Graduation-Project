package cn.communityelderly.modules.order.controller;

import cn.communityelderly.common.api.Result;
import cn.communityelderly.common.security.CurrentUser;
import cn.communityelderly.common.security.RequireRoles;
import cn.communityelderly.common.security.UserContext;
import cn.communityelderly.modules.order.dto.WorkOrderExecutionDTO;
import cn.communityelderly.modules.order.dto.WorkOrderReviewDTO;
import cn.communityelderly.modules.order.service.WorkOrderService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/work-orders")
public class WorkOrderExecutionController {

    private final WorkOrderService workOrderService;

    public WorkOrderExecutionController(WorkOrderService workOrderService) {
        this.workOrderService = workOrderService;
    }

    @PostMapping(value = {"/{workOrderId}/execution", "/{workOrderId}/execution/submit"})
    @RequireRoles({"GRID_WORKER", "CARE_WORKER"})
    public Result<Void> submitExecution(@PathVariable Long workOrderId,
                                        @Valid @RequestBody WorkOrderExecutionDTO dto) {
        CurrentUser user = UserContext.requireCurrentUser();
        workOrderService.submitExecutionResult(workOrderId, dto, Long.parseLong(user.getUserId()));
        return Result.success();
    }

    @PostMapping("/{workOrderId}/review")
    @RequireRoles({"COMMUNITY_ADMIN"})
    public Result<Void> reviewWorkOrder(@PathVariable Long workOrderId,
                                        @Valid @RequestBody WorkOrderReviewDTO dto) {
        CurrentUser user = UserContext.requireCurrentUser();
        workOrderService.reviewWorkOrder(workOrderId, dto, Long.parseLong(user.getUserId()));
        return Result.success();
    }
}
