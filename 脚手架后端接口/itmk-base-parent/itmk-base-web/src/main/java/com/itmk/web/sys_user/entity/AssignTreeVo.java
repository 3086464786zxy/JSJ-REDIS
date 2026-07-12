package com.itmk.web.sys_user.entity;

import com.itmk.web.sys_menu.entity.SysMenu;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AssignTreeVo {
    private List<SysMenu> menuList = new ArrayList<>();
    private Object[] checkList;
}
