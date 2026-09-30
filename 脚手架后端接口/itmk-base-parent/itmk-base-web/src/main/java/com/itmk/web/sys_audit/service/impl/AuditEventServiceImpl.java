package com.itmk.web.sys_audit.service.impl;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.itmk.web.sys_audit.entity.AuditEvent;
import com.itmk.web.sys_audit.mapper.AuditEventMapper;
import com.itmk.web.sys_audit.service.AuditEventService;
import org.springframework.stereotype.Service;
@Service
public class AuditEventServiceImpl extends ServiceImpl<AuditEventMapper, AuditEvent> implements AuditEventService {}
