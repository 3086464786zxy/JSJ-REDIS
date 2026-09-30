package com.itmk.web.sys_audit.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;
@Data @TableName("sys_audit_event")
public class AuditEvent {
    @TableId(type=IdType.AUTO) private Long eventId;
    private String requestId;
    private String phase;
    private Long userId;
    private String method;
    private String path;
    private String remoteAddress;
    private Integer httpStatus;
    private Integer resultCode;
    private Long durationMs;
    private Long targetId;
    private String details;
    private LocalDateTime occurredAt;
}
