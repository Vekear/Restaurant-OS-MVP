package com.restaurant.audit;

import com.restaurant.audit.mapper.AuditLogMapper;
import com.restaurant.common.log.TraceContext;
import com.restaurant.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 审计日志写入服务。
 *
 * <p>规则：审计失败绝不影响主流程（例如登录成功但审计写库失败），内部捕获异常并 WARN；
 * 详情只记录必要摘要，禁止写入密码、token、session_key 等敏感信息。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private static final int MAX_DETAIL_LENGTH = 1000;

    private static final int MAX_USER_AGENT_LENGTH = 255;

    private static final int MAX_IP_LENGTH = 64;

    private final AuditLogMapper auditLogMapper;

    /** 登录成功审计。 */
    public void loginSuccess(User user, String ip, String userAgent) {
        record(user.getId(), user.getUsername(), user.getRole(), AuditActions.LOGIN_SUCCESS,
                null, null, "登录成功", ip, userAgent, true);
    }

    /** 登录失败审计（用户名不存在/密码错误/账号禁用/微信 code 无效等，统一记为 LOGIN_FAIL）。 */
    public void loginFail(LoginAttempt attempt, String ip, String userAgent) {
        record(attempt.userId(), attempt.username(), attempt.role(), AuditActions.LOGIN_FAIL,
                null, null, attempt.reason(), ip, userAgent, false);
    }

    /** 登出审计。 */
    public void logout(User user, String ip, String userAgent) {
        record(user.getId(), user.getUsername(), user.getRole(), AuditActions.LOGOUT,
                "USER", String.valueOf(user.getId()), "登出", ip, userAgent, true);
    }

    /** 资料修改审计。 */
    public void profileUpdated(User user, String detail, String ip, String userAgent) {
        record(user.getId(), user.getUsername(), user.getRole(), AuditActions.PROFILE_UPDATE,
                "USER", String.valueOf(user.getId()), detail, ip, userAgent, true);
    }

    private void record(Long userId,
                        String username,
                        String role,
                        String action,
                        String targetType,
                        String targetId,
                        String detail,
                        String ip,
                        String userAgent,
                        boolean success) {
        try {
            AuditLog auditLog = new AuditLog();
            auditLog.setUserId(userId);
            auditLog.setUsername(truncate(username, 50));
            auditLog.setRole(truncate(role, 20));
            auditLog.setAction(action);
            auditLog.setTargetType(targetType);
            auditLog.setTargetId(targetId);
            auditLog.setTraceId(TraceContext.currentTraceId());
            auditLog.setDetail(truncate(detail, MAX_DETAIL_LENGTH));
            auditLog.setIp(truncate(ip, MAX_IP_LENGTH));
            auditLog.setUserAgent(truncate(userAgent, MAX_USER_AGENT_LENGTH));
            auditLog.setSuccess(success);
            auditLogMapper.insert(auditLog);
        } catch (RuntimeException exception) {
            log.warn("审计日志写入失败 action={} userId={} traceId={}: {}",
                    action, userId, TraceContext.currentTraceId(), exception.getMessage());
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }

    /** 登录失败审计上下文（避免为审计构造半成品 User）。 */
    public record LoginAttempt(Long userId, String username, String role, String reason) {
    }
}
