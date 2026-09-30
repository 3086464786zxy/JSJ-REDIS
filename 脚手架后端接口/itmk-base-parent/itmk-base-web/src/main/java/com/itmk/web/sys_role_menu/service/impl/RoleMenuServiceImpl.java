package com.itmk.web.sys_role_menu.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.itmk.web.sys_role_menu.entity.RoleMenu;
import com.itmk.web.sys_role_menu.entity.SaveMenuParm;
import com.itmk.web.sys_role_menu.mapper.RoleMenuMapper;
import com.itmk.web.sys_role_menu.service.RoleMenuService;

import org.springframework.stereotype.Service;

@Service
public class RoleMenuServiceImpl extends ServiceImpl<RoleMenuMapper, RoleMenu>
        implements RoleMenuService {
    @org.springframework.beans.factory.annotation.Autowired
    private com.itmk.web.sys_role.mapper.SysRoleMapper roles;

    @org.springframework.beans.factory.annotation.Autowired
    private com.itmk.web.sys_menu.mapper.SysMenuMapper menus;

    @org.springframework.beans.factory.annotation.Autowired
    private com.itmk.config.security.service.ManagementPolicy policy;

    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    @Override
    public void saveRoleMenu(SaveMenuParm parm) {
        if (parm.getList() == null || parm.getRoleId() == null)
            throw new IllegalArgumentException("角色和菜单不能为空");
        if (roles.lockRole(parm.getRoleId()) == null) throw new IllegalArgumentException("角色不存在");
        var ids = parm.getList().stream().distinct().toList();
        if (ids.size() != parm.getList().size()) throw new IllegalArgumentException("菜单不能重复");
        if (!ids.isEmpty() && menus.selectBatchIds(ids).size() != ids.size())
            throw new IllegalArgumentException("菜单不存在");
        policy.assertRole(parm.getRoleId(), ids);
        // 先删除
        QueryWrapper<RoleMenu> query = new QueryWrapper<>();
        query.lambda().eq(RoleMenu::getRoleId, parm.getRoleId());
        this.baseMapper.delete(query);
        // 再保存
        if (!ids.isEmpty() && !this.baseMapper.saveRoleMenu(parm.getRoleId(), ids))
            throw new IllegalStateException("角色菜单保存失败");
    }
}
