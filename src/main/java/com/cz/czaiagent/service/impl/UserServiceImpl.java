package com.cz.czaiagent.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.cz.czaiagent.common.DeleteRequest;
import com.cz.czaiagent.common.PageResult;
import com.cz.czaiagent.constant.FileConstant;
import com.cz.czaiagent.constant.UserConstant;
import com.cz.czaiagent.exception.BusinessException;
import com.cz.czaiagent.exception.ErrorCode;
import com.cz.czaiagent.exception.ThrowUtils;
import com.cz.czaiagent.model.dto.user.*;
import com.cz.czaiagent.model.entity.User;
import com.cz.czaiagent.model.enums.UserRoleEnum;
import com.cz.czaiagent.model.vo.LoginResponse;
import com.cz.czaiagent.model.vo.UserVO;
import com.cz.czaiagent.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 用户服务实现（基于 JdbcTemplate，不引入额外 ORM）
 */
@Service
@Slf4j
public class UserServiceImpl implements UserService {

    /**
     * 管理员新增用户时的初始密码
     */
    private static final String INITIAL_PASSWORD = "12345678";

    private static final Pattern ACCOUNT_PATTERN = Pattern.compile("^[A-Za-z0-9_]+$");

    private static final Set<String> ALLOWED_AVATAR_EXTS = Set.of("jpg", "jpeg", "png", "gif", "webp");

    private static final long MAX_AVATAR_SIZE = 5 * 1024 * 1024L;

    private final JdbcTemplate jdbcTemplate;

    public UserServiceImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Long register(UserRegisterRequest request) {
        String account = StrUtil.trim(request.getUserAccount());
        String password = request.getUserPassword();
        ThrowUtils.throwIf(StrUtil.isBlank(account) || account.length() < 4 || account.length() > 16,
                ErrorCode.PARAMS_ERROR, "账号长度应为 4-16 位");
        ThrowUtils.throwIf(!ACCOUNT_PATTERN.matcher(account).matches(),
                ErrorCode.PARAMS_ERROR, "账号仅支持字母、数字和下划线");
        ThrowUtils.throwIf(StrUtil.isBlank(password) || password.length() < 8 || password.length() > 32,
                ErrorCode.PARAMS_ERROR, "密码长度应为 8-32 位");
        ThrowUtils.throwIf(!password.equals(request.getCheckPassword()),
                ErrorCode.PARAMS_ERROR, "两次输入的密码不一致");
        ThrowUtils.throwIf(selectUserByAccount(account) != null,
                ErrorCode.OPERATION_ERROR, "账号已存在");

        String hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt());
        jdbcTemplate.update(
                "INSERT INTO user (userAccount, userPassword, userName, userRole, isDelete) VALUES (?, ?, ?, ?, 0)",
                account, hashedPassword, account, UserConstant.DEFAULT_ROLE);
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    @Override
    public LoginResponse login(UserLoginRequest request) {
        String account = StrUtil.trim(request.getUserAccount());
        String password = request.getUserPassword();
        ThrowUtils.throwIf(StrUtil.isBlank(account) || StrUtil.isBlank(password),
                ErrorCode.PARAMS_ERROR, "账号或密码不能为空");

        User user = selectUserByAccount(account);
        ThrowUtils.throwIf(user == null || !BCrypt.checkpw(password, user.getUserPassword()),
                ErrorCode.PARAMS_ERROR, "账号或密码错误");

        StpUtil.login(user.getId());
        log.info("用户登录成功：id={}, account={}", user.getId(), user.getUserAccount());
        return new LoginResponse(getUserVO(user), StpUtil.getTokenValue());
    }

    @Override
    public void logout() {
        StpUtil.logout();
    }

    @Override
    public UserVO getCurrentUser() {
        return getUserVO(getLoginUser());
    }

    @Override
    public User getUserById(Long id) {
        return selectUserById(id);
    }

    @Override
    public void updateUser(UserUpdateRequest request) {
        User loginUser = getLoginUser();
        boolean isAdmin = isAdmin(loginUser);
        ThrowUtils.throwIf(request.getId() != null && !request.getId().equals(loginUser.getId()) && !isAdmin,
                ErrorCode.NO_AUTH_ERROR, "无权修改其他用户资料");
        Long targetId = (request.getId() != null && isAdmin) ? request.getId() : loginUser.getId();
        ThrowUtils.throwIf(targetId == null, ErrorCode.PARAMS_ERROR, "缺少用户 id");

        // 动态拼接：仅更新传入的非空字段，避免覆盖其他字段
        StringBuilder sql = new StringBuilder("UPDATE user SET ");
        List<Object> args = new ArrayList<>();
        if (request.getUserName() != null) {
            sql.append("userName = ?, ");
            args.add(request.getUserName());
        }
        if (request.getUserAvatar() != null) {
            sql.append("userAvatar = ?, ");
            args.add(request.getUserAvatar());
        }
        if (request.getUserProfile() != null) {
            sql.append("userProfile = ?, ");
            args.add(request.getUserProfile());
        }
        if (args.isEmpty()) {
            return;
        }
        sql.setLength(sql.length() - 2);
        sql.append(" WHERE id = ? AND isDelete = 0");
        args.add(targetId);
        jdbcTemplate.update(sql.toString(), args.toArray());
    }

