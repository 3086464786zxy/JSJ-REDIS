package com.itmk.web.sys_menu.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.itmk.web.sys_menu.entity.MakeMenuTree;
import com.itmk.web.sys_menu.entity.SysMenu;
import com.itmk.web.sys_menu.mapper.SysMenuMapper;
import com.itmk.web.sys_menu.service.SysMenuService;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Service
public class SysMenuServiceImpl extends ServiceImpl<SysMenuMapper, SysMenu> implements SysMenuService {
    @Override
    public List<SysMenu> getParent() {
        String[] type = {"0", "1"};
        List<String> strings = Arrays.asList(type);
        QueryWrapper<SysMenu> queryWrapper = new QueryWrapper<>();
        queryWrapper.lambda().in(SysMenu::getType, strings).orderByAsc(SysMenu::getOrderNum);
        List<SysMenu> menuList = baseMapper.selectList(queryWrapper);
        //组装顶级树
        SysMenu menu = new SysMenu();
        menu.setTitle("顶级菜单");
        menu.setLabel("顶级菜单");
        menu.setParentId(-1L);
        //顶级菜单的menuId统一设置成0
        menu.setMenuId(0L);
        menu.setValue(0L);
        menuList.add(menu);
        //组装菜单树
        List<SysMenu> tree = MakeMenuTree.makeTree(menuList,-1L);
        return tree;
    }

    @Override
    public List<SysMenu> getMenuByUserId(Long userId) {
        return this.baseMapper.getMenuByUserId(userId);
    }

    @Override
    public List<SysMenu> getMenuByRoleId(Long roleId) {
        return this.baseMapper.getMenuByRoleId(roleId);
    }


}
