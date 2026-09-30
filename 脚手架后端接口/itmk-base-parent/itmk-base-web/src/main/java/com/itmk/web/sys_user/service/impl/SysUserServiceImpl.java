package com.itmk.web.sys_user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.itmk.web.sys_menu.entity.MakeMenuTree;
import com.itmk.web.sys_menu.entity.SysMenu;
import com.itmk.web.sys_menu.service.SysMenuService;
import com.itmk.web.sys_user.entity.AssignTreeParm;
import com.itmk.web.sys_user.entity.AssignTreeVo;
import com.itmk.web.sys_user.entity.SysUser;
import com.itmk.web.sys_user.mapper.SysUserMapper;
import com.itmk.web.sys_user.service.SysUserService;
import com.itmk.web.sys_user_role.entity.SysUserRole;
import com.itmk.web.sys_user_role.service.SysUserRoleService;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser>
        implements SysUserService {
    @Autowired private SysUserRoleService sysUserRoleService;
    @Autowired private SysMenuService sysMenuService;

    @Autowired private com.itmk.config.security.service.ManagementPolicy managementPolicy;
    @Autowired private com.itmk.config.security.service.SecurityStateService securityStates;

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void saveUser(SysUser sysUser) {
        managementPolicy.assertUserRoles(sysUser.getRoleId());
        // 插入用户信息
        int i = this.baseMapper.insert(sysUser);
        if (i != 1) throw new IllegalStateException("用户新增失败");
        // 设置用户的角色
        if (i > 0) {
            // 把前端逗号分隔的字符串转为数组
            String[] split =
                    sysUser.getRoleId().isBlank()
                            ? new String[0]
                            : java.util.Arrays.stream(sysUser.getRoleId().split(","))
                                    .distinct()
                                    .toArray(String[]::new);
            if (split.length > 0) {
                List<SysUserRole> roles = new ArrayList<>();
                for (int j = 0; j < split.length; j++) {
                    SysUserRole userRole = new SysUserRole();
                    userRole.setUserId(sysUser.getUserId());
                    userRole.setRoleId(Long.parseLong(split[j]));
                    roles.add(userRole);
                }
                // 保存到用户角色表
                if (!sysUserRoleService.saveBatch(roles)) throw new IllegalStateException("角色保存失败");
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void editUser(SysUser sysUser) {
        // 编辑用户信息
        this.baseMapper.lockUser(sysUser.getUserId());
        managementPolicy.assertUser(sysUser.getUserId(), false);
        managementPolicy.assertUserRoles(sysUser.getRoleId());
        var update = new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<SysUser>();
        update.eq("user_id", sysUser.getUserId())
                .set("username", sysUser.getUsername())
                .set("phone", sysUser.getPhone())
                .set("email", sysUser.getEmail())
                .set("sex", sysUser.getSex())
                .set("nick_name", sysUser.getNickName())
                .set("update_time", sysUser.getUpdateTime());
        int i = this.baseMapper.update(null, update);
        if (i != 1) throw new IllegalArgumentException("用户不存在或编辑失败");
        // 设置用户的角色
        if (i > 0) {
            String[] split =
                    sysUser.getRoleId().isBlank()
                            ? new String[0]
                            : java.util.Arrays.stream(sysUser.getRoleId().split(","))
                                    .distinct()
                                    .toArray(String[]::new);
            // 删除用户原来的角色
            QueryWrapper<SysUserRole> queryWrapper = new QueryWrapper<>();
            queryWrapper.lambda().eq(SysUserRole::getUserId, sysUser.getUserId());
            sysUserRoleService.remove(queryWrapper);
            if (split.length > 0) {
                List<SysUserRole> roles = new ArrayList<>();
                for (int j = 0; j < split.length; j++) {
                    SysUserRole userRole = new SysUserRole();
                    userRole.setUserId(sysUser.getUserId());
                    userRole.setRoleId(Long.parseLong(split[j]));
                    roles.add(userRole);
                }
                // 保存到用户角色表
                if (!sysUserRoleService.saveBatch(roles)) throw new IllegalStateException("角色保存失败");
            }
        }
        securityStates.invalidateUser(sysUser.getUserId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteUser(Long userId) {
        this.baseMapper.lockUser(userId);
        managementPolicy.assertUser(userId, true);
        QueryWrapper<SysUserRole> queryWrapper = new QueryWrapper<>();
        queryWrapper.lambda().eq(SysUserRole::getUserId, userId);
        sysUserRoleService.remove(queryWrapper);
        if (this.baseMapper.deleteById(userId) != 1)
            throw new IllegalArgumentException("用户不存在或删除失败");
    }

    @Override
    public AssignTreeVo getAssignTreeVo(AssignTreeParm parm) {
        // 查询用户的信息
        SysUser user = this.baseMapper.selectById(parm.getUserId());
        List<SysMenu> menuList = null;
        // 判断是否是超级管理员
        if (user != null
                && StringUtils.isNotEmpty(user.getIsAdmin())
                && "1".equals(user.getIsAdmin())) {
            // 是超级管理员，查询所有的菜单
            menuList = sysMenuService.list();
        } else {
            menuList = sysMenuService.getMenuByUserId(parm.getUserId());
        }
        // 组装树
        List<SysMenu> makeTree = MakeMenuTree.makeTree(menuList, 0L);
        // 查询角色原来的菜单
        List<SysMenu> roleMenuList = sysMenuService.getMenuByRoleId(parm.getRoleId());
        List<Long> ids = new ArrayList<>();
        Optional.ofNullable(roleMenuList).orElse(new ArrayList<>()).stream()
                .filter(item -> item != null)
                .forEach(
                        item -> {
                            ids.add(item.getMenuId());
                        });
        // 组装返回数据
        AssignTreeVo assignTreeVo = new AssignTreeVo();
        assignTreeVo.setMenuList(makeTree);
        assignTreeVo.setCheckList(ids.toArray());
        return assignTreeVo;
    }

    @Override
    public SysUser loadUser(String username) {
        QueryWrapper<SysUser> query = new QueryWrapper<>();
        query.lambda().eq(SysUser::getUsername, username);
        // 根据用户名查询
        SysUser user = this.baseMapper.selectOne(query);
        return user;
    }
}
