package com.restaurant.auth;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.restaurant.audit.AuditService;
import com.restaurant.auth.dto.LoginRequest;
import com.restaurant.auth.dto.RegisterRequest;
import com.restaurant.auth.dto.UpdateProfileRequest;
import com.restaurant.auth.dto.WxLoginRequest;
import com.restaurant.auth.vo.AuthTokenView;
import com.restaurant.auth.vo.UserBrief;
import com.restaurant.auth.vo.UserView;
import com.restaurant.common.api.ErrorCode;
import com.restaurant.common.config.AppProperties;
import com.restaurant.common.enums.Role;
import com.restaurant.common.enums.UserStatus;
import com.restaurant.common.exception.BizException;
import com.restaurant.common.util.CacheKeys;
import com.restaurant.integration.wechat.WxSession;
import com.restaurant.integration.wechat.WxSessionClient;
import com.restaurant.merchant.Merchant;
import com.restaurant.merchant.mapper.MerchantMapper;
import com.restaurant.security.JwtTokenProvider;
import com.restaurant.security.RateLimitService;
import com.restaurant.security.UserPrincipal;
import com.restaurant.user.User;
import com.restaurant.user.WxUser;
import com.restaurant.user.mapper.UserMapper;
import com.restaurant.user.mapper.WxUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.UUID;

