package cn.communityelderly.modules.audit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import cn.communityelderly.modules.audit.entity.AuditLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AuditLogMapper extends BaseMapper<AuditLog> {
}
