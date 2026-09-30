package com.itmk.web.sys_user_role.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.itmk.web.sys_user_role.entity.SysUserRole;
import com.itmk.web.sys_user_role.mapper.SysUserRoleMapper;
import com.itmk.web.sys_user_role.service.SysUserRoleService;

import org.springframework.stereotype.Service;

@Service
public class SysUserRoleServiceImpl extends ServiceImpl<SysUserRoleMapper, SysUserRole>
        implements SysUserRoleService {
    @Override
    public void saveRole(SysUserRole sysUserRole) {}
}
