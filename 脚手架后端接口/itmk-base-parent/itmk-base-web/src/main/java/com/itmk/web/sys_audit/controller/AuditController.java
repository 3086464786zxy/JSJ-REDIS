package com.itmk.web.sys_audit.controller;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.itmk.config.security.service.ManagementPolicy;
import com.itmk.utils.*;
import com.itmk.web.sys_audit.entity.*;
import com.itmk.web.sys_audit.service.AuditEventService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/audit")
public class AuditController {
    private final AuditEventService events;
    private final ManagementPolicy policy;
    public AuditController(AuditEventService events, ManagementPolicy policy) { this.events=events; this.policy=policy; }
    @GetMapping("/list") @PreAuthorize("hasAuthority('sys:audit:list')")
    public ResultVo list(@jakarta.validation.Valid AuditPageParm parm) {
        policy.requireAdmin();
        var query = new LambdaQueryWrapper<AuditEvent>()
                .eq(parm.getRequestId()!=null, AuditEvent::getRequestId, parm.getRequestId())
                .eq(parm.getUserId()!=null, AuditEvent::getUserId, parm.getUserId())
                .eq(parm.getPhase()!=null, AuditEvent::getPhase, parm.getPhase())
                .orderByDesc(AuditEvent::getOccurredAt).orderByDesc(AuditEvent::getEventId);
        return ResultUtils.success("查询成功", events.page(new Page<>(parm.getCurrentPage(),parm.getPageSize()),query));
    }
}
