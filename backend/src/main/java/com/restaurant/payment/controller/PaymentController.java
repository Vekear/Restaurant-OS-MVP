package com.restaurant.payment.controller;

import com.restaurant.common.api.Result;
import com.restaurant.common.config.OpenApiConfig;
import com.restaurant.common.constant.ApiConstants;
import com.restaurant.common.idempotency.IdempotencyService;
import com.restaurant.common.idempotency.IdempotentResult;
import com.restaurant.payment.PaymentService;
import com.restaurant.payment.dto.PaymentCreateRequest;
import com.restaurant.payment.vo.PaymentCallbackResult;
import com.restaurant.payment.vo.PaymentCreateView;
import com.restaurant.payment.vo.PaymentView;
import com.restaurant.security.RoleGuard;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 支付接口：创建支付单、查询、mock-pay、模拟网关回调。 */
@RestController
@RequestMapping(ApiConstants.API_V1 + "/payments")
@RequiredArgsConstructor
@Tag(name = "支付")
public class PaymentController {

    private final PaymentService paymentService;

    private final IdempotencyService idempotencyService;

    private final RoleGuard roleGuard;

    @PostMapping
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
    @Operation(summary = "创建支付单（需 Idempotency-Key）")
    public ResponseEntity<?> create(
            @RequestHeader(value = ApiConstants.HEADER_IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody PaymentCreateRequest request) {
        Long userId = roleGuard.currentUserId();
        IdempotentResult<PaymentCreateView> result = idempotencyService.execute(idempotencyKey,
                "POST /api/v1/payments", userId, request, () -> paymentService.create(userId, request));
        if (result.replayed()) {
            return ResponseEntity.ok()
                    .header(ApiConstants.HEADER_IDEMPOTENCY_REPLAYED, "true")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(result.replayBody());
        }
        return ResponseEntity.ok(Result.success(result.data()));
    }

    @GetMapping("/{payNo}")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
    @Operation(summary = "查询支付单")
    public Result<PaymentView> get(@PathVariable String payNo) {
        return Result.success(paymentService.get(roleGuard.currentUserId(), payNo));
    }

    @PostMapping("/{payNo}/mock-pay")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
    @Operation(summary = "模拟支付（需 Idempotency-Key）")
    public ResponseEntity<?> mockPay(
            @PathVariable String payNo,
            @RequestHeader(value = ApiConstants.HEADER_IDEMPOTENCY_KEY, required = false) String idempotencyKey) {
        Long userId = roleGuard.currentUserId();
        IdempotentResult<PaymentView> result = idempotencyService.execute(idempotencyKey,
                "POST /api/v1/payments/" + payNo + "/mock-pay", userId, payNo,
                () -> paymentService.mockPay(userId, payNo));
        if (result.replayed()) {
            return ResponseEntity.ok()
                    .header(ApiConstants.HEADER_IDEMPOTENCY_REPLAYED, "true")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(result.replayBody());
        }
        return ResponseEntity.ok(Result.success(result.data()));
    }

    /** 模拟支付网关回调（Nginx 限源；HMAC 签名 + 时间戳 + nonce 防重放）。 */
    @PostMapping("/mock/callback")
    @Operation(summary = "模拟支付回调（内部）")
    public Result<PaymentCallbackResult> callback(
            @RequestBody String rawBody,
            @RequestHeader(value = "X-Pay-Timestamp", required = false) String timestamp,
            @RequestHeader(value = "X-Pay-Nonce", required = false) String nonce,
            @RequestHeader(value = "X-Pay-Signature", required = false) String signature) {
        return Result.success(paymentService.handleCallback(rawBody, timestamp, nonce, signature));
    }
}
