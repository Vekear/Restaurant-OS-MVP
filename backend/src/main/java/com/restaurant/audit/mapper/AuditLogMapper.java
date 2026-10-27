package com.restaurant.audit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.restaurant.audit.AuditLog;
import org.apache.ibatis.annotations.Mapper;

/** 审计日志 Mapper。 */
@Mapper
public interface AuditLogMapper extends BaseMapper<AuditLog> {
}
