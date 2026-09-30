package com.itmk.web.sys_role.entity;

import lombok.Data;

@Data
public class RoleParm {
    // 当前第几页
    @jakarta.validation.constraints.Min(1)
    @jakarta.validation.constraints.Max(1000000)
    private Long currentPage = 1L;

    // 每页查询的条数
    @jakarta.validation.constraints.Min(1)
    @jakarta.validation.constraints.Max(100)
    private Long pageSize = 10L;

    // 查询角色的名称
    private String roleName;
}
