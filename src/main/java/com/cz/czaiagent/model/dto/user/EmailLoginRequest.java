package com.cz.czaiagent.model.dto.user;

import lombok.Data;

import java.io.Serializable;

/**
 * 邮箱验证码登录请求
 */
@Data
public class EmailLoginRequest implements Serializable {

    /**
     * 邮箱
     */
    private String email;

    /**
     * 验证码
     */
    private String verifyCode;

    private static final long serialVersionUID = 1L;
}
