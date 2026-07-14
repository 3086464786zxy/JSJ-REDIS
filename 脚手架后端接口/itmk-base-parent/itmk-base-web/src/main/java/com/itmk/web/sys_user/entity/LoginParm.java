package com.itmk.web.sys_user.entity;

import lombok.Data;

@Data
public class LoginParm {
    private String username;
    private String password;
    // Redis 验证码唯一标识
    private String captchaId;
    // 用户输入的验证码
    private String code;
}
