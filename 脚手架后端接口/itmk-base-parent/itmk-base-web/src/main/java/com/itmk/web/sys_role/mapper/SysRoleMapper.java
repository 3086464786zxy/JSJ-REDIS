package com.itmk.web.sys_role.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.itmk.web.sys_role.entity.SysRole;

public interface SysRoleMapper extends BaseMapper<SysRole> {
    @org.apache.ibatis.annotations.Select(
            "SELECT role_id FROM sys_role WHERE role_id = #{id} FOR UPDATE")
    Long lockRole(@org.apache.ibatis.annotations.Param("id") Long id);
}