/**
 * 认证领域服务：账号密码登录、顾客注册、微信小程序登录、登出、个人资料。
 *
 * <p>安全约定：用户名不存在与密码错误统一抛 40101（防枚举）；账号禁用抛 40304；
 * 登录成功/失败都写 audit_log；响应永不含 password / session_key。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final Duration LOGIN_RATE_WINDOW = Duration.ofMinutes(1);

    private final UserMapper userMapper;

    private final WxUserMapper wxUserMapper;

    private final MerchantMapper merchantMapper;

    private final PasswordEncoder passwordEncoder;

    private final JwtTokenProvider jwtTokenProvider;

    private final AuditService auditService;

    private final RateLimitService rateLimitService;

    private final WxSessionClient wxSessionClient;

    private final AppProperties appProperties;

    private final StringRedisTemplate stringRedisTemplate;

    /** 账号密码登录（Web / 管理后台）。 */
    public AuthTokenView login(LoginRequest request, String ip, String userAgent) {
        rateLimitService.checkOrThrow(CacheKeys.rateLoginKey(ip),
                appProperties.getRateLimit().getLogin(), LOGIN_RATE_WINDOW);
        User user = userMapper.selectOne(Wrappers.<User>lambdaQuery()
                .eq(User::getUsername, request.getUsername()));
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            auditService.loginFail(new AuditService.LoginAttempt(
                    user == null ? null : user.getId(),
                    request.getUsername(),
                    user == null ? null : user.getRole(),
                    "用户名或密码错误"), ip, userAgent);
            throw new BizException(ErrorCode.UNAUTHENTICATED);
        }
        if (!UserStatus.isEnabled(user.getStatus())) {
            auditService.loginFail(new AuditService.LoginAttempt(
                    user.getId(), user.getUsername(), user.getRole(), "账号已禁用"), ip, userAgent);
            throw new BizException(ErrorCode.ACCOUNT_DISABLED);
        }
        userMapper.update(null, Wrappers.<User>lambdaUpdate()
                .set(User::getLastLoginAt, LocalDateTime.now())
                .eq(User::getId, user.getId()));
        user.setLastLoginAt(LocalDateTime.now());
        auditService.loginSuccess(user, ip, userAgent);
        return issueToken(user);
    }

    /** 顾客注册（成功即登录）。 */
    @Transactional
    public AuthTokenView register(RegisterRequest request) {
        String username = request.getUsername();
        if (userMapper.exists(Wrappers.<User>lambdaQuery().eq(User::getUsername, username))) {
            throw new BizException(ErrorCode.UNIQUE_CONFLICT, "用户名已存在");
        }
        String phone = normalizePhone(request.getPhone());
        if (phone != null && userMapper.exists(Wrappers.<User>lambdaQuery().eq(User::getPhone, phone))) {
            throw new BizException(ErrorCode.UNIQUE_CONFLICT, "手机号已被注册");
        }
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setNickname(StringUtils.hasText(request.getNickname()) ? request.getNickname().trim() : username);
        user.setPhone(phone);
        user.setRole(Role.CUSTOMER.name());
        user.setStatus(UserStatus.ENABLED.name());
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException exception) {
            log.warn("注册唯一键冲突 username={}", username);
            throw new BizException(ErrorCode.UNIQUE_CONFLICT);
        }
        return issueToken(user);
    }

    /**
     * 微信小程序 code 登录：openid 首次出现时自动创建 users + wx_user，之后复用绑定关系。
     * 同一 code 重复调用返回同一用户（幂等）。
     */
    @Transactional
    public AuthTokenView wechatLogin(WxLoginRequest request, String ip, String userAgent) {
        WxSession session;
        try {
            session = wxSessionClient.code2Session(request.getCode());
        } catch (BizException exception) {
            auditService.loginFail(new AuditService.LoginAttempt(
                    null, "wechat", Role.CUSTOMER.name(), "微信登录凭证无效"), ip, userAgent);
            throw exception;
        }
        WxUser wxUser = wxUserMapper.selectOne(Wrappers.<WxUser>lambdaQuery()
                .eq(WxUser::getOpenid, session.openid()));
        User user = wxUser == null || wxUser.getUserId() == null
                ? null
                : userMapper.selectById(wxUser.getUserId());
        if (user == null) {
            user = createWxAccount(session);
            if (wxUser == null) {
                WxUser record = new WxUser();
                record.setOpenid(session.openid());
                record.setUnionid(session.unionid());
                record.setNickname(user.getNickname());
                record.setUserId(user.getId());
                try {
                    wxUserMapper.insert(record);
                } catch (DuplicateKeyException duplicate) {
                    // 并发首登：回滚本次新建用户，复用已存在的 wx_user
                    userMapper.deleteById(user.getId());
                    user = reuseExistingWxBinding(session);
                }
            } else {
                wxUser.setUserId(user.getId());
                wxUser.setUnionid(session.unionid());
                wxUserMapper.updateById(wxUser);
            }
        }
        if (user == null) {
            throw new BizException(ErrorCode.UNAUTHENTICATED, "微信登录失败，请重试");
        }
        if (!UserStatus.isEnabled(user.getStatus())) {
            auditService.loginFail(new AuditService.LoginAttempt(
                    user.getId(), user.getUsername(), user.getRole(), "账号已禁用"), ip, userAgent);
            throw new BizException(ErrorCode.ACCOUNT_DISABLED);
        }
        auditService.loginSuccess(user, ip, userAgent);
        return issueToken(user);
    }

    /** 当前登录用户信息（完整手机号）。 */
    @Transactional(readOnly = true)
    public UserView me(UserPrincipal principal) {
        User user = requireUser(principal);
        return UserView.from(user, resolveMerchantId(user));
    }

    /** 登出：把 jti 写入 Redis 黑名单直到 token 自然过期；重复登出幂等。 */
    public void logout(UserPrincipal principal, String ip, String userAgent) {
        String jti = principal.getJti();
        if (StringUtils.hasText(jti) && principal.getExpiresAt() != null) {
            long ttlSeconds = Duration.between(Instant.now(), principal.getExpiresAt()).toSeconds();
            if (ttlSeconds > 0) {
                try {
                    stringRedisTemplate.opsForValue().set(CacheKeys.jwtBlacklistKey(jti),
                            "1", Duration.ofSeconds(ttlSeconds));
                } catch (RuntimeException exception) {
                    log.warn("登出黑名单写入 Redis 失败 jti={}: {}", jti, exception.getMessage());
                }
            }
        }
        User user = userMapper.selectById(principal.getId());
        if (user != null) {
            auditService.logout(user, ip, userAgent);
        }
    }

    /** 修改个人资料（null 字段不修改，phone 空串表示清空）。 */
    @Transactional
    public UserView updateProfile(UserPrincipal principal, UpdateProfileRequest request,
                                  String ip, String userAgent) {
        User user = requireUser(principal);
        if (request.getNickname() != null) {
            user.setNickname(request.getNickname().trim());
        }
        if (request.getPhone() != null) {
            String phone = normalizePhone(request.getPhone());
            if (phone != null && !phone.equals(user.getPhone())
                    && userMapper.exists(Wrappers.<User>lambdaQuery().eq(User::getPhone, phone))) {
                throw new BizException(ErrorCode.UNIQUE_CONFLICT, "手机号已被占用");
            }
            user.setPhone(phone);
        }
        if (request.getAvatarUrl() != null) {
            user.setAvatarUrl(request.getAvatarUrl());
        }
        try {
            // 使用 UpdateWrapper 以支持 phone 显式置空（MyBatis-Plus 默认忽略 null 字段）
            userMapper.update(null, Wrappers.<User>lambdaUpdate()
                    .set(User::getNickname, user.getNickname())
                    .set(User::getPhone, user.getPhone())
                    .set(User::getAvatarUrl, user.getAvatarUrl())
                    .eq(User::getId, user.getId()));
        } catch (DuplicateKeyException exception) {
            throw new BizException(ErrorCode.UNIQUE_CONFLICT, "手机号已被占用");
        }
        auditService.profileUpdated(user, "更新个人资料", ip, userAgent);
        return UserView.from(user, resolveMerchantId(user));
    }

    private User requireUser(UserPrincipal principal) {
        User user = principal == null ? null : userMapper.selectById(principal.getId());
        if (user == null) {
            throw new BizException(ErrorCode.UNAUTHENTICATED);
        }
        return user;
    }

    private User reuseExistingWxBinding(WxSession session) {
        WxUser existing = wxUserMapper.selectOne(Wrappers.<WxUser>lambdaQuery()
                .eq(WxUser::getOpenid, session.openid()));
        if (existing != null && existing.getUserId() != null) {
            User linked = userMapper.selectById(existing.getUserId());
            if (linked != null) {
                return linked;
            }
        }
        // 极端并发下仍未绑定：重新创建并回填绑定
        User user = createWxAccount(session);
        if (existing != null) {
            existing.setUserId(user.getId());
            wxUserMapper.updateById(existing);
        }
        return user;
    }

    private User createWxAccount(WxSession session) {
        User user = new User();
        user.setUsername("wx_" + sha256Hex(session.openid()).substring(0, 16));
        user.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
        user.setNickname("微信用户" + lastFour(session.openid()));
        user.setRole(Role.CUSTOMER.name());
        user.setStatus(UserStatus.ENABLED.name());
        userMapper.insert(user);
        return user;
    }

    private AuthTokenView issueToken(User user) {
        Long merchantId = resolveMerchantId(user);
        JwtTokenProvider.IssuedToken issued = jwtTokenProvider.issue(
                user.getId(), user.getUsername(), user.getRole(), merchantId);
        return AuthTokenView.of(issued.token(), issued.expiresIn(), UserBrief.from(user, merchantId));
    }

    private Long resolveMerchantId(User user) {
        if (!Role.MERCHANT.name().equals(user.getRole())) {
            return null;
        }
        Merchant merchant = merchantMapper.selectOne(Wrappers.<Merchant>lambdaQuery()
                .eq(Merchant::getUserId, user.getId())
                .last("LIMIT 1"));
        return merchant == null ? null : merchant.getId();
    }

    private String normalizePhone(String phone) {
        if (!StringUtils.hasText(phone)) {
            return null;
        }
        return phone.trim();
    }

    private String lastFour(String value) {
        if (value.length() <= 4) {
            return value;
        }
        return value.substring(value.length() - 4);
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JVM 不支持 SHA-256", exception);
        }
    }
}
