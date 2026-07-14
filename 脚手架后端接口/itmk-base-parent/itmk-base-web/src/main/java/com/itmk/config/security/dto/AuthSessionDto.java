package com.itmk.config.security.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Redis 中保存的最小登录会话，不包含 Token 和用户隐私信息。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthSessionDto {
    private Long userId;
    private String username;
    private long loginTime;
}
