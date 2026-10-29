package cn.communityelderly.modules.order.controller;

import cn.communityelderly.common.api.Result;
import cn.communityelderly.common.idempotency.Idempotency;
import cn.communityelderly.common.security.CurrentUser;
import cn.communityelderly.common.security.RequireRoles;
import cn.communityelderly.common.security.UserContext;
import cn.communityelderly.modules.order.dto.WorkOrderHandoffAcceptDTO;
import cn.communityelderly.modules.order.dto.WorkOrderHandoffRequestDTO;
import cn.communityelderly.modules.order.dto.WorkOrderHandoffRejectDTO;
import cn.communityelderly.modules.order.service.WorkOrderService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/work-orders/{workOrderId}/handoffs")
public class WorkOrderHandoffController {

    private final WorkOrderService workOrderService;

    public WorkOrderHandoffController(WorkOrderService workOrderService) {
        this.workOrderService = workOrderService;
    }

    @PostMapping
    @Idempotency(required = true)
    @RequireRoles("COMMUNITY_ADMIN")
    public Result<Long> requestHandoff(@PathVariable Long workOrderId,
                                       @Valid @RequestBody WorkOrderHandoffRequestDTO dto) {
        CurrentUser user = UserContext.requireCurrentUser();
        return Result.success(workOrderService.requestHandoff(
                workOrderId, dto, Long.parseLong(user.getUserId())));
    }

    @PostMapping("/{handoffId}/accept")
    @Idempotency(required = true)
    @RequireRoles({"GRID_WORKER", "CARE_WORKER"})
    public Result<Void> acceptHandoff(@PathVariable Long workOrderId,
                                      @PathVariable Long handoffId,
                                      @Valid @RequestBody(required = false) WorkOrderHandoffAcceptDTO dto) {
        CurrentUser user = UserContext.requireCurrentUser();
        workOrderService.acceptHandoff(workOrderId, handoffId, dto,
                Long.parseLong(user.getUserId()));
        return Result.success();
    }

    @PostMapping("/{handoffId}/reject")
    @Idempotency(required = true)
    @RequireRoles({"GRID_WORKER", "CARE_WORKER"})
    public Result<Void> rejectHandoff(@PathVariable Long workOrderId,
                                      @PathVariable Long handoffId,
                                      @Valid @RequestBody WorkOrderHandoffRejectDTO dto) {
        CurrentUser user = UserContext.requireCurrentUser();
        workOrderService.rejectHandoff(workOrderId, handoffId, dto,
                Long.parseLong(user.getUserId()));
        return Result.success();
    }
}
