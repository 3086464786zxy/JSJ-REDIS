package com.itmk.web.sys_user.entity;

import lombok.Data;

@Data
public class SysUserPage {
    private String phone;
    private String nickname;
    //当前所在页数
    private Long currentPage;
    //每页查询的条数
    private Long pageSize;
}
