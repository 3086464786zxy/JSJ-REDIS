package com.itmk.web.sys_menu.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@TableName("sys_menu")
@Data
public class SysMenu {
    @TableId(type = IdType.AUTO)
    private Long menuId;
    private Long parentId;
    private String title;
    private String code;
    private String name;
    private String path;
    private String url;
    private String type; //菜单类型: 0: 目录  1: 菜单  2: 按钮
    private String icon;
    private String parentName;
    private String orderNum;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    //下级菜单 children字段不属于sys_menu表, 需要排除
    @TableField(exist = false)
    private List<SysMenu> children = new ArrayList<>();
    @TableField(exist = false)
    private Long value;
    @TableField(exist = false)
    private String label;
}
