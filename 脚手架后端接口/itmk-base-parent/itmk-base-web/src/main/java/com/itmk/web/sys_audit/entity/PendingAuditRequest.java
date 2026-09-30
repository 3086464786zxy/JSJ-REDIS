package com.itmk.web.sys_audit.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;
@Data @TableName("sys_audit_pending")
public class PendingAuditRequest {
    @TableId(type=IdType.INPUT) private String requestId;
    private LocalDateTime receivedAt;
}
