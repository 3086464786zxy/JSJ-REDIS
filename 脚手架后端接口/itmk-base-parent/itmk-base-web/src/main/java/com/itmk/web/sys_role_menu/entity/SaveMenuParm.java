package com.itmk.web.sys_role_menu.entity;

import lombok.Data;

import java.util.List;

@Data
public class SaveMenuParm {
    @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.Positive
    private Long roleId;

    @jakarta.validation.constraints.NotNull
    @jakarta.validation.constraints.Size(max = 2000)
    private List<
                    @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.Positive
                    Long>
            list;
}
