package com.itmk.config.security.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 存储在 Redis 中的 RefreshToken 信息。
 * 刷新时通过 sessionId 校验同一会话，轮换凭证而不重建会话。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RefreshTokenDto {
    private Long userId;
    private String username;
    /** 关联的 Session ID，刷新不会更换或重新创建它。 */
    private String sessionId;
}
