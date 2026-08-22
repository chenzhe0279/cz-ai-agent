package com.cz.czaiagent.model.dto.user;

import lombok.Data;

import java.io.Serializable;

/**
 * 管理员批量生成 VIP 兑换码请求
 */
@Data
public class VipCodeGenerateRequest implements Serializable {

    /**
     * 生成数量（1-100，默认 1）
     */
    private int count = 1;

    /**
     * 兑换后会员时长（天，1-3650，默认 30）
     */
    private int durationDays = 30;

    private static final long serialVersionUID = 1L;
}
