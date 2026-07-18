package com.itmk.config.security.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 存储在 Redis 中的 RefreshToken 信息。
 * 刷新时通过 sessionId 关联旧会话，实现 Session Rotation。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RefreshTokenDto {
    private Long userId;
    private String username;
    /** 关联的 Session ID，刷新时用于删除旧会话。 */
    private String sessionId;
}
