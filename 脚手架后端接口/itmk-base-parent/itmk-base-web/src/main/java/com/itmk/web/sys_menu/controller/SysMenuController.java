package com.itmk.web.sys_menu.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.itmk.config.security.service.PermissionCacheService;
import com.itmk.utils.ResultUtils;
import com.itmk.utils.ResultVo;
import com.itmk.web.sys_menu.entity.MakeMenuTree;
import com.itmk.web.sys_menu.entity.RouterVO;
import com.itmk.web.sys_menu.entity.SysMenu;
import com.itmk.web.sys_menu.service.SysMenuService;
import com.itmk.web.sys_user.entity.SysUser;
import com.itmk.web.sys_user.service.SysUserService;
import org.apache.commons.lang.StringUtils;
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
    @Autowired
    private SysMenuService sysMenuService;
    @Autowired
    private SysUserService sysUserService;
    @Autowired
    private PermissionCacheService permissionCacheService;

    //新增
    @PreAuthorize("hasAuthority('sys:menu:add')")
    @PostMapping
    public ResultVo add(@RequestBody SysMenu sysMenu) {
        sysMenu.setCreateTime(LocalDateTime.now());
        if (sysMenuService.save(sysMenu)) {
            permissionCacheService.invalidateAll();
            return ResultUtils.success("新增成功!");
        }
        return ResultUtils.error("新增失败!");
    }

    //编辑
    @PreAuthorize("hasAuthority('sys:menu:edit')")
    @PutMapping
    public ResultVo edit(@RequestBody SysMenu sysMenu) {
        sysMenu.setUpdateTime(LocalDateTime.now());
        if (sysMenuService.updateById(sysMenu)) {
            permissionCacheService.invalidateAll();
            return ResultUtils.success("编辑成功!");
        }
        return ResultUtils.error("编辑失败!");
    }

    //删除
    @PreAuthorize("hasAuthority('sys:menu:delete')")
    @DeleteMapping("/{menuId}")
    public ResultVo delete(@PathVariable("menuId") Long menuId) {
        //如果存在下级，不能删除
        QueryWrapper<SysMenu> queryWrapper = new QueryWrapper<>();
        queryWrapper.lambda().eq(SysMenu::getParentId,menuId);
        List<SysMenu> list = sysMenuService.list(queryWrapper);
        if(list.size() > 0) {
            return ResultUtils.error("该菜单存在下级，不能删除!");
        }

        if (sysMenuService.removeById(menuId)) {
            permissionCacheService.invalidateAll();
            return ResultUtils.success("删除成功!");
        }
        return ResultUtils.error("删除失败!");
    }

    //列表
    @GetMapping("/list")
    public ResultVo getList(){
        //排序
        QueryWrapper<SysMenu> queryWrapper = new QueryWrapper<>();
        queryWrapper.lambda().orderByAsc(SysMenu::getOrderNum);

        //查询出所有的菜单
        List<SysMenu> list = sysMenuService.list(queryWrapper);
        //组装树数据
        List<SysMenu> menuList = MakeMenuTree.makeTree(list, 0L);
        return ResultUtils.success("查询成功!",menuList);
    }

    //上级菜单
    @GetMapping("/getParent")
    public ResultVo getParent() {
        List<SysMenu> list = sysMenuService.getParent();
        return ResultUtils.success("查询成功!",list);
    }

    //获取菜单
    @GetMapping("/getMenuList")
    public ResultVo getMenuList(Long userId) {
        //获取用户的信息
        SysUser user = sysUserService.getById(userId);
        //菜单数据
        List<SysMenu> menuList = null;
        //判断是否是超级管理员
        if (StringUtils.isNotEmpty(user.getIsAdmin()) && "1".equals(user.getIsAdmin())) {
            QueryWrapper<SysMenu> query = new QueryWrapper<>();
            query.lambda().orderByAsc(SysMenu::getOrderNum);
            menuList = sysMenuService.list(query);
        } else {
           menuList = sysMenuService.getMenuByUserId(userId);
        }
        //过滤菜单数据, 去掉按钮数据
        List<SysMenu> collect = Optional.ofNullable(menuList).orElse(new ArrayList<>())
                .stream()
                .filter(item -> item != null && StringUtils.isNotEmpty(item.getType()) && !item.getType().equals("2"))
                .collect(Collectors.toList());
        //组装路由数据
        List<RouterVO> router = MakeMenuTree.makeRouter(collect,0L);
        System.out.println(router);
        return ResultUtils.success("查询成功!",router);
    }
}
