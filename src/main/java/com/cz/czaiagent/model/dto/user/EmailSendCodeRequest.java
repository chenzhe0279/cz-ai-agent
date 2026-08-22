package com.cz.czaiagent.model.dto.user;

import lombok.Data;

import java.io.Serializable;

/**
 * 发送邮箱验证码请求
 */
@Data
public class EmailSendCodeRequest implements Serializable {

    /**
     * 邮箱
     */
    private String email;

    private static final long serialVersionUID = 1L;
}
