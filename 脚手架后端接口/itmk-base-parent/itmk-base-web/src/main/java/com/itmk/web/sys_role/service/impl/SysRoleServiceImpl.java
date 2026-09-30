package com.itmk.web.sys_role.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.itmk.web.sys_role.entity.SysRole;
import com.itmk.web.sys_role.mapper.SysRoleMapper;
import com.itmk.web.sys_role.service.SysRoleService;

import org.springframework.stereotype.Service;

@Service
public class SysRoleServiceImpl extends ServiceImpl<SysRoleMapper, SysRole>
        implements SysRoleService {}
