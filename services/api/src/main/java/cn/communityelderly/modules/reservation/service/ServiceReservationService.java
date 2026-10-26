package cn.communityelderly.modules.reservation.service;

import cn.communityelderly.modules.reservation.dto.ReservationCreateDTO;
import cn.communityelderly.modules.reservation.dto.ReservationVO;

import java.util.List;

public interface ServiceReservationService {
    Long createReservation(ReservationCreateDTO dto, Long applicantUserId);
    List<ReservationVO> listMyReservations(Long applicantUserId);
    List<ReservationVO> listElderReservations(Long elderId);
    ReservationVO getReservationDetail(Long reservationId);
    void cancelReservation(Long reservationId, String reason, Long userId);
}
