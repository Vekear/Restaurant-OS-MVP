package com.restaurant.common.exception;

import com.restaurant.common.api.ErrorCode;
import com.restaurant.common.api.Result;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 全局异常处理器：把所有异常收敛为统一响应体 {@link Result}。
 *
 * <p>错误码遵循 {@code docs/01-architecture.md §16.6}：HTTP 状态码表达协议语义，
 * 响应体 {@code code} 表达业务语义（前端按 code 分支）；字段校验失败额外返回
 * {@code data.errors=[{field,message}]}（{@code docs/02-api.md §0.2}）。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final int MAX_VALIDATION_ERRORS = 5;

    @ExceptionHandler(BizException.class)
    public ResponseEntity<Result<Void>> handleBizException(BizException exception) {
        log.warn("业务异常 code={} message={}", exception.getCode(), exception.getMessage());
        return ResponseEntity.status(exception.getHttpStatus())
                .body(Result.failure(exception.getErrorCode(), exception.getMessage()));
    }

    /** 限流：HTTP 429 + 42901 + Retry-After。 */
    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<Result<Void>> handleRateLimitExceeded(RateLimitExceededException exception) {
        log.warn("请求被限流 retryAfter={}s", exception.getRetryAfterSeconds());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(exception.getRetryAfterSeconds()))
                .body(Result.failure(ErrorCode.TOO_MANY_REQUESTS));
    }

    /**
     * {@code @RequestBody @Valid} / 表单绑定校验失败（MethodArgumentNotValidException 是 BindException 子类）。
     */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<Result<ValidationErrors>> handleBindException(BindException exception) {
        List<ValidationErrorItem> errors = exception.getBindingResult().getFieldErrors().stream()
                .limit(MAX_VALIDATION_ERRORS)
                .map(fieldError -> new ValidationErrorItem(fieldError.getField(), defaultMessage(fieldError)))
                .toList();
        log.warn("参数校验失败: {}", describe(errors));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.failure(ErrorCode.PARAM_INVALID, ErrorCode.PARAM_INVALID.getMessage(),
                        new ValidationErrors(errors)));
    }

    /**
     * {@code @PathVariable} / {@code @RequestParam} 上的约束校验失败。
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Result<ValidationErrors>> handleConstraintViolation(ConstraintViolationException exception) {
        List<ValidationErrorItem> errors = exception.getConstraintViolations().stream()
                .limit(MAX_VALIDATION_ERRORS)
                .map(violation -> new ValidationErrorItem(violation.getPropertyPath().toString(), violation.getMessage()))
                .toList();
        log.warn("参数约束校验失败: {}", describe(errors));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.failure(ErrorCode.PARAM_INVALID, ErrorCode.PARAM_INVALID.getMessage(),
                        new ValidationErrors(errors)));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Result<Void>> handleMissingParameter(MissingServletRequestParameterException exception) {
        return badRequest("缺少必需参数: " + exception.getParameterName());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Result<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
        return badRequest("参数类型错误: " + exception.getName());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Result<Void>> handleNotReadable(HttpMessageNotReadableException exception) {
        log.warn("请求体解析失败: {}", exception.getMessage());
        return badRequest("请求体格式错误，请检查 JSON 结构与字段类型");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Result<Void>> handleIllegalArgument(IllegalArgumentException exception) {
        return badRequest(exception.getMessage() == null ? "业务参数不合法" : exception.getMessage());
    }

    /**
     * 注册表未定义 405 专项错误码，统一返回 40001，避免自行扩展契约。
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Result<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException exception) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(Result.failure(ErrorCode.PARAM_INVALID, "请求方法不支持: " + exception.getMethod()));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Result<Void>> handleNoResourceFound(NoResourceFoundException exception) {
        log.debug("静态资源不存在: {}", exception.getResourcePath());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Result.failure(ErrorCode.NOT_FOUND));
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<Result<Void>> handleDuplicateKey(DuplicateKeyException exception) {
        log.warn("唯一键冲突: {}", exception.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Result.failure(ErrorCode.UNIQUE_CONFLICT));
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<Result<Void>> handleMissingPart(MissingServletRequestPartException exception) {
        return badRequest("缺少必需的文件或参数: " + exception.getRequestPartName());
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<Result<Void>> handleMultipart(MultipartException exception) {
        log.warn("multipart 解析失败: {}", exception.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.failure(ErrorCode.FILE_INVALID));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Result<Void>> handleMaxUploadSize(MaxUploadSizeExceededException exception) {
        log.warn("上传文件超过大小限制: {}", exception.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.failure(ErrorCode.FILE_INVALID));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Result<Void>> handleAccessDenied(AccessDeniedException exception) {
        log.warn("权限不足: {}", exception.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Result.failure(ErrorCode.ROLE_FORBIDDEN));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Result<Void>> handleAuthentication(AuthenticationException exception) {
        log.warn("认证失败: {}", exception.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Result.failure(ErrorCode.UNAUTHENTICATED));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleUnknownException(Exception exception) {
        log.error("未处理异常: {}", exception.getMessage(), exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.failure(ErrorCode.INTERNAL_ERROR));
    }

    private ResponseEntity<Result<Void>> badRequest(String message) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.failure(ErrorCode.PARAM_INVALID, message));
    }

    private String defaultMessage(FieldError fieldError) {
        return fieldError.getDefaultMessage() == null ? "参数不合法" : fieldError.getDefaultMessage();
    }

    private String describe(List<ValidationErrorItem> errors) {
        return errors.stream()
                .map(item -> item.field() + ": " + item.message())
                .collect(Collectors.joining("; "));
    }
}
