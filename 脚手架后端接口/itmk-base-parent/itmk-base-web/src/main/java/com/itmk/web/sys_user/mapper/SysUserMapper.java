package com.itmk.web.sys_user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.itmk.web.sys_user.entity.SysUser;

public interface SysUserMapper extends BaseMapper<SysUser> {
    @org.apache.ibatis.annotations.Select(
            "SELECT user_id FROM sys_user WHERE user_id = #{id} FOR UPDATE")
    Long lockUser(@org.apache.ibatis.annotations.Param("id") Long id);
}
