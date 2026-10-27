package com.restaurant.contract;

/** 跨域端口：读取用户收货地址快照（实现方：user 域）。 */
public interface AddressPort {

    /**
     * 读取属于指定用户的地址（逻辑删除的地址视为不存在）。
     *
     * @param addressId 地址 ID
     * @param userId    用户 ID
     * @return 不存在或不属于该用户返回 {@code null}
     */
    AddressSnapshot getOwned(Long addressId, Long userId);

    /**
     * 地址快照（下单时冗余到 orders）。
     *
     * @param id        地址 ID
     * @param userId    归属用户
     * @param consignee 收货人
     * @param phone     联系电话
     * @param province  省
     * @param city      市
     * @param district  区县
     * @param detail    详细地址
     */
    record AddressSnapshot(Long id,
                           Long userId,
                           String consignee,
                           String phone,
                           String province,
                           String city,
                           String district,
                           String detail) {
    }
}
