package com.cz.czaiagent.service;

import com.cz.czaiagent.common.DeleteRequest;
import com.cz.czaiagent.common.PageResult;
import com.cz.czaiagent.model.dto.user.*;
import com.cz.czaiagent.model.entity.User;
import com.cz.czaiagent.model.vo.LoginResponse;
import com.cz.czaiagent.model.vo.UserVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 用户服务
 */
public interface UserService {

    /**
     * 注册
     */
    Long register(UserRegisterRequest request);

    /**
     * 登录，返回用户信息与令牌
     */
    LoginResponse login(UserLoginRequest request);

    /**
     * 退出登录
     */
    void logout();

    /**
     * 获取当前登录用户（脱敏）
     */
    UserVO getCurrentUser();

    /**
     * 获取原始用户实体（供 Sa-Token 角色查询等内部使用）
     */
    User getUserById(Long id);

    /**
     * 更新当前用户资料（昵称/头像/简介；管理员可指定 id 修改他人资料）
     */
    void updateUser(UserUpdateRequest request);

    /**
     * 修改密码（成功后强制重新登录）
     */
    void updatePassword(UpdatePasswordRequest request);

    /**
     * 兑换 VIP 会员
     */
    void exchangeVipCode(VipExchangeRequest request);

    /**
     * 管理员新增用户（初始密码 12345678，首次登录后应修改）
     */
    UserVO addUser(UserAddRequest request);

    /**
     * 管理员删除用户（软删除，同时踢下线）
     */
    void deleteUser(DeleteRequest request);

    /**
     * 管理员修改用户角色
     */
    void updateUserRole(UpdateUserRoleRequest request);

    /**
     * 管理员分页查询用户
     */
    PageResult<UserVO> listUsers(UserQueryRequest request);

    /**
     * 管理员批量生成 VIP 兑换码
     */
    List<String> generateVipCodes(VipCodeGenerateRequest request);

    /**
     * 上传头像（保存到本地 tmp/avatar，返回相对访问路径并更新当前用户）
     */
    String uploadAvatar(MultipartFile file);
}
