package com.itmk.config.security.service;

import java.nio.charset.StandardCharsets;

public final class PasswordPolicy {
    private PasswordPolicy() {}

    public static void validate(String password) {
        if (password == null
                || password.isBlank()
                || password.length() < 12
                || password.length() > 64
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("新密码需要12至64个字符，UTF-8编码不能超过72字节");
        }
    }
}
