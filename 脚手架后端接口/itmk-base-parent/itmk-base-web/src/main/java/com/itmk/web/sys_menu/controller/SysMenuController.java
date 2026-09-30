package com.itmk.web.sys_menu.controller;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.itmk.config.security.filter.CheckTokenFilter;
import com.itmk.config.security.service.PermissionCacheService;
import com.itmk.jwt.JwtUtils;
import com.itmk.utils.ResultUtils;
import com.itmk.utils.ResultVo;
import com.itmk.web.sys_menu.entity.MakeMenuTree;
import com.itmk.web.sys_menu.entity.RouterVO;
import com.itmk.web.sys_menu.entity.SysMenu;
import com.itmk.web.sys_menu.service.SysMenuService;
import com.itmk.web.sys_user.entity.SysUser;
import com.itmk.web.sys_user.service.SysUserService;

import jakarta.servlet.http.HttpServletRequest;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/sysMenu")
public class SysMenuController {
    @Autowired private SysMenuService sysMenuService;
    @Autowired private SysUserService sysUserService;
    @Autowired private PermissionCacheService permissionCacheService;
    @Autowired private JwtUtils jwtUtils;

    @Autowired private com.itmk.config.security.service.ManagementPolicy managementPolicy;
    @Autowired private com.itmk.web.sys_menu.mapper.SysMenuMapper menuMapper;
    @Autowired private com.itmk.web.sys_role_menu.service.RoleMenuService roleMenus;

    private void validateMenu(SysMenu menu, boolean adding) {
        if (menu.getTitle() == null
                || menu.getTitle().isBlank()
                || menu.getTitle().length() > 64
                || !java.util.Set.of("0", "1", "2")
                        .contains(menu.getType() == null ? "" : menu.getType())
                || menu.getParentId() == null
                || menu.getParentId() < 0) throw new IllegalArgumentException("菜单名称、类型或父节点无效");
        if (adding) menu.setMenuId(null);
        var rows = menuMapper.lockHierarchy();
        var byId =
                rows.stream()
                        .collect(java.util.stream.Collectors.toMap(SysMenu::getMenuId, m -> m));
        if (!adding && !byId.containsKey(menu.getMenuId()))
            throw new IllegalArgumentException("菜单不存在");
        byId.put(menu.getMenuId() == null ? -1L : menu.getMenuId(), menu);
        // Validate all paths, including descendants affected by moving an existing node.
        for (SysMenu row : byId.values()) {
            java.util.Set<Long> seen = new java.util.HashSet<>();
            SysMenu node = row;
            int depth = 0;
            while (node != null) {
                if (++depth > 32 || !seen.add(node.getMenuId() == null ? -1L : node.getMenuId()))
                    throw new IllegalArgumentException("菜单不能成环或超过32层");
                if (Long.valueOf(0).equals(node.getParentId())) break;
                node = byId.get(node.getParentId());
                if (node == null || "2".equals(node.getType()))
                    throw new IllegalArgumentException("上级菜单不存在或是按钮");
            }
        }
        menu.setCreateTime(null);
        menu.setUpdateTime(null);
    }

    // 新增
    @PreAuthorize("hasAuthority('sys:menu:add')")
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    @PostMapping
    public ResultVo add(@RequestBody SysMenu sysMenu) {
        managementPolicy.requireAdmin();
        validateMenu(sysMenu, true);
        sysMenu.setCreateTime(LocalDateTime.now());
        if (sysMenuService.save(sysMenu)) {
            permissionCacheService.invalidateAll();
            return ResultUtils.success("新增成功!");
        }
        return ResultUtils.error("新增失败!");
    }

    // 编辑
    @PreAuthorize("hasAuthority('sys:menu:edit')")
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    @PutMapping
    public ResultVo edit(@RequestBody SysMenu sysMenu) {
        managementPolicy.requireAdmin();
        validateMenu(sysMenu, false);
        sysMenu.setUpdateTime(LocalDateTime.now());
        if (sysMenuService.updateById(sysMenu)) {
            permissionCacheService.invalidateAll();
            return ResultUtils.success("编辑成功!");
        }
        return ResultUtils.error("编辑失败!");
    }

