package com.itmk.config.security.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.itmk.utils.ResultVo;

import jakarta.servlet.http.*;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component("customAccessDeineHandler")
public class CustomAccessDeineHandler implements AccessDeniedHandler {
    private final ObjectMapper mapper;

    public CustomAccessDeineHandler(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException exception)
            throws IOException {
        response.setStatus(403);
        response.setContentType("application/json;charset=UTF-8");
        mapper.writeValue(response.getOutputStream(), new ResultVo("无权限访问，请联系管理员", 403, null));
    }
}
