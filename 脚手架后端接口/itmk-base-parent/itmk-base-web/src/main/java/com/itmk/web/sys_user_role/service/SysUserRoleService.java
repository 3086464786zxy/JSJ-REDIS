package com.itmk.web.sys_user_role.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.itmk.web.sys_user_role.entity.SysUserRole;

public interface SysUserRoleService extends IService<SysUserRole> {
    //保存角色
    void saveRole(SysUserRole sysUserRole);
}
