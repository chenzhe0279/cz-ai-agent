package com.cz.czaiagent.model.dto.user;

import lombok.Data;

import java.io.Serializable;

/**
 * 绑定邮箱请求
 */
@Data
public class EmailBindRequest implements Serializable {

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
