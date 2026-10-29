package cn.communityelderly.modules.emergency.mapper;

import cn.communityelderly.modules.emergency.entity.WorkOrder;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface WorkOrderMapper extends BaseMapper<WorkOrder> {

    @Select("SELECT * FROM work_order WHERE work_order_id = #{workOrderId} FOR UPDATE")
    WorkOrder selectByIdForUpdate(@Param("workOrderId") Long workOrderId);
}
