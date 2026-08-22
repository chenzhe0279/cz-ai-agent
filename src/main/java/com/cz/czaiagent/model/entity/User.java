package com.cz.czaiagent.model.entity;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 用户实体，对应数据库 user 表
 */
@Data
public class User implements Serializable {

    private Long id;

    /**
     * 账号
     */
    private String userAccount;

    /**
     * 密码（BCrypt 哈希）
     */
    private String userPassword;

    /**
     * 用户昵称
     */
    private String userName;

    /**
     * 用户头像
     */
    private String userAvatar;

    /**
     * 用户简介
     */
    private String userProfile;

    /**
     * 用户角色：user / vip / admin
     */
    private String userRole;

    /**
     * 会员过期时间
     */
    private Date vipExpireTime;

    /**
     * 会员兑换码
     */
    private String vipCode;

    /**
     * 会员编号
     */
    private Long vipNumber;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 更新时间
     */
    private Date updateTime;

    /**
     * 是否删除：0-正常 1-删除（软删除）
     */
    private Integer isDelete;

    private static final long serialVersionUID = 1L;
}
