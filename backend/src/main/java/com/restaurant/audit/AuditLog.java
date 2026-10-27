package com.restaurant.audit;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 审计日志表 {@code audit_log}（登录成功/失败、登出等关键动作）。
 *
 * <p>审计写入失败不得影响主流程：{@link AuditService} 内部捕获并 WARN。
 * 该表只有 {@code created_at}，没有 {@code updated_at}，因此不继承 BaseEntity。
 */
@Getter
@Setter
@TableName("audit_log")
public class AuditLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String username;

    private String role;

    /** 动作名，见 {@link AuditActions}。 */
    private String action;

    private String targetType;

    private String targetId;

    private String traceId;

    private String detail;

    private String ip;

    private String userAgent;

    /** true=成功，false=失败（对应 TINYINT(1)）。 */
    private Boolean success;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
