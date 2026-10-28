package cn.communityelderly.coordination;

import cn.communityelderly.common.api.Result;
import cn.communityelderly.common.idempotency.Idempotency;
import cn.communityelderly.common.security.CurrentUser;
import cn.communityelderly.common.security.RequireRoles;
import cn.communityelderly.common.security.UserContext;
import cn.communityelderly.modules.reservation.dto.ReservationCancelReviewDTO;
import cn.communityelderly.modules.reservation.dto.ReservationCancelRequestVO;
import cn.communityelderly.modules.reservation.dto.ReservationCancelDTO;
import cn.communityelderly.modules.reservation.dto.ReservationVO;
import cn.communityelderly.modules.reservation.service.ServiceReservationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/reservations")
@RequireRoles({"COMMUNITY_ADMIN"})
public class AdminReservationCoordinationController {

    private final ReservationWorkOrderCoordinator coordinator;
    private final ServiceReservationService reservationService;

    public AdminReservationCoordinationController(ReservationWorkOrderCoordinator coordinator,
                                                  ServiceReservationService reservationService) {
        this.coordinator = coordinator;
        this.reservationService = reservationService;
    }

    @GetMapping("/pending")
    public Result<List<ReservationVO>> getPendingReservations() {
        return Result.success(coordinator.getPendingReservations());
    }

    @PostMapping("/{reservationId}/confirm")
    @Idempotency(required = true)
    public Result<Long> confirmReservation(@PathVariable Long reservationId) {
        CurrentUser user = UserContext.requireCurrentUser();
        Long workOrderId = coordinator.confirmReservationAndCreateWorkOrder(reservationId, Long.parseLong(user.getUserId()));
        return Result.success(workOrderId);
    }

    @PostMapping("/{reservationId}/reject")
    @Idempotency(required = true)
    public Result<Void> rejectReservation(@PathVariable Long reservationId,
                                          @Valid @RequestBody ReservationCancelDTO dto) {
        CurrentUser user = UserContext.requireCurrentUser();
        coordinator.rejectReservation(reservationId, dto.getReason(), Long.parseLong(user.getUserId()));
        return Result.success();
    }

    @GetMapping("/cancel-requests/pending")
    public Result<List<ReservationCancelRequestVO>> listPendingCancellationRequests() {
        return Result.success(reservationService.listPendingCancellationRequests());
    }

    @PostMapping("/cancel-requests/{cancelId}/review")
    @Idempotency(required = true)
    public Result<Void> reviewCancellationRequest(@PathVariable Long cancelId,
                                                  @Valid @RequestBody ReservationCancelReviewDTO dto) {
        CurrentUser user = UserContext.requireCurrentUser();
        reservationService.reviewCancellationRequest(cancelId, dto, Long.parseLong(user.getUserId()));
        return Result.success();
    }
}
