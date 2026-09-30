package com.itmk.web.sys_menu.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.itmk.web.sys_menu.entity.SysMenu;

import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface SysMenuMapper extends BaseMapper<SysMenu> {
    @org.apache.ibatis.annotations.Select(
            "SELECT DISTINCT rm.role_id FROM sys_role_menu rm WHERE NOT EXISTS (SELECT 1 FROM"
                    + " sys_user_role ur JOIN sys_role_menu own ON own.role_id = ur.role_id WHERE"
                    + " ur.user_id = #{userId} AND own.menu_id = rm.menu_id)")
    java.util.Set<Long> roleIdsOutsideMenus(@Param("userId") Long userId);

    @org.apache.ibatis.annotations.Select("SELECT * FROM sys_menu ORDER BY menu_id FOR UPDATE")
    List<SysMenu> lockHierarchy();

    // 根据用户id查询菜单
    List<SysMenu> getMenuByUserId(@Param("userId") Long userId);

    // 根据角色id查询菜单
    List<SysMenu> getMenuByRoleId(@Param("roleId") Long roleId);
}
