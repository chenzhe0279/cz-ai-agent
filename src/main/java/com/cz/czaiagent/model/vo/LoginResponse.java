package com.cz.czaiagent.model.vo;

/**
 * 登录响应：脱敏用户信息 + Sa-Token 令牌
 */
public record LoginResponse(UserVO user, String token) {
}
