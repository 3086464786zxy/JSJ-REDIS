package com.itmk.web.sys_user.entity;

import com.fasterxml.jackson.annotation.JsonAnySetter;

import jakarta.validation.constraints.*;

import lombok.Data;

@Data
public class UserWriteParm {
    @Positive private Long userId;

    @NotBlank
    @Size(max = 64)
    private String username;

    @Size(max = 64)
    private String password;

    @Size(max = 32)
    private String phone;

    @Email
    @Size(max = 128)
    private String email;

    @Pattern(regexp = "[01]")
    private String sex;

    @NotBlank
    @Size(max = 64)
    private String nickName;

    @NotNull
    @Size(max = 1024)
    @Pattern(regexp = "(?:[1-9][0-9]*(?:,[1-9][0-9]*)*)?")
    private String roleId;

    @JsonAnySetter
    public void rejectUnknown(String name, Object value) {
        throw new IllegalArgumentException("不支持的用户字段");
    }

    public SysUser toUser() {
        SysUser user = new SysUser();
        user.setUserId(userId);
        user.setUsername(username);
        user.setPhone(phone);
        user.setEmail(email);
        user.setSex(sex);
        user.setNickName(nickName);
        user.setRoleId(roleId);
        return user;
    }
}
