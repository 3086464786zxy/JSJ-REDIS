package com.itmk.config.exception;

import com.itmk.utils.ResultVo;

import jakarta.validation.ConstraintViolationException;

import org.slf4j.*;
import org.springframework.dao.*;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    private ResponseEntity<ResultVo> result(int code, String msg) {
        return ResponseEntity.status(code).body(new ResultVo(msg, code, null));
    }

    @ExceptionHandler({
        MethodArgumentNotValidException.class,
        BindException.class,
        ConstraintViolationException.class,
        HttpMessageNotReadableException.class
    })
    public ResponseEntity<ResultVo> invalid(Exception e) {
        return result(400, "请求参数不符合要求");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ResultVo> argument(IllegalArgumentException e) {
        return result(400, e.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ResultVo> denied(Exception e) {
        return result(403, "无权限访问，请联系管理员");
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ResultVo> authentication(Exception e) {
        return result(401, "账号、密码错误或账户不可用");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ResultVo> conflict(Exception e) {
        return result(409, "数据重复或存在关联，请检查后重试");
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ResultVo> unavailable(Exception e) {
        log.error("Data service unavailable: {}", e.getClass().getSimpleName());
        return result(503, "数据服务暂时不可用，请稍后重试");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ResultVo> unexpected(Exception e) {
        log.error("Request failed: {}", e.getClass().getSimpleName());
        return result(500, "服务处理失败，请联系管理员并提供请求编号");
    }
}
