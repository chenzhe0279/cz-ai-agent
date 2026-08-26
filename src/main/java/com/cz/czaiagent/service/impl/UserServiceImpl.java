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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
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

    private static final int EMAIL_CODE_TTL_MINUTES = 10;

    private static final long EMAIL_CODE_COOLDOWN_SECONDS = 60;

    private static final String EMAIL_PURPOSE_BIND = "bind";

    private static final String EMAIL_PURPOSE_REGISTER = "register";

    private static final String EMAIL_PURPOSE_LOGIN = "login";

    private static final String EMAIL_PURPOSE_RESET = "reset";

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+$");

    private final JdbcTemplate jdbcTemplate;

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String mailUsername;

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
        String email = validateEmail(request.getEmail());
        verifyCode(email, EMAIL_PURPOSE_REGISTER, request.getVerifyCode());
        ThrowUtils.throwIf(selectUserByEmail(email) != null,
                ErrorCode.OPERATION_ERROR, "该邮箱已被其他账号绑定");

        String hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt());
        jdbcTemplate.update(
                "INSERT INTO user (userAccount, userPassword, userName, userRole, email, isDelete) VALUES (?, ?, ?, ?, ?, 0)",
                account, hashedPassword, account, UserConstant.DEFAULT_ROLE, email);
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    @Override
    public void sendRegisterCode(EmailSendCodeRequest request) {
        String email = validateEmail(request.getEmail());
        ThrowUtils.throwIf(selectUserByEmail(email) != null,
                ErrorCode.OPERATION_ERROR, "该邮箱已被绑定，请直接登录或使用找回密码");
        sendCode(email, EMAIL_PURPOSE_REGISTER);
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
    public void sendLoginCode(EmailSendCodeRequest request) {
        String email = validateEmail(request.getEmail());
        ThrowUtils.throwIf(selectUserByEmail(email) == null,
                ErrorCode.NOT_FOUND_ERROR, "该邮箱未绑定任何账号，请先注册");
        sendCode(email, EMAIL_PURPOSE_LOGIN);
    }

    @Override
    public LoginResponse loginByEmailCode(EmailLoginRequest request) {
        String email = validateEmail(request.getEmail());
        verifyCode(email, EMAIL_PURPOSE_LOGIN, request.getVerifyCode());
        User user = selectUserByEmail(email);
        ThrowUtils.throwIf(user == null, ErrorCode.NOT_FOUND_ERROR, "该邮箱未绑定任何账号，请先注册");

        StpUtil.login(user.getId());
        log.info("用户通过邮箱验证码登录成功：id={}, account={}", user.getId(), user.getUserAccount());
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

    @Override // 标记该方法是 UserService 接口 sendEmailCode 的实现
    public void sendEmailCode(EmailSendCodeRequest request) { // 发送绑定邮箱验证码：要求用户已登录
        String email = validateEmail(request.getEmail()); // 校验邮箱格式并返回去除首尾空白后的邮箱地址
        getLoginUser(); // 获取当前登录用户，未登录或用户不存在时会抛出异常，确保绑定邮箱场景必须登录
        sendCode(email, EMAIL_PURPOSE_BIND); // 发送用途为 bind（绑定邮箱）的邮箱验证码
    } // sendEmailCode 方法结束

    @Override // 标记该方法是 UserService 接口 bindEmail 的实现
    public void bindEmail(EmailBindRequest request) { // 绑定邮箱：校验验证码后将邮箱绑定到当前登录用户
        User user = getLoginUser(); // 获取当前登录用户，未登录时抛出异常
        String email = validateEmail(request.getEmail()); // 校验并规范化待绑定的邮箱地址
        verifyCode(email, EMAIL_PURPOSE_BIND, request.getVerifyCode()); // 校验“绑定邮箱”场景的邮箱验证码，验证通过后会作废验证码

        // 查询当前邮箱是否已经被其他未删除用户绑定，便于避免重复绑定
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user WHERE email = ? AND id <> ? AND isDelete = 0", // SQL：统计除当前用户外绑定该邮箱的未删除用户数
                Integer.class, email, user.getId()); // 查询参数：返回类型为 Integer，条件为 email 和当前用户 id
        ThrowUtils.throwIf(count != null && count > 0, // 判断是否存在其他用户已绑定该邮箱
                ErrorCode.OPERATION_ERROR, "该邮箱已被其他账号绑定"); // 若已被绑定，则抛出业务操作异常
        jdbcTemplate.update("UPDATE user SET email = ? WHERE id = ?", email, user.getId()); // 执行 SQL：将当前登录用户的邮箱更新为新绑定邮箱
        log.info("用户 {} 绑定邮箱 {}", user.getId(), email); // 记录用户绑定邮箱的成功日志
    } // bindEmail 方法结束

    @Override // 标记该方法是 UserService 接口 sendPasswordResetCode 的实现
    public void sendPasswordResetCode(EmailSendCodeRequest request) { // 发送找回密码验证码：要求邮箱已绑定账号
        String email = validateEmail(request.getEmail()); // 校验邮箱格式并返回去除首尾空白后的邮箱地址
        ThrowUtils.throwIf(selectUserByEmail(email) == null, // 判断该邮箱是否绑定了某个未删除账号
                ErrorCode.NOT_FOUND_ERROR, "该邮箱未绑定任何账号"); // 如果邮箱未绑定账号，则抛出“未找到”错误
        sendCode(email, EMAIL_PURPOSE_RESET); // 发送用途为 reset（找回密码）的邮箱验证码
    } // sendPasswordResetCode 方法结束

    @Override // 标记该方法是 UserService 接口 resetPassword 的实现
    public void resetPassword(PasswordResetRequest request) { // 找回密码：校验邮箱验证码后重置该邮箱绑定账号的密码
        String email = validateEmail(request.getEmail()); // 校验邮箱格式并返回去除首尾空白后的邮箱地址
        verifyCode(email, EMAIL_PURPOSE_RESET, request.getVerifyCode()); // 校验“找回密码”场景的邮箱验证码，验证通过后会作废验证码
        ThrowUtils.throwIf(StrUtil.isBlank(request.getNewPassword()) // 判断新密码是否为空
                        || request.getNewPassword().length() < 8 || request.getNewPassword().length() > 32, // 判断新密码长度是否小于 8 或大于 32
                ErrorCode.PARAMS_ERROR, "新密码长度应为 8-32 位"); // 新密码为空或长度不合法时，抛出参数错误
        ThrowUtils.throwIf(!request.getNewPassword().equals(request.getCheckPassword()), // 判断新密码与确认密码是否一致
                ErrorCode.PARAMS_ERROR, "两次输入的新密码不一致"); // 新密码两次输入不一致时，抛出参数错误

        User user = selectUserByEmail(email); // 根据邮箱查询已绑定该邮箱的未删除用户
        ThrowUtils.throwIf(user == null, ErrorCode.NOT_FOUND_ERROR, "该邮箱未绑定任何账号"); // 若查询不到用户，说明邮箱未绑定账号，抛出未找到错误
        String hashed = BCrypt.hashpw(request.getNewPassword(), BCrypt.gensalt()); // 使用 BCrypt 对新密码进行加盐哈希，生成密文
        jdbcTemplate.update("UPDATE user SET userPassword = ? WHERE id = ?", hashed, user.getId()); // 执行 SQL：将用户密码更新为新密文
        try { // 尝试强制下线该用户，使重置后的密码强制重新登录
            StpUtil.kickout(user.getId()); // 调用 Sa-Token 将该用户强制踢下线
        } catch (Exception ignored) { // 捕获下线时可能出现的异常（例如用户本来就不在线）
        } // 异常处理结束，忽略下线失败
        log.info("用户 {} 通过邮箱验证码重置密码", user.getId()); // 记录用户通过邮箱验证码重置密码的成功日志
    } // resetPassword 方法结束

    // ==================== 邮箱验证码内部方法 ====================

    /**
     * 校验邮箱格式并返回去除首尾空白后的邮箱地址。
     * 若邮箱为空或格式不匹配，则抛出参数错误异常。
     *
     * @param email 待校验的邮箱字符串
     * @return 去除首尾空白后的邮箱地址
     */
    private String validateEmail(String email) {
        // 判断邮箱是否为空（去除首尾空白后）或者不符合正则表达式格式，若是则抛出参数错误异常
        ThrowUtils.throwIf(StrUtil.isBlank(email) || !EMAIL_PATTERN.matcher(email.trim()).matches(),
                ErrorCode.PARAMS_ERROR, "邮箱格式不正确");
        // 返回去除首尾空白后的邮箱地址
        return email.trim();
    }

    /**
     * 向指定邮箱发送验证码邮件。
     * 包含发送频率限制（同一邮箱同一用途60秒内仅能发送一次）、邮件服务可用性检查、
     * 生成随机验证码、记录验证码到数据库、组装邮件并发送等步骤。
     *
     * @param email   目标邮箱地址（已经过校验）
     * @param purpose 验证码用途（bind/register/login/reset）
     */
    private void sendCode(String email, String purpose) {
        // 冷却：同一邮箱同一用途 60 秒内只能发送一次
        // 计算60秒前的时间点，用于查询最近是否有发送记录
        Date earliest = new Date(System.currentTimeMillis() - EMAIL_CODE_COOLDOWN_SECONDS * 1000L);
        // 查询该邮箱和用途下，创建时间晚于 earliest 的验证码记录数
        Integer recent = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM email_verify_code WHERE email = ? AND purpose = ? AND create_time > ?",
                Integer.class, email, purpose, earliest);
        // 如果最近60秒内已有发送记录，则抛出操作错误异常，提示频率过高
        ThrowUtils.throwIf(recent != null && recent > 0,
                ErrorCode.OPERATION_ERROR, "发送过于频繁，请稍后再试");
        // 检查邮件发送器是否已注入（若未配置邮件服务，mailSender 为 null）
        ThrowUtils.throwIf(mailSender == null,
                ErrorCode.OPERATION_ERROR, "邮件服务未配置，请先配置 spring.mail");

        // 生成6位随机数字验证码
        String code = RandomUtil.randomNumbers(6);
        // 计算验证码过期时间（当前时间 + 配置的有效分钟数）
        Date expireTime = new Date(System.currentTimeMillis() + EMAIL_CODE_TTL_MINUTES * 60L * 1000L);
        // 将验证码信息插入数据库，记录邮箱、验证码、用途和过期时间
        jdbcTemplate.update(
                "INSERT INTO email_verify_code (email, code, purpose, expire_time) VALUES (?, ?, ?, ?)",
                email, code, purpose, expireTime);
        try {
            // 创建简单邮件消息对象
            SimpleMailMessage message = new SimpleMailMessage();
            // 如果配置了发件人地址（mailUsername 非空白），则设置发件人
            if (StrUtil.isNotBlank(mailUsername)) {
                message.setFrom(mailUsername);
            }
            // 设置收件人邮箱
            message.setTo(email);
            // 根据验证码用途设置不同的邮件主题
            message.setSubject(EMAIL_PURPOSE_BIND.equals(purpose)
                    ? "【CZ AI】邮箱绑定验证码"          // 绑定邮箱
                    : EMAIL_PURPOSE_REGISTER.equals(purpose)
                            ? "【CZ AI】注册验证码"      // 注册
                            : EMAIL_PURPOSE_LOGIN.equals(purpose)
                                    ? "【CZ AI】登录验证码"  // 登录
                                    : "【CZ AI】找回密码验证码"); // 找回密码
            // 设置邮件正文内容，包含验证码和有效期提示
            message.setText("您的验证码是：" + code + "，" + EMAIL_CODE_TTL_MINUTES
                    + " 分钟内有效。若非本人操作，请忽略本邮件。");
            // 发送邮件
            mailSender.send(message);
        } catch (Exception e) {
            // 记录发送失败日志
            log.error("发送验证码邮件失败", e);
            // 抛出业务异常，提示发送失败
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "验证码邮件发送失败，请检查邮箱配置");
        }
    }

    /**
     * 校验邮箱验证码的正确性、有效期，并在验证通过后作废该邮箱及用途下的所有未使用验证码（一次性使用）。
     *
     * @param email   邮箱地址
     * @param purpose 验证码用途
     * @param code    用户输入的验证码
     */
    private void verifyCode(String email, String purpose, String code) {
        // 校验验证码不能为空，若为空则抛出参数错误异常
        ThrowUtils.throwIf(StrUtil.isBlank(code), ErrorCode.PARAMS_ERROR, "验证码不能为空");
        // 查询该邮箱和用途下最新的一条未使用验证码记录（按 id 降序取第一条）
        EmailCodeRow row = jdbcTemplate.query(
                        "SELECT id, code, expire_time, is_used FROM email_verify_code " +
                                "WHERE email = ? AND purpose = ? AND is_used = 0 ORDER BY id DESC LIMIT 1",
                        (rs, rowNum) -> new EmailCodeRow(
                                rs.getLong("id"),           // 验证码记录ID
                                rs.getString("code"),        // 验证码内容
                                rs.getTimestamp("expire_time"), // 过期时间
                                rs.getInt("is_used")),      // 是否已使用（0未使用，1已使用）
                        email, purpose)
                .stream().findFirst().orElse(null); // 若没有记录则返回 null
        // 如果查询不到记录，或者记录中的验证码与用户输入的验证码（去除首尾空白后）不一致，则抛出验证码错误异常
        ThrowUtils.throwIf(row == null || !row.code().equals(code.trim()),
                ErrorCode.PARAMS_ERROR, "验证码错误");
        // 如果验证码的过期时间为空，或者已过期（早于当前时间），则抛出验证码已过期异常
        ThrowUtils.throwIf(row.expireTime() == null || row.expireTime().before(new Date()),
                ErrorCode.PARAMS_ERROR, "验证码已过期");
        // 一次性使用：作废该邮箱 + 用途下的所有未使用验证码（确保验证码只能使用一次）
        jdbcTemplate.update(
                "UPDATE email_verify_code SET is_used = 1 WHERE email = ? AND purpose = ?",
                email, purpose);
    }

    /**
     * 根据邮箱查询未删除的用户。
     * 若邮箱为空或未查询到用户，则返回 null；否则返回第一条匹配的用户。
     *
     * @param email 邮箱地址
     * @return 对应的用户实体，找不到则返回 null
     */
    private User selectUserByEmail(String email) {
        // 如果邮箱为空（去除首尾空白后），直接返回 null，避免无效查询
        if (StrUtil.isBlank(email)) {
            return null;
        }
        // 查询 user 表中邮箱匹配且未逻辑删除（isDelete = 0）的所有用户
        List<User> users = jdbcTemplate.query(
                "SELECT * FROM user WHERE email = ? AND isDelete = 0",
                (rs, rowNum) -> mapRow(rs), email); // 将结果集映射为 User 对象
        // 如果结果列表为空，返回 null；否则返回第一条记录
        return users.isEmpty() ? null : users.get(0);
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
        vo.setEmail(user.getEmail());
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
        user.setEmail(rs.getString("email"));
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

    /**
     * email_verify_code 表行记录（内部载体）
     */
    private record EmailCodeRow(Long id, String code, Date expireTime, int isUsed) {
    }
}
