package com.itmk.config.audit;

import com.itmk.utils.ResultVo;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.*;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

@RestControllerAdvice
public class AuditResultAdvice implements ResponseBodyAdvice<Object> {
    public boolean supports(MethodParameter p, Class<? extends HttpMessageConverter<?>> c) { return true; }
    public Object beforeBodyWrite(Object body, MethodParameter p, MediaType t,
            Class<? extends HttpMessageConverter<?>> c, ServerHttpRequest request, ServerHttpResponse response) {
        if (body instanceof ResultVo result && request instanceof ServletServerHttpRequest servlet)
            servlet.getServletRequest().setAttribute("audit.resultCode", result.getCode());
        return body;
    }
}
