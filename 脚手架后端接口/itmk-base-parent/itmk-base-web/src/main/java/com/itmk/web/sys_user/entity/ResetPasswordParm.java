package com.itmk.web.sys_user.entity;

import com.fasterxml.jackson.annotation.JsonAnySetter;

import jakarta.validation.constraints.*;

import lombok.Data;

@Data
public class ResetPasswordParm {
    @NotNull @Positive private Long userId;

    @NotBlank
    @Size(max = 64)
    private String password;

    @JsonAnySetter
    public void rejectUnknown(String name, Object value) {
        throw new IllegalArgumentException("不支持的密码重置字段");
    }
}
