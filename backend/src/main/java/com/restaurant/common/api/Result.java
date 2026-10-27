package com.restaurant.common.api;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.restaurant.common.log.TraceContext;
import lombok.Getter;

import java.io.Serializable;

/**
 * 统一响应体：{@code {"code":0,"message":"ok","data":...,"traceId":"..."}}。
 *
 * <p>约定见 {@code docs/02-api.md §0.2}：成功 {@code code=0}；失败 {@code code} 取自
 * {@link ErrorCode}，{@code data} 一般为 null，字段校验失败时携带 {@code data.errors}。
 * traceId 在构造时从 MDC 捕获，因此必须在请求线程内创建（Controller 或异常处理器中创建均满足）。
 *
 * @param <T> 业务数据类型
 */
@Getter
public class Result<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 成功码。 */
    public static final int SUCCESS_CODE = 0;

    /** 成功提示语。 */
    public static final String SUCCESS_MESSAGE = "ok";

    private final int code;

    private final String message;

    private final T data;

    private final String traceId;

    private Result(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.traceId = TraceContext.currentTraceId();
    }

    public static <T> Result<T> success(T data) {
        return new Result<>(SUCCESS_CODE, SUCCESS_MESSAGE, data);
    }

    public static Result<Void> success() {
        return new Result<>(SUCCESS_CODE, SUCCESS_MESSAGE, null);
    }

    public static <T> Result<T> failure(ErrorCode errorCode) {
        return new Result<>(errorCode.getCode(), errorCode.getMessage(), null);
    }

    public static <T> Result<T> failure(ErrorCode errorCode, String message) {
        return new Result<>(errorCode.getCode(), message, null);
    }

    public static <T> Result<T> failure(ErrorCode errorCode, String message, T data) {
        return new Result<>(errorCode.getCode(), message, data);
    }

    public static <T> Result<T> failure(int code, String message) {
        return new Result<>(code, message, null);
    }

    /** 供服务端判断，不参与 JSON 序列化（契约响应仅 code/message/data/traceId）。 */
    @JsonIgnore
    public boolean isSuccess() {
        return code == SUCCESS_CODE;
    }
}
