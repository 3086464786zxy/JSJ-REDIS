package com.itmk.web.sys_user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.itmk.config.security.dto.SecurityState;
import com.itmk.web.sys_user.entity.SysUser;
import org.apache.ibatis.annotations.Param;
import java.sql.Timestamp;

public interface SecurityStateMapper extends BaseMapper<SysUser> {
    SecurityState read(@Param("userId") Long userId, @Param("sessionId") String sessionId);
    int invalidateAll();
    int invalidateUser(@Param("id") Long id);
    int revokeUser(@Param("id") Long id);
    int revokeSession(@Param("id") Long id, @Param("sid") String sid, @Param("expiry") Timestamp expiry);
    Long lockAuthorizationState();
    long countUsers();
    int insertBootstrapAdmin(@Param("username") String username, @Param("password") String password);
}
