package com.itmk.config.security.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * 权限缓存 DTO，只保存认证所需字段，绝不缓存密码或完整 SysUser。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PermissionDto {
    private Long userId;
    private String username;
    private boolean enabled;
    private Set<String> permissions;

    public List<SimpleGrantedAuthority> toAuthorities() {
        return permissions == null ? Collections.emptyList() : permissions.stream()
                .map(SimpleGrantedAuthority::new)
                .toList();
    }
}
