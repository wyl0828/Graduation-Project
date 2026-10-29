package cn.communityelderly.modules.reservation.mapper;

import cn.communityelderly.modules.reservation.entity.ReservationCancelRequest;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface ReservationCancelRequestMapper extends BaseMapper<ReservationCancelRequest> {

    @Select("SELECT * FROM reservation_cancel_request WHERE cancel_id = #{cancelId} FOR UPDATE")
    ReservationCancelRequest selectByIdForUpdate(@Param("cancelId") Long cancelId);

    @Update("""
            UPDATE reservation_cancel_request
            SET status = #{status}, reviewer_user_id = #{reviewerUserId},
                reviewed_at = #{reviewedAt}, review_reason = #{reviewReason}
            WHERE cancel_id = #{cancelId} AND status = 'PENDING_REVIEW'
            """)
    int reviewPendingRequest(@Param("cancelId") Long cancelId,
                             @Param("status") String status,
                             @Param("reviewerUserId") Long reviewerUserId,
                             @Param("reviewedAt") LocalDateTime reviewedAt,
                             @Param("reviewReason") String reviewReason);
}
