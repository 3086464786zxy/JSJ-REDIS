package com.itmk.config.security.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.itmk.web.sys_menu.entity.SysMenu;
import com.itmk.web.sys_menu.mapper.SysMenuMapper;
import com.itmk.web.sys_role.mapper.SysRoleMapper;
import com.itmk.web.sys_user.entity.SysUser;
import com.itmk.web.sys_user.mapper.SysUserMapper;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class ManagementPolicy {
    private final com.itmk.web.sys_user_role.mapper.SysUserRoleMapper userRoles;
    private final SysUserMapper users;
    private final SysRoleMapper roles;
    private final SysMenuMapper menus;

    public ManagementPolicy(
            SysUserMapper users,
            SysRoleMapper roles,
            SysMenuMapper menus,
            com.itmk.web.sys_user_role.mapper.SysUserRoleMapper userRoles) {
        this.users = users;
        this.roles = roles;
        this.menus = menus;
        this.userRoles = userRoles;
    }

    public SysUser actor() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) throw new AccessDeniedException("需要登录");
        SysUser user = users.selectOne(new QueryWrapper<SysUser>().eq("username", auth.getName()));
        if (user == null
                || !user.isEnabled()
                || !user.isAccountNonLocked()
                || !user.isAccountNonExpired()
                || !user.isCredentialsNonExpired()) throw new AccessDeniedException("账户不可用");
        return user;
    }

    public void requireAdmin() {
        if (!"1".equals(actor().getIsAdmin())) throw new AccessDeniedException("菜单定义只允许超级管理员修改");
    }

    public void assertUser(Long id, boolean deleting) {
        SysUser actor = actor(), target = users.selectById(id);
        if (target == null) throw new IllegalArgumentException("用户不存在");
        if (deleting && Objects.equals(id, actor.getUserId()))
            throw new IllegalArgumentException("不能删除当前登录账户");
        if (!"1".equals(actor.getIsAdmin())) {
            if ("1".equals(target.getIsAdmin())) throw new AccessDeniedException("不能修改超级管理员账户");
            Set<Long> blocked = menus.roleIdsOutsideMenus(actor.getUserId());
            if (userRoles
                    .selectList(
                            new QueryWrapper<com.itmk.web.sys_user_role.entity.SysUserRole>()
                                    .eq("user_id", id))
                    .stream()
                    .anyMatch(r -> blocked.contains(r.getRoleId())))
                throw new AccessDeniedException("不能修改权限超出自身范围的账户");
        }
    }

    private Set<Long> allowed(SysUser user) {
        return menus.getMenuByUserId(user.getUserId()).stream()
                .map(SysMenu::getMenuId)
                .collect(Collectors.toSet());
    }

    public void assertRole(Long roleId) {
        assertRole(roleId, List.of());
    }

    public void assertRole(Long roleId, Collection<Long> requested) {
        if (roleId == null || roles.selectById(roleId) == null)
            throw new IllegalArgumentException("角色不存在");
        SysUser actor = actor();
        if (!"1".equals(actor.getIsAdmin())) {
            Set<Long> allowed = allowed(actor);
            if (!allowed.containsAll(requested)
                    || menus.getMenuByRoleId(roleId).stream()
                            .anyMatch(m -> !allowed.contains(m.getMenuId())))
                throw new AccessDeniedException("不能分配或修改超出自身范围的角色权限");
        }
    }

    public List<Long> parseRoles(String value) {
        if (value == null) throw new IllegalArgumentException("角色不能为空");
        if (value.isBlank()) return List.of();
        try {
            return Arrays.stream(value.split(",")).map(Long::valueOf).distinct().toList();
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("角色格式错误");
        }
    }

    public void assertUserRoles(String value) {
        var ids = parseRoles(value);
        if (ids.isEmpty()) return;
        var rows =
                roles.selectList(
                        new QueryWrapper<com.itmk.web.sys_role.entity.SysRole>()
                                .in("role_id", ids)
                                .orderByAsc("role_id")
                                .last("FOR UPDATE"));
        if (rows.size() != ids.size()) throw new IllegalArgumentException("角色不存在");
        SysUser actor = actor();
        if (!"1".equals(actor.getIsAdmin())
                && menus.roleIdsOutsideMenus(actor.getUserId()).stream().anyMatch(ids::contains))
            throw new AccessDeniedException("不能分配超出自身范围的角色");
    }

    public Set<Long> assignableRoles() {
        SysUser actor = actor();
        var all = roles.selectList(null);
        if ("1".equals(actor.getIsAdmin()))
            return all.stream().map(r -> r.getRoleId()).collect(Collectors.toSet());
        // One joined query avoids querying permissions once per role.
        Set<Long> blocked = menus.roleIdsOutsideMenus(actor.getUserId());
        return all.stream()
                .map(r -> r.getRoleId())
                .filter(id -> !blocked.contains(id))
                .collect(Collectors.toSet());
    }
}
