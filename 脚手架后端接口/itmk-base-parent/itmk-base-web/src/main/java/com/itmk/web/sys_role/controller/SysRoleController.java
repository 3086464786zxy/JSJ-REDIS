package com.itmk.web.sys_role.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.itmk.config.security.service.PermissionCacheService;
import com.itmk.utils.ResultUtils;
import com.itmk.utils.ResultVo;
import com.itmk.web.sys_role.entity.RoleParm;
import com.itmk.web.sys_role.entity.SelectItem;
import com.itmk.web.sys_role.entity.SysRole;
import com.itmk.web.sys_role.service.SysRoleService;
import com.itmk.web.sys_role_menu.entity.SaveMenuParm;
import com.itmk.web.sys_role_menu.service.RoleMenuService;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@RequestMapping("/api/role")
@RestController
public class SysRoleController {
    @Autowired private SysRoleService sysRoleService;
    @Autowired private RoleMenuService roleMenuService;
    @Autowired private PermissionCacheService permissionCacheService;

    @Autowired private com.itmk.config.security.service.ManagementPolicy managementPolicy;
    @Autowired private com.itmk.web.sys_user_role.service.SysUserRoleService userRoles;
    @Autowired private com.itmk.web.sys_role.mapper.SysRoleMapper roleMapper;

    // 新增
    @PreAuthorize("hasAuthority('sys:role:add')")
    @PostMapping
    public ResultVo add(@RequestBody SysRole sysRole) {
        sysRole.setRoleId(null);
        if (sysRole.getRoleName() == null
                || sysRole.getRoleName().isBlank()
                || sysRole.getRoleName().length() > 64)
            throw new IllegalArgumentException("角色名称长度应为1至64个字符");
        sysRole.setCreateTime(LocalDateTime.now());
        if (sysRoleService.save(sysRole)) {
            return ResultUtils.success("新增成功!");
        }
        return ResultUtils.error("新增失败!");
    }

    // 编辑
    @PreAuthorize("hasAuthority('sys:role:edit')")
    @PutMapping
    public ResultVo edit(@RequestBody SysRole sysRole) {
        managementPolicy.assertRole(sysRole.getRoleId());
        sysRole.setCreateTime(null);
        sysRole.setUpdateTime(LocalDateTime.now());
        if (sysRoleService.updateById(sysRole)) {
            return ResultUtils.success("编辑成功!");
        }
        return ResultUtils.error("编辑失败!");
    }

    // 删除
    @PreAuthorize("hasAuthority('sys:role:delete')")
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    @DeleteMapping("/{roleId}")
    public ResultVo delete(@PathVariable("roleId") Long roleId) {
        roleMapper.lockRole(roleId);
        managementPolicy.assertRole(roleId);
        roleMenuService.remove(
                new QueryWrapper<com.itmk.web.sys_role_menu.entity.RoleMenu>()
                        .eq("role_id", roleId));
        userRoles.remove(
                new QueryWrapper<com.itmk.web.sys_user_role.entity.SysUserRole>()
                        .eq("role_id", roleId));
        if (sysRoleService.removeById(roleId)) {
            permissionCacheService.invalidateAll();
            return ResultUtils.success("删除成功!");
        }
        return ResultUtils.error("删除失败!");
    }

    // 列表
    @PreAuthorize(
            "hasAnyAuthority('sys:role','sys:role:list','sys:role:add','sys:role:edit','sys:role:delete','sys:role:assign')")
    @GetMapping("/getList")
    public ResultVo getList(@jakarta.validation.Valid RoleParm parm) {
        // 构造分页对象
        IPage<SysRole> page = new Page<>(parm.getCurrentPage(), parm.getPageSize());
        // 构造查询条件
        QueryWrapper<SysRole> query = new QueryWrapper<>();
        if (StringUtils.isNotEmpty(parm.getRoleName())) {
            query.lambda().like(SysRole::getRoleName, parm.getRoleName());
        }
        query.lambda().orderByDesc(SysRole::getCreateTime);
        IPage<SysRole> list = sysRoleService.page(page, query);
        return ResultUtils.success("查询成功", list);
    }

    // 角色下拉数据
    @PreAuthorize("hasAnyAuthority('sys:user:add','sys:user:edit','sys:role:assign')")
    @GetMapping("/selectList")
    public ResultVo selectList() {
        java.util.Set<Long> assignable = managementPolicy.assignableRoles();
        List<SysRole> list =
                sysRoleService.list().stream()
                        .filter(role -> assignable.contains(role.getRoleId()))
                        .toList();
        // 返回的值
        List<SelectItem> selectItems = new ArrayList<>();
        Optional.ofNullable(list)
                .orElse(new ArrayList<>())
                .forEach(
                        item -> {
                            SelectItem vo = new SelectItem();
                            vo.setCheck(false);
                            vo.setLabel(item.getRoleName());
                            vo.setValue(item.getRoleId());
                            selectItems.add(vo);
                        });
        return ResultUtils.success("查询成功", selectItems);
    }

    // 保存角色菜单
    @PreAuthorize("hasAuthority('sys:role:assign')")
    @PostMapping("/saveRoleMenu")
    public ResultVo saveRoleMenu(@jakarta.validation.Valid @RequestBody SaveMenuParm parm) {
        roleMenuService.saveRoleMenu(parm);
        permissionCacheService.invalidateAll();
        return ResultUtils.success("分配成功");
    }
}
