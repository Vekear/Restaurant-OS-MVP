package com.restaurant.contract;

/**
 * 跨域端口：释放桌台（实现方：table 域；消费方：order 域堂食完成/取消）。
 *
 * <p>必须在订单状态流转的同一事务内调用：订单完成 + 会话关闭 + 桌台释放三写要么全成功，
 * 要么全部回滚（{@code docs/02-api.md §18.12}）。实现需幂等：桌台已 FREE 时直接返回。
 */
public interface TableReleasePort {

    /**
     * 关闭会话并释放桌台。
     *
     * @param tableId   桌台 ID
     * @param sessionId 会话 ID
     */
    void release(Long tableId, Long sessionId);
}