    @Override
    public void updatePassword(UpdatePasswordRequest request) {
        User user = getLoginUser();
        ThrowUtils.throwIf(StrUtil.isBlank(request.getOldPassword()) || StrUtil.isBlank(request.getNewPassword()),
                ErrorCode.PARAMS_ERROR, "密码不能为空");
        ThrowUtils.throwIf(!BCrypt.checkpw(request.getOldPassword(), user.getUserPassword()),
                ErrorCode.PARAMS_ERROR, "原密码错误");
        ThrowUtils.throwIf(request.getNewPassword().length() < 8 || request.getNewPassword().length() > 32,
                ErrorCode.PARAMS_ERROR, "新密码长度应为 8-32 位");
        ThrowUtils.throwIf(!request.getNewPassword().equals(request.getCheckPassword()),
                ErrorCode.PARAMS_ERROR, "两次输入的新密码不一致");

        String hashed = BCrypt.hashpw(request.getNewPassword(), BCrypt.gensalt());
        jdbcTemplate.update("UPDATE user SET userPassword = ? WHERE id = ?", hashed, user.getId());
        // 修改密码后强制重新登录
        StpUtil.logout();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void exchangeVipCode(VipExchangeRequest request) {
        User user = getLoginUser();
        String code = StrUtil.trim(request.getVipCode());
        ThrowUtils.throwIf(StrUtil.isBlank(code), ErrorCode.PARAMS_ERROR, "兑换码不能为空");

        VipCodeRow vipCodeRow = jdbcTemplate.query(
                        "SELECT id, code, duration_days, is_used FROM vip_code WHERE code = ? FOR UPDATE",
                        (rs, rowNum) -> new VipCodeRow(
                                rs.getLong("id"),
                                rs.getString("code"),
                                rs.getInt("duration_days"),
                                rs.getInt("is_used")),
                        code)
                .stream().findFirst().orElse(null);
        ThrowUtils.throwIf(vipCodeRow == null, ErrorCode.PARAMS_ERROR, "兑换码不存在");
        ThrowUtils.throwIf(vipCodeRow.isUsed() == 1, ErrorCode.OPERATION_ERROR, "兑换码已被使用");

        Date expireTime = new Date(System.currentTimeMillis()
                + vipCodeRow.durationDays() * 24L * 60L * 60L * 1000L);
        jdbcTemplate.update(
                "UPDATE user SET userRole = ?, vipExpireTime = ?, vipCode = ?, vipNumber = ? WHERE id = ?",
                UserConstant.VIP_ROLE, expireTime, code, vipCodeRow.id(), user.getId());
        jdbcTemplate.update(
                "UPDATE vip_code SET is_used = 1, used_by = ?, used_at = NOW() WHERE id = ?",
                user.getId(), vipCodeRow.id());
        log.info("用户 {} 成功兑换 VIP，时长 {} 天", user.getId(), vipCodeRow.durationDays());
    }

    @Override
    public UserVO addUser(UserAddRequest request) {
        String account = StrUtil.trim(request.getUserAccount());
        ThrowUtils.throwIf(StrUtil.isBlank(account) || account.length() < 4 || account.length() > 16,
                ErrorCode.PARAMS_ERROR, "账号长度应为 4-16 位");
        ThrowUtils.throwIf(selectUserByAccount(account) != null,
                ErrorCode.OPERATION_ERROR, "账号已存在");

        String role = StrUtil.isBlank(request.getUserRole()) ? UserConstant.DEFAULT_ROLE : request.getUserRole();
        validateRole(role);
        String hashed = BCrypt.hashpw(INITIAL_PASSWORD, BCrypt.gensalt());
        jdbcTemplate.update(
                "INSERT INTO user (userAccount, userPassword, userName, userAvatar, userProfile, userRole, isDelete) " +
                        "VALUES (?, ?, ?, ?, ?, ?, 0)",
                account, hashed, request.getUserName(), request.getUserAvatar(), request.getUserProfile(), role);
        Long id = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        return getUserVO(selectUserById(id));
    }

    @Override
    public void deleteUser(DeleteRequest request) {
        ThrowUtils.throwIf(request.getId() == null, ErrorCode.PARAMS_ERROR, "缺少用户 id");
        User loginUser = getLoginUser();
        ThrowUtils.throwIf(loginUser.getId().equals(request.getId()), ErrorCode.PARAMS_ERROR, "不能删除自己");

        jdbcTemplate.update("UPDATE user SET isDelete = 1 WHERE id = ?", request.getId());
        try {
            StpUtil.kickout(request.getId());
        } catch (Exception ignored) {
            // 用户可能不在线，忽略
        }
        log.info("管理员 {} 删除用户 id={}", loginUser.getId(), request.getId());
    }

    @Override
    public void updateUserRole(UpdateUserRoleRequest request) {
        ThrowUtils.throwIf(request.getId() == null, ErrorCode.PARAMS_ERROR, "缺少用户 id");
        ThrowUtils.throwIf(StrUtil.isBlank(request.getUserRole()), ErrorCode.PARAMS_ERROR, "缺少角色");
        validateRole(request.getUserRole());

        User target = selectUserById(request.getId());
        ThrowUtils.throwIf(target == null, ErrorCode.NOT_FOUND_ERROR, "用户不存在");
        User loginUser = getLoginUser();
        ThrowUtils.throwIf(loginUser.getId().equals(request.getId())
                        && !UserConstant.ADMIN_ROLE.equals(request.getUserRole()),
                ErrorCode.PARAMS_ERROR, "不能取消自己的管理员角色");

        jdbcTemplate.update("UPDATE user SET userRole = ? WHERE id = ?", request.getUserRole(), request.getId());
        try {
            StpUtil.kickout(request.getId());
        } catch (Exception ignored) {
        }
        log.info("管理员 {} 将用户 {} 的角色修改为 {}", loginUser.getId(), request.getId(), request.getUserRole());
    }

    @Override
    public PageResult<UserVO> listUsers(UserQueryRequest request) {
        int current = Math.max(request.getCurrent(), 1);
        int pageSize = Math.min(Math.max(request.getPageSize(), 1), 50);

        StringBuilder where = new StringBuilder(" WHERE isDelete = 0");
        List<Object> args = new ArrayList<>();
        if (request.getId() != null) {
            where.append(" AND id = ?");
            args.add(request.getId());
        }
        if (StrUtil.isNotBlank(request.getUserAccount())) {
            where.append(" AND userAccount LIKE ?");
            args.add("%" + request.getUserAccount() + "%");
        }
        if (StrUtil.isNotBlank(request.getUserName())) {
            where.append(" AND userName LIKE ?");
            args.add("%" + request.getUserName() + "%");
        }
        if (StrUtil.isNotBlank(request.getUserRole())) {
            where.append(" AND userRole = ?");
            args.add(request.getUserRole());
        }

        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user" + where, Long.class, args.toArray());

        String orderBy = "id DESC";
        if (StrUtil.isNotBlank(request.getSortField())
                && request.getSortField().matches("^[A-Za-z0-9_]+$")) {
            String order = "ascend".equals(request.getSortOrder()) ? "ASC" : "DESC";
            orderBy = request.getSortField() + " " + order;
        }
        args.add(pageSize);
        args.add((current - 1) * pageSize);

        List<UserVO> records = jdbcTemplate.query(
                "SELECT * FROM user" + where + " ORDER BY " + orderBy + " LIMIT ? OFFSET ?",
                (rs, rowNum) -> getUserVO(mapRow(rs)),
                args.toArray());
        return PageResult.of(records, total == null ? 0 : total, current, pageSize);
    }

    @Override
    public List<String> generateVipCodes(VipCodeGenerateRequest request) {
        int count = Math.min(Math.max(request.getCount(), 1), 100);
        int durationDays = Math.min(Math.max(request.getDurationDays(), 1), 3650);
        Long adminId = getLoginUser().getId();

        List<String> codes = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String code;
            int retry = 0;
            do {
                code = "VIP-" + RandomUtil.randomStringUpper(14);
                retry++;
            } while (countByCode(code) > 0 && retry < 5);
            jdbcTemplate.update(
                    "INSERT INTO vip_code (code, duration_days, created_by) VALUES (?, ?, ?)",
                    code, durationDays, adminId);
            codes.add(code);
        }
        log.info("管理员 {} 生成 {} 个 VIP 兑换码，时长 {} 天", adminId, count, durationDays);
        return codes;
    }

