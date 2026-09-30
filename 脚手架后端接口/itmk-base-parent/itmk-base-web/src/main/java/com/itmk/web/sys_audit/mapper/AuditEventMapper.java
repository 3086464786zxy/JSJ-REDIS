package com.itmk.web.sys_audit.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.itmk.web.sys_audit.entity.AuditEvent;
public interface AuditEventMapper extends BaseMapper<AuditEvent> {
    long pendingCount(@org.apache.ibatis.annotations.Param("cutoff") java.time.LocalDateTime cutoff);
}
