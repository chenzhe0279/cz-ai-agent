package com.cz.czaiagent.config;

import cn.dev33.satoken.stp.StpInterface;
import com.cz.czaiagent.model.entity.User;
import com.cz.czaiagent.service.UserService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Sa-Token 角色数据源：从数据库读取用户角色，支撑 @SaCheckRole 校验
 */
@Component
public class StpInterfaceImpl implements StpInterface {

    @Resource
    private UserService userService;

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        return List.of();
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        User user = userService.getUserById(parseId(loginId));
        return user == null ? List.of() : List.of(user.getUserRole());
    }

    private Long parseId(Object loginId) {
        if (loginId == null) {
            return null;
        }
        try {
            return Long.valueOf(loginId.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
