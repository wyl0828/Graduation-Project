package cn.communityelderly.modules.elder.mapper;

import cn.communityelderly.modules.elder.entity.ElderProfile;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ElderProfileMapper extends BaseMapper<ElderProfile> {

    @Select("SELECT * FROM elder_profile WHERE elder_id = #{elderId} FOR UPDATE")
    ElderProfile selectByIdForUpdate(@Param("elderId") Long elderId);
}
