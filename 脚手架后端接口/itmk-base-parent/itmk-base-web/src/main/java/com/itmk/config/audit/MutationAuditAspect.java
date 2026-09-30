package com.itmk.config.audit;

import com.itmk.utils.ResultVo;
import com.itmk.web.sys_user.entity.*;
import com.itmk.web.sys_role.entity.SysRole;
import com.itmk.web.sys_role_menu.entity.SaveMenuParm;
import com.itmk.web.sys_menu.entity.SysMenu;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Runs inside the business transaction; audit failure rolls back the change. */
@Aspect @Component @Order(Ordered.LOWEST_PRECEDENCE)
public class MutationAuditAspect {
    private final AuditService audit;
    private final com.fasterxml.jackson.databind.ObjectMapper json;
    public MutationAuditAspect(AuditService audit, com.fasterxml.jackson.databind.ObjectMapper json) { this.audit=audit; this.json=json; }
    @Around("@annotation(com.itmk.config.audit.AuditedChange)")
    public Object record(ProceedingJoinPoint point) throws Throwable {
        var attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servlet)) return point.proceed();
        var request=servlet.getRequest();
        Object result=point.proceed();
        var details=new java.util.LinkedHashMap<String,Object>();
        for (Object arg:point.getArgs()) {
            Long id=null;
            if (arg instanceof UserWriteParm p) {
                id=p.getUserId(); details.put("roleIds",p.getRoleId()); details.put("profileUpdated",true);
            } else if (arg instanceof ResetPasswordParm p) {
                id=p.getUserId(); details.put("passwordChanged",true);
            } else if (arg instanceof UpdatePasswordParm) {
                id=(Long) request.getAttribute("audit.userId"); details.put("passwordChanged",true);
            } else if (arg instanceof SysRole p) {
                id=p.getRoleId(); details.put("roleName",p.getRoleName());
            } else if (arg instanceof SaveMenuParm p) {
                id=p.getRoleId(); details.put("menuIds",p.getList());
            } else if (arg instanceof SysMenu p) {
                id=p.getMenuId(); details.put("permissionCode",p.getCode()); details.put("parentId",p.getParentId());
            }
            else if (arg instanceof Long p) id=p;
            if (id!=null) request.setAttribute("audit.targetId",id);
        }
        if (result instanceof ResultVo<?> vo && vo.getCode()==200) {
            if (!TransactionSynchronizationManager.isActualTransactionActive())
                throw new IllegalStateException("业务修改与审计必须在同一事务中提交");
            request.setAttribute("audit.details",json.writeValueAsString(details));
            try { audit.appendMutation(request); }
            catch(RuntimeException e) { audit.failure(); throw e; }
        }
        return result;
    }
}