    @Override
    public String uploadAvatar(MultipartFile file) {
        User user = getLoginUser();
        ThrowUtils.throwIf(file == null || file.isEmpty(), ErrorCode.PARAMS_ERROR, "请选择图片文件");
        ThrowUtils.throwIf(file.getSize() > MAX_AVATAR_SIZE,
                ErrorCode.PARAMS_ERROR, "图片大小不能超过 5MB");

        String originalName = StrUtil.blankToDefault(file.getOriginalFilename(), "avatar.jpg");
        String ext = StrUtil.subAfter(originalName, ".", true).toLowerCase();
        ThrowUtils.throwIf(!ALLOWED_AVATAR_EXTS.contains(ext),
                ErrorCode.PARAMS_ERROR, "仅支持 jpg / jpeg / png / gif / webp 格式");

        String dir = FileConstant.FILE_SAVE_DIR + "/avatar";
        FileUtil.mkdir(dir);
        String fileName = IdUtil.fastSimpleUUID() + "." + ext;
        File dest = new File(dir, fileName);
        try {
            file.transferTo(dest);
        } catch (IOException e) {
            log.error("头像保存失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "头像保存失败");
        }

        String avatarUrl = "/file/avatar/" + fileName;
        jdbcTemplate.update("UPDATE user SET userAvatar = ? WHERE id = ?", avatarUrl, user.getId());
        log.info("用户 {} 上传新头像：{}", user.getId(), avatarUrl);
        return avatarUrl;
    }

    // ==================== 内部方法 ====================

    private User getLoginUser() {
        Object loginId = StpUtil.getLoginId();
        User user = selectUserById(Long.valueOf(loginId.toString()));
        ThrowUtils.throwIf(user == null, ErrorCode.NOT_LOGIN_ERROR, "登录用户不存在或已注销");
        return user;
    }

    private User selectUserById(Long id) {
        if (id == null) {
            return null;
        }
        List<User> users = jdbcTemplate.query(
                "SELECT * FROM user WHERE id = ? AND isDelete = 0",
                (rs, rowNum) -> mapRow(rs), id);
        return users.isEmpty() ? null : users.get(0);
    }

    private User selectUserByAccount(String account) {
        if (StrUtil.isBlank(account)) {
            return null;
        }
        List<User> users = jdbcTemplate.query(
                "SELECT * FROM user WHERE userAccount = ? AND isDelete = 0",
                (rs, rowNum) -> mapRow(rs), account);
        return users.isEmpty() ? null : users.get(0);
    }

    private int countByCode(String code) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM vip_code WHERE code = ?", Integer.class, code);
        return count == null ? 0 : count;
    }

