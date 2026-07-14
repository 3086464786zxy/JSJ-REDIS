package com.itmk.web.sys_user.entity;

import lombok.AllArgsConstructor;
import lombok.Data;

/** 前端提交验证码时必须同时带回 captchaId。 */
@Data
@AllArgsConstructor
public class CaptchaVo {
    private String captchaId;
    private String image;
}
