package com.itmk.web.sys_user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Collection;

@Data
@TableName("sys_user")
public class SysUser implements UserDetails {
    @TableId(type = IdType.AUTO)
    private Long userId;

    private String username;

    @com.fasterxml.jackson.annotation.JsonProperty(
            access = com.fasterxml.jackson.annotation.JsonProperty.Access.WRITE_ONLY)
    private String password;

    private String phone;
    private String email;
    private String sex;
    private String isAdmin;

    @TableField(exist = false)
    private String roleId;

    // 账户是否过期(1: 未过期, 0: 已过期)
    private boolean isAccountNonExpired = true;
    // 账户是否被锁定(1: 未锁定, 0: 已锁定)
    private boolean isAccountNonLocked = true;
    // 密码是否过期(1: 未过期, 0: 已过期)
    private boolean isCredentialsNonExpired = true;
    // 账户是否可用(1: 可用, 0: 删除用户)
    private boolean isEnabled = true;
    private String nickName;
    // 创建时间
    private LocalDateTime createTime;
    // 更新时间
    private LocalDateTime updateTime;

    // 用户权限字段的集合
    @TableField(exist = false)
    Collection<? extends GrantedAuthority> authorities;
}
