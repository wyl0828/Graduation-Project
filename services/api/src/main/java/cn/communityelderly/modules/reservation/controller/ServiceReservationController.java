package cn.communityelderly.modules.reservation.controller;

import cn.communityelderly.common.api.Result;
import cn.communityelderly.common.idempotency.Idempotency;
import cn.communityelderly.common.security.CurrentUser;
import cn.communityelderly.common.security.RequireRoles;
import cn.communityelderly.common.security.UserContext;
import cn.communityelderly.modules.reservation.dto.ReservationCancelDTO;
import cn.communityelderly.modules.reservation.dto.ReservationCreateDTO;
import cn.communityelderly.modules.reservation.dto.ReservationVO;
import cn.communityelderly.modules.reservation.service.ServiceReservationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/service-reservations")
public class ServiceReservationController {

    private final ServiceReservationService reservationService;

    public ServiceReservationController(ServiceReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @PostMapping
    @Idempotency(required = true)
    @RequireRoles({"CHILD"})
    public Result<Long> createReservation(@Valid @RequestBody ReservationCreateDTO dto) {
        CurrentUser user = UserContext.requireCurrentUser();
        Long reservationId = reservationService.createReservation(dto, Long.parseLong(user.getUserId()));
        return Result.success(reservationId);
    }

    @GetMapping("/my")
    @RequireRoles({"CHILD", "ELDER"})
    public Result<List<ReservationVO>> listMyReservations() {
        CurrentUser user = UserContext.requireCurrentUser();
        return Result.success(reservationService.listMyReservations(Long.parseLong(user.getUserId())));
    }

    @GetMapping("/elder/{elderId}")
    @RequireRoles({"CHILD", "ELDER", "COMMUNITY_ADMIN", "GRID_WORKER"})
    public Result<List<ReservationVO>> listElderReservations(@PathVariable Long elderId) {
        return Result.success(reservationService.listElderReservations(elderId));
    }

    @GetMapping("/{reservationId}")
    @RequireRoles({"CHILD", "ELDER", "COMMUNITY_ADMIN", "GRID_WORKER"})
    public Result<ReservationVO> getReservationDetail(@PathVariable Long reservationId) {
        return Result.success(reservationService.getReservationDetail(reservationId));
    }

    @PostMapping("/{reservationId}/cancel")
    @Idempotency(required = true)
    @RequireRoles({"CHILD"})
    public Result<Void> cancelReservation(@PathVariable Long reservationId,
                                          @Valid @RequestBody ReservationCancelDTO dto) {
        CurrentUser user = UserContext.requireCurrentUser();
        reservationService.cancelReservation(reservationId, dto.getReason(), Long.parseLong(user.getUserId()));
        return Result.success();
    }
}