    // 删除
    @PreAuthorize("hasAuthority('sys:menu:delete')")
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    @DeleteMapping("/{menuId}")
    public ResultVo delete(@PathVariable("menuId") Long menuId) {
        managementPolicy.requireAdmin();
        menuMapper.lockHierarchy();
        // 如果存在下级，不能删除
        QueryWrapper<SysMenu> queryWrapper = new QueryWrapper<>();
        queryWrapper.lambda().eq(SysMenu::getParentId, menuId);
        if (sysMenuService.exists(queryWrapper)) {
            return ResultUtils.error("该菜单存在下级，不能删除!");
        }

        if (sysMenuService.getById(menuId) == null) throw new IllegalArgumentException("菜单不存在");
        roleMenus.remove(
                new QueryWrapper<com.itmk.web.sys_role_menu.entity.RoleMenu>()
                        .eq("menu_id", menuId));
        if (sysMenuService.removeById(menuId)) {
            permissionCacheService.invalidateAll();
            return ResultUtils.success("删除成功!");
        }
        return ResultUtils.error("删除失败!");
    }

    // 列表
    @PreAuthorize(
            "hasAnyAuthority('sys:menu','sys:menu:list','sys:menu:add','sys:menu:edit','sys:menu:delete')")
    @GetMapping("/list")
    public ResultVo getList() {
        // 排序
        QueryWrapper<SysMenu> queryWrapper = new QueryWrapper<>();
        queryWrapper.lambda().orderByAsc(SysMenu::getOrderNum);

        // 查询出所有的菜单
        List<SysMenu> list = sysMenuService.list(queryWrapper);
        // 组装树数据
        List<SysMenu> menuList = MakeMenuTree.makeTree(list, 0L);
        return ResultUtils.success("查询成功!", menuList);
    }

    // 上级菜单
    @PreAuthorize("hasAnyAuthority('sys:menu:add','sys:menu:edit')")
    @GetMapping("/getParent")
    public ResultVo getParent() {
        List<SysMenu> list = sysMenuService.getParent();
        return ResultUtils.success("查询成功!", list);
    }

    // 获取当前用户的菜单
    @GetMapping("/getMenuList")
    public ResultVo getMenuList(HttpServletRequest request) {
        Long currentUserId = extractUserId(request);
        // 获取用户的信息
        SysUser user = sysUserService.getById(currentUserId);
        // 菜单数据
        List<SysMenu> menuList = null;
        // 判断是否是超级管理员
        if (StringUtils.isNotEmpty(user.getIsAdmin()) && "1".equals(user.getIsAdmin())) {
            QueryWrapper<SysMenu> query = new QueryWrapper<>();
            query.lambda().orderByAsc(SysMenu::getOrderNum);
            menuList = sysMenuService.list(query);
        } else {
            menuList = sysMenuService.getMenuByUserId(currentUserId);
        }
        // 过滤菜单数据, 去掉按钮数据
        List<SysMenu> collect =
                Optional.ofNullable(menuList).orElse(new ArrayList<>()).stream()
                        .filter(
                                item ->
                                        item != null
                                                && StringUtils.isNotEmpty(item.getType())
                                                && !item.getType().equals("2"))
                        .collect(Collectors.toList());
        // 组装路由数据
        List<RouterVO> router = MakeMenuTree.makeRouter(collect, 0L);
        return ResultUtils.success("查询成功!", router);
    }

    /** 从请求的 JWT Token 中提取当前用户 ID */
    private Long extractUserId(HttpServletRequest request) {
        String token = CheckTokenFilter.resolveToken(request);
        DecodedJWT jwt = jwtUtils.jwtDecode(token);
        return Long.valueOf(jwt.getClaim("userId").asString());
    }
}