    private boolean isAdmin(User user) {
        return user != null && UserConstant.ADMIN_ROLE.equals(user.getUserRole());
    }

    private void validateRole(String role) {
        ThrowUtils.throwIf(UserRoleEnum.getEnumByValue(role) == null,
                ErrorCode.PARAMS_ERROR, "角色不合法，仅支持 user / vip / admin");
    }

    private UserVO getUserVO(User user) {
        if (user == null) {
            return null;
        }
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUserAccount(user.getUserAccount());
        vo.setUserName(user.getUserName());
        vo.setUserAvatar(user.getUserAvatar());
        vo.setUserProfile(user.getUserProfile());
        vo.setUserRole(user.getUserRole());
        vo.setVipExpireTime(user.getVipExpireTime());
        vo.setVipCode(user.getVipCode());
        vo.setVipNumber(user.getVipNumber());
        vo.setCreateTime(user.getCreateTime());
        return vo;
    }

    private User mapRow(ResultSet rs) throws SQLException {
        User user = new User();
        user.setId(rs.getLong("id"));
        user.setUserAccount(rs.getString("userAccount"));
        user.setUserPassword(rs.getString("userPassword"));
        user.setUserName(rs.getString("userName"));
        user.setUserAvatar(rs.getString("userAvatar"));
        user.setUserProfile(rs.getString("userProfile"));
        user.setUserRole(rs.getString("userRole"));
        user.setVipExpireTime(rs.getTimestamp("vipExpireTime"));
        user.setVipCode(rs.getString("vipCode"));
        user.setVipNumber(rs.getObject("vipNumber") == null ? null : rs.getLong("vipNumber"));
        user.setCreateTime(rs.getTimestamp("createTime"));
        user.setUpdateTime(rs.getTimestamp("updateTime"));
        user.setIsDelete(rs.getInt("isDelete"));
        return user;
    }

    /**
     * vip_code 表行记录（内部载体）
     */
    private record VipCodeRow(Long id, String code, int durationDays, int isUsed) {
    }
}
