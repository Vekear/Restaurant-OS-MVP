package com.restaurant.merchant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.restaurant.merchant.Merchant;
import org.apache.ibatis.annotations.Mapper;

/** 商家表 Mapper。 */
@Mapper
public interface MerchantMapper extends BaseMapper<Merchant> {
}
