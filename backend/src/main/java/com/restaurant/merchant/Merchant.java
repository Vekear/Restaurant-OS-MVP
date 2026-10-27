package com.restaurant.merchant;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.restaurant.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 商家表 {@code merchant}：MERCHANT 角色账号的店铺主体。
 *
 * <p>本类当前只映射鉴权所需字段；菜单/订单任务会在此基础上扩展营业状态、配送费等。
 */
@Getter
@Setter
@TableName("merchant")
public class Merchant extends BaseEntity {

    /** 所属 {@code users.id}，一个商家账号对应一个店铺。 */
    private Long userId;

    private String name;

    /** OPEN / CLOSED。 */
    private String businessStatus;

    /** PENDING / APPROVED / REJECTED。 */
    private String auditStatus;

    /** ENABLED / DISABLED。 */
    private String status;

    @Version
    private Integer version;
}
