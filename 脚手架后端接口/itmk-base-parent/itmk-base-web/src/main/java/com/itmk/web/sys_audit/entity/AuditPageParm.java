package com.itmk.web.sys_audit.entity;
import jakarta.validation.constraints.*;
import lombok.Data;
@Data
public class AuditPageParm {
    @Min(1) @Max(1000000) private long currentPage=1;
    @Min(1) @Max(100) private long pageSize=20;
    @Size(max=36) private String requestId;
    private Long userId;
    @Pattern(regexp="RECEIVED|CHANGED|COMPLETED") private String phase;
}
