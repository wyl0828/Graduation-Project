package cn.communityelderly.modules.reservation.mapper;

import cn.communityelderly.modules.reservation.entity.ServiceReservation;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface ServiceReservationMapper extends BaseMapper<ServiceReservation> {

    @Select("SELECT * FROM service_reservation WHERE reservation_id = #{reservationId} FOR UPDATE")
    ServiceReservation selectByIdForUpdate(@Param("reservationId") Long reservationId);

    @Select("SELECT reservation_id FROM service_reservation "
            + "WHERE elder_id = #{elderId} AND service_type = #{serviceType} "
            + "AND service_date = #{serviceDate} AND slot_code = #{slotCode} "
            + "AND status NOT IN ('REJECTED', 'CANCELLED', 'SERVICE_STOPPED') "
            + "ORDER BY reservation_id FOR UPDATE")
    List<Long> selectActiveSlotReservationIdsForUpdate(@Param("elderId") Long elderId,
                                                       @Param("serviceType") String serviceType,
                                                       @Param("serviceDate") LocalDate serviceDate,
                                                       @Param("slotCode") String slotCode);
}
