package com.cz.czaiagent.model.dto.user;

import lombok.Data;

import java.io.Serializable;

/**
 * 管理员修改用户角色请求
 */
@Data
public class UpdateUserRoleRequest implements Serializable {

    /**
     * 用户 id
     */
    private Long id;

    /**
     * 新角色：user / vip / admin
     */
    private String userRole;

    private static final long serialVersionUID = 1L;
}
