package com.itmk.web.sys_role_menu.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.itmk.web.sys_role_menu.entity.RoleMenu;
import com.itmk.web.sys_role_menu.entity.SaveMenuParm;
import com.itmk.web.sys_role_menu.mapper.RoleMenuMapper;
import com.itmk.web.sys_role_menu.service.RoleMenuService;
import org.springframework.stereotype.Service;

@Service
public class RoleMenuServiceImpl extends ServiceImpl<RoleMenuMapper, RoleMenu> implements RoleMenuService {
    @Override
    public void saveRoleMenu(SaveMenuParm parm) {
       //先删除
        QueryWrapper<RoleMenu> query = new QueryWrapper<>();
        query.lambda().eq(RoleMenu::getRoleId, parm.getRoleId());
        this.baseMapper.delete(query);
       //再保存
        this.baseMapper.saveRoleMenu(parm.getRoleId(),parm.getList());
    }
}
