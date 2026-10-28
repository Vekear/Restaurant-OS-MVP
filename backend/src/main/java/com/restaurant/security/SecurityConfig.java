package com.restaurant.security;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

/**
 * Spring Security 配置（T06：JWT 双入口 + RBAC）。
 *
 * <p>入口：Web/管理后台走 {@code POST /auth/login}；小程序走 {@code POST /auth/wechat/login}；
 * 两条入口签发同一套 JWT，由 {@link JwtAuthenticationFilter} 统一校验。
 * 白名单严格对齐 {@code docs/02-api.md §1}；{@code /api/v1/admin/**} 仅 ADMIN、
 * {@code /api/v1/merchant/**} 仅 MERCHANT，其余 {@code /api/v1/**} 需登录。
 * Actuator 仅 {@code /actuator/health} 匿名公开，其余 {@code /actuator/**} 需 ADMIN（L-03）。
 * 未认证/无权限分别由 {@link RestAuthenticationEntryPoint}、{@link RestAccessDeniedHandler}
 * 输出统一 JSON（40101/40102/40103、40301）。
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    private final RateLimitFilter rateLimitFilter;

    private final RestAuthenticationEntryPoint restAuthenticationEntryPoint;

    private final RestAccessDeniedHandler restAccessDeniedHandler;

    private final CorsConfigurationSource corsConfigurationSource;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(restAuthenticationEntryPoint)
                        .accessDeniedHandler(restAccessDeniedHandler))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // 双入口登录 + 注册（未登录可访问）
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/auth/login",
                                "/api/v1/auth/register",
                                "/api/v1/auth/wechat/login").permitAll()
                        // 公开只读接口：菜单/商家/公共配置（文件策略与上传必须登录）
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/menu/**",
                                "/api/v1/merchants/**",
                                "/api/v1/public/**").permitAll()
                        // 健康检查保持匿名（容器/K8s 探针）；其余 actuator 端点仅 ADMIN，
                        // 避免 8080 直连绕过 Nginx 的 /actuator 白名单
                        .requestMatchers(
                                "/actuator/health",
                                "/actuator/health/**").permitAll()
                        .requestMatchers("/actuator/**").hasRole("ADMIN")
                        .requestMatchers(
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/error",
                                "/ws/**").permitAll()
                        // 模拟支付网关回调：内部调用，由 Nginx 限制来源
                        .requestMatchers(HttpMethod.POST, "/api/v1/payments/mock/callback").permitAll()
                        // RBAC
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/merchant/**").hasRole("MERCHANT")
                        .requestMatchers("/api/v1/**").authenticated()
                        .anyRequest().permitAll())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(rateLimitFilter, JwtAuthenticationFilter.class);
        return http.build();
    }

    /**
     * 关闭 Servlet 容器对 JWT 过滤器的自动注册：该过滤器只应存在于 Spring Security 过滤链中，
     * 否则会被执行两次。
     */
    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtFilterRegistration(JwtAuthenticationFilter filter) {
        FilterRegistrationBean<JwtAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    /** 同上：限流过滤器只允许在 Security 链中执行一次，避免重复计数。 */
    @Bean
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration(RateLimitFilter filter) {
        FilterRegistrationBean<RateLimitFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    /** 密码哈希统一 BCrypt(cost 10)（{@code docs/01-architecture.md §16.8}）。 */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }
}
