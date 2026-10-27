package com.restaurant.common.idempotency;

/**
 * 幂等执行结果：首次执行返回 {@code data}；命中已成功记录时返回 {@code replayBody}（原样响应体）。
 */
public record IdempotentResult<T>(T data, String replayBody) {

    public boolean replayed() {
        return replayBody != null;
    }
}
