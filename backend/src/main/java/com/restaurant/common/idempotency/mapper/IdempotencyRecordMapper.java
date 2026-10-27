package com.restaurant.common.idempotency.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.restaurant.common.idempotency.IdempotencyRecord;
import org.apache.ibatis.annotations.Mapper;

/** 幂等记录 Mapper。 */
@Mapper
public interface IdempotencyRecordMapper extends BaseMapper<IdempotencyRecord> {
}
