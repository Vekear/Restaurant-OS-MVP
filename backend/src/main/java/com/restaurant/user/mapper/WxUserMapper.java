package com.restaurant.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.restaurant.user.WxUser;
import org.apache.ibatis.annotations.Mapper;

/** 微信用户表 Mapper。 */
@Mapper
public interface WxUserMapper extends BaseMapper<WxUser> {
}
