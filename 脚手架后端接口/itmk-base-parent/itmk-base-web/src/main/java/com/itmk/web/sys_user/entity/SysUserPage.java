package com.itmk.web.sys_user.entity;

import lombok.Data;

@Data
public class SysUserPage {
    private String phone;
    private String nickname;

    // 当前所在页数
    @jakarta.validation.constraints.Min(1)
    @jakarta.validation.constraints.Max(1000000)
    private Long currentPage = 1L;

    // 每页查询的条数
    @jakarta.validation.constraints.Min(1)
    @jakarta.validation.constraints.Max(100)
    private Long pageSize = 10L;
}
