package com.itmk.web.sys_role_menu.entity;

import com.itmk.web.sys_menu.entity.SysMenu;
import lombok.Data;

import java.util.List;

@Data
public class SaveMenuParm {
    private Long roleId;
    private List<Long> list;
}
