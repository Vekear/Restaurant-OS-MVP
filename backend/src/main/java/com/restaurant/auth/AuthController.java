package com.restaurant.auth;

import com.restaurant.auth.dto.LoginRequest;
import com.restaurant.auth.dto.RegisterRequest;
import com.restaurant.auth.dto.UpdateProfileRequest;
import com.restaurant.auth.dto.WxLoginRequest;
import com.restaurant.auth.vo.AuthTokenView;
import com.restaurant.auth.vo.LogoutView;
import com.restaurant.auth.vo.UserView;
import com.restaurant.common.api.Result;
import com.restaurant.common.config.AppProperties;
import com.restaurant.common.config.OpenApiConfig;
import com.restaurant.common.constant.ApiConstants;
import com.restaurant.common.util.ClientIpUtils;
import com.restaurant.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口（{@code docs/02-api.md §4}）。
 *
 * <p>双入口：{@code /auth/login}（Web/管理后台账号密码）与 {@code /auth/wechat/login}
 * （小程序 code）签发同一套 JWT；登录失败统一 40101，禁用账号 40304，限流 429/42901。
 */
@RestController
@RequestMapping(ApiConstants.API_V1 + "/auth")
@RequiredArgsConstructor
@Tag(name = "认证", description = "账号密码登录、顾客注册、微信小程序登录、登出与个人资料")
public class AuthController {

    private final AuthService authService;

    private final AppProperties appProperties;

    @Operation(summary = "账号密码登录（Web / 管理后台）",
            description = "ADMIN / MERCHANT / CUSTOMER 共用；用户名不存在或密码错误统一 40101，账号禁用 40304，"
                    + "同一 IP 每分钟最多 5 次（429/42901）")
    @PostMapping("/login")
    public Result<AuthTokenView> login(@Valid @RequestBody LoginRequest request,
                                       HttpServletRequest httpRequest) {
        return Result.success(authService.login(request, clientIp(httpRequest),
                userAgent(httpRequest)));
    }

    @Operation(summary = "顾客注册", description = "注册成功直接返回 token；用户名/手机号重复返回 40914")
    @PostMapping("/register")
    public Result<AuthTokenView> register(@Valid @RequestBody RegisterRequest request) {
        return Result.success(authService.register(request));
    }

    @Operation(summary = "微信小程序登录",
            description = "用 wx.login 的 code 换取 openid；首次登录自动建号，同一 openid 幂等；"
                    + "mock 模式（默认）不访问微信接口，session_key 永不下发")
    @PostMapping("/wechat/login")
    public Result<AuthTokenView> wechatLogin(@Valid @RequestBody WxLoginRequest request,
                                             HttpServletRequest httpRequest) {
        return Result.success(authService.wechatLogin(request, clientIp(httpRequest),
                userAgent(httpRequest)));
    }

    @Operation(summary = "当前登录用户信息")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
    @GetMapping("/me")
    public Result<UserView> me(@AuthenticationPrincipal UserPrincipal principal) {
        return Result.success(authService.me(principal));
    }

    @Operation(summary = "登出", description = "将 token 的 jti 写入 Redis 黑名单直至过期；重复登出幂等返回 loggedOut=true")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
    @PostMapping("/logout")
    public Result<LogoutView> logout(@AuthenticationPrincipal UserPrincipal principal,
                                     HttpServletRequest httpRequest) {
        authService.logout(principal, clientIp(httpRequest), userAgent(httpRequest));
        return Result.success(LogoutView.success());
    }

    @Operation(summary = "修改个人资料", description = "nickname 1-20 位、phone 为空串表示清空、avatarUrl 需为 /files/** 或 https://")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
    @PutMapping("/profile")
    public Result<UserView> updateProfile(@AuthenticationPrincipal UserPrincipal principal,
                                          @Valid @RequestBody UpdateProfileRequest request,
                                          HttpServletRequest httpRequest) {
        return Result.success(authService.updateProfile(principal, request,
                clientIp(httpRequest), userAgent(httpRequest)));
    }

    private String clientIp(HttpServletRequest request) {
        return ClientIpUtils.resolve(request, appProperties.getRateLimit().getTrustedProxies());
    }

    private String userAgent(HttpServletRequest request) {
        return request.getHeader(HttpHeaders.USER_AGENT);
    }
}
