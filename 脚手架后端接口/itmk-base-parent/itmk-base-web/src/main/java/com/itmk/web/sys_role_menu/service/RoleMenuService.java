package com.itmk.web.sys_role_menu.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.itmk.web.sys_role_menu.entity.RoleMenu;
import com.itmk.web.sys_role_menu.entity.SaveMenuParm;

public interface RoleMenuService extends IService<RoleMenu> {
    void saveRoleMenu(SaveMenuParm parm);
}
