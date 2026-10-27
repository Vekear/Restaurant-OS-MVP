package com.restaurant.user;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.restaurant.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/** 收货地址 {@code address}（逻辑删除；历史订单保存快照）。 */
@Getter
@Setter
@TableName("address")
public class Address extends BaseEntity {

    private Long userId;

    private String consignee;

    private String phone;

    private String province;

    private String city;

    private String district;

    private String detail;

    private Integer isDefault;

    @TableLogic
    private Integer deleted;

    @Version
    private Integer version;
}
