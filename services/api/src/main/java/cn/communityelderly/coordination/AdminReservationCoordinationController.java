package cn.communityelderly.coordination;

import cn.communityelderly.common.api.Result;
import cn.communityelderly.common.security.CurrentUser;
import cn.communityelderly.common.security.RequireRoles;
import cn.communityelderly.common.security.UserContext;
import cn.communityelderly.modules.reservation.dto.ReservationCancelDTO;
import cn.communityelderly.modules.reservation.dto.ReservationVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/reservations")
@RequireRoles({"COMMUNITY_ADMIN"})
public class AdminReservationCoordinationController {

    private final ReservationWorkOrderCoordinator coordinator;

    public AdminReservationCoordinationController(ReservationWorkOrderCoordinator coordinator) {
        this.coordinator = coordinator;
    }

    @GetMapping("/pending")
    public Result<List<ReservationVO>> getPendingReservations() {
        return Result.success(coordinator.getPendingReservations());
    }

    @PostMapping("/{reservationId}/confirm")
    public Result<Long> confirmReservation(@PathVariable Long reservationId) {
        CurrentUser user = UserContext.requireCurrentUser();
        Long workOrderId = coordinator.confirmReservationAndCreateWorkOrder(reservationId, Long.parseLong(user.getUserId()));
        return Result.success(workOrderId);
    }

    @PostMapping("/{reservationId}/reject")
    public Result<Void> rejectReservation(@PathVariable Long reservationId,
                                          @Valid @RequestBody ReservationCancelDTO dto) {
        CurrentUser user = UserContext.requireCurrentUser();
        coordinator.rejectReservation(reservationId, dto.getReason(), Long.parseLong(user.getUserId()));
        return Result.success();
    }
}
