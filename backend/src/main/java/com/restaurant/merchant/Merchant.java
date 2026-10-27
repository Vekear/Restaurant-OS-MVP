package com.restaurant.merchant;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.restaurant.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 商家表 {@code merchant}：MERCHANT 角色账号的店铺主体。
 *
 * <p>公开信息（名称/简介/配送费/起送价等）用于 {@code /merchants/{id}} 与 {@code /public/config}；
 * 营业状态 OPEN/CLOSED 与审核状态 PENDING/APPROVED/REJECTED 控制店铺可用性。
 */
@Getter
@Setter
@TableName("merchant")
public class Merchant extends BaseEntity {

    /** 所属 {@code users.id}，一个商家账号对应一个店铺。 */
    private Long userId;

    private String name;

    private String description;

    private String logoUrl;

    private String phone;

    private String address;

    /** 配送费（元）。 */
    private BigDecimal deliveryFee;

    /** 起送金额（元）。 */
    private BigDecimal minOrderAmount;

    /** OPEN / CLOSED。 */
    private String businessStatus;

    /** PENDING / APPROVED / REJECTED。 */
    private String auditStatus;

    /** ENABLED / DISABLED。 */
    private String status;

    @Version
    private Integer version;
}
