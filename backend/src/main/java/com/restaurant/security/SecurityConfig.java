package com.restaurant.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfigurationSource;

/**
 * Spring Security 骨架（T05 占位实现）。
 *
 * <p><b>T06 将替换本类</b>：接入 JWT 双入口（Web Bearer / 小程序 code2session）、
 * 角色 RBAC、401/403 JSON 响应、Redis 限流与登出黑名单。
 * 当前仅关闭表单登录/CSRF、保持无状态并放行全部请求，避免骨架阶段默认 Basic 认证挡住
 * Swagger、Actuator 与后续接口联调。
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   CorsConfigurationSource corsConfigurationSource) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // TODO(T06): 替换为 /auth/login|register|wechat/login、GET /menu/**、/files/**、/actuator/health 放行，
                //  其余按 CUSTOMER / MERCHANT / ADMIN 角色鉴权，并注册 JwtAuthenticationFilter。
                .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll());
        return http.build();
    }

    /** 密码哈希统一 BCrypt(cost 10)（{@code docs/01-architecture.md §16.8}）。 */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }
}
