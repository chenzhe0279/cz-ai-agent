package com.cz.czaiagent.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.cz.czaiagent.common.BaseResponse;
import com.cz.czaiagent.common.DeleteRequest;
import com.cz.czaiagent.common.PageResult;
import com.cz.czaiagent.common.ResultUtils;
import com.cz.czaiagent.model.dto.user.*;
import com.cz.czaiagent.model.vo.LoginResponse;
import com.cz.czaiagent.model.vo.UserVO;
import com.cz.czaiagent.service.UserService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 用户接口：注册、登录、个人中心、VIP 兑换、管理员用户管理
 */
@RestController
@RequestMapping("/user")
public class UserController {

    @Resource
    private UserService userService;

    /**
     * 注册
     */
    @PostMapping("/register")
    public BaseResponse<Long> register(@RequestBody UserRegisterRequest request) {
        return ResultUtils.success(userService.register(request));
    }

    /**
     * 登录
     */
    @PostMapping("/login")
    public BaseResponse<LoginResponse> login(@RequestBody UserLoginRequest request) {
        return ResultUtils.success(userService.login(request));
    }

    /**
     * 退出登录
     */
    @PostMapping("/logout")
    public BaseResponse<Boolean> logout() {
        userService.logout();
        return ResultUtils.success(true);
    }

    /**
     * 获取当前登录用户
     */
    @GetMapping("/current")
    public BaseResponse<UserVO> getCurrentUser() {
        return ResultUtils.success(userService.getCurrentUser());
    }

    /**
     * 更新当前用户资料（昵称/头像/简介）
     */
    @PostMapping("/update")
    public BaseResponse<Boolean> updateUser(@RequestBody UserUpdateRequest request) {
        userService.updateUser(request);
        return ResultUtils.success(true);
    }

    /**
     * 修改密码
     */
    @PostMapping("/update/password")
    public BaseResponse<Boolean> updatePassword(@RequestBody UpdatePasswordRequest request) {
        userService.updatePassword(request);
        return ResultUtils.success(true);
    }

    /**
     * 兑换 VIP 会员
     */
    @PostMapping("/vip/exchange")
    public BaseResponse<Boolean> exchangeVipCode(@RequestBody VipExchangeRequest request) {
        userService.exchangeVipCode(request);
        return ResultUtils.success(true);
    }

    /**
     * 上传头像（multipart/form-data，字段名 file）
     */
    @PostMapping("/avatar/upload")
    public BaseResponse<String> uploadAvatar(@RequestParam("file") MultipartFile file) {
        return ResultUtils.success(userService.uploadAvatar(file));
    }

    /**
     * 管理员：新增用户
     */
    @SaCheckRole("admin")
    @PostMapping("/add")
    public BaseResponse<UserVO> addUser(@RequestBody UserAddRequest request) {
        return ResultUtils.success(userService.addUser(request));
    }

    /**
     * 管理员：删除用户
     */
    @SaCheckRole("admin")
    @PostMapping("/delete")
    public BaseResponse<Boolean> deleteUser(@RequestBody DeleteRequest request) {
        userService.deleteUser(request);
        return ResultUtils.success(true);
    }

    /**
     * 管理员：修改用户角色
     */
    @SaCheckRole("admin")
    @PostMapping("/update/role")
    public BaseResponse<Boolean> updateUserRole(@RequestBody UpdateUserRoleRequest request) {
        userService.updateUserRole(request);
        return ResultUtils.success(true);
    }

    /**
     * 管理员：分页查询用户
     */
    @SaCheckRole("admin")
    @GetMapping("/list")
    public BaseResponse<PageResult<UserVO>> listUsers(UserQueryRequest request) {
        return ResultUtils.success(userService.listUsers(request));
    }

    /**
     * 管理员：批量生成 VIP 兑换码
     */
    @SaCheckRole("admin")
    @PostMapping("/vip/code/generate")
    public BaseResponse<List<String>> generateVipCodes(@RequestBody VipCodeGenerateRequest request) {
        return ResultUtils.success(userService.generateVipCodes(request));
    }
}
