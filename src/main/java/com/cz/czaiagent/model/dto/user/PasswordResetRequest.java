package com.cz.czaiagent.model.dto.user;

import lombok.Data;

import java.io.Serializable;

/**
 * 找回密码请求
 */
@Data
public class PasswordResetRequest implements Serializable {

    /**
     * 邮箱
     */
    private String email;

    /**
     * 验证码
     */
    private String verifyCode;

    /**
     * 新密码
     */
    private String newPassword;

    /**
     * 确认新密码
     */
    private String checkPassword;

    private static final long serialVersionUID = 1L;
}
