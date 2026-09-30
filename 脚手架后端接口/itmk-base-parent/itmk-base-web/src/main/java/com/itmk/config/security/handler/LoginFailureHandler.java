package com.itmk.config.security.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.itmk.utils.ResultVo;

import jakarta.servlet.http.*;

import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component("loginFailureHandler")
public class LoginFailureHandler implements AuthenticationEntryPoint {
    private final ObjectMapper mapper;

    public LoginFailureHandler(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception)
            throws IOException {
        response.setStatus(401);
        response.setContentType("application/json;charset=UTF-8");
        mapper.writeValue(response.getOutputStream(), new ResultVo("认证失败或登录已失效，请重新登录", 600, null));
    }

    public void unavailable(HttpServletResponse response) throws IOException {
        response.setStatus(503);
        response.setContentType("application/json;charset=UTF-8");
        mapper.writeValue(response.getOutputStream(), new ResultVo("认证服务暂时不可用，请稍后重试", 503, null));
    }
}
