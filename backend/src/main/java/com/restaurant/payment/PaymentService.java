package com.restaurant.payment;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.restaurant.common.api.ErrorCode;
import com.restaurant.common.config.AppProperties;
import com.restaurant.common.enums.OrderStatus;
import com.restaurant.common.enums.OrderType;
import com.restaurant.common.enums.PaymentStatus;
import com.restaurant.common.exception.BizException;
import com.restaurant.contract.OrderLookupPort;
import com.restaurant.contract.OrderPayablePort;
import com.restaurant.contract.PaymentQueryPort;
import com.restaurant.contract.PaymentRefundPort;
import com.restaurant.contract.PaymentSnapshot;
import com.restaurant.contract.event.PaymentSucceededEvent;
import com.restaurant.payment.dto.PaymentCallbackRequest;
import com.restaurant.payment.dto.PaymentCreateRequest;
import com.restaurant.payment.mapper.PaymentMapper;
import com.restaurant.payment.vo.PaymentCallbackResult;
import com.restaurant.payment.vo.PaymentCreateView;
import com.restaurant.payment.vo.PaymentView;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 模拟支付：创建支付单、mock-pay（自回调 + HMAC）、回调验签与幂等、退款/关闭。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService implements PaymentQueryPort, PaymentRefundPort {

    private static final String CHANNEL_MOCK = "MOCK";

    private static final String NONCE_PREFIX = "pay:nonce:";

    private static final Duration NONCE_TTL = Duration.ofMinutes(10);

    private final PaymentMapper paymentMapper;

    private final OrderPayablePort orderPayablePort;

    private final OrderLookupPort orderLookupPort;

    private final MockPayGatewayClient mockPayGatewayClient;

    private final StringRedisTemplate stringRedisTemplate;

    private final ApplicationEventPublisher eventPublisher;

    private final AppProperties appProperties;

    private final TransactionTemplate transactionTemplate;

    /** 创建支付单；已有待支付单时复用，已支付订单 40913。 */
    @Transactional(rollbackFor = Exception.class)
    public PaymentCreateView create(Long userId, PaymentCreateRequest request) {
        OrderPayablePort.PayableOrder order = orderPayablePort.requireOwned(request.getOrderId(), userId);
        String orderType = StringUtils.hasText(request.getOrderType()) ? request.getOrderType() : OrderType.TAKEOUT.name();
        if (!orderType.equals(order.orderType())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "订单类型不匹配");
        }
        Payment existing = latestByOrderEntity(order.orderId());
        if (existing != null && PaymentStatus.SUCCESS.name().equals(existing.getStatus())) {
            throw new BizException(ErrorCode.PAYMENT_ALREADY_PAID, "订单已支付");
        }
        requirePayable(order);
        if (existing != null && PaymentStatus.PENDING.name().equals(existing.getStatus())) {
            return toCreateView(existing, order);
        }

        BigDecimal mockCharge = mockCharge(order.payAmount());
        Payment payment = new Payment();
        payment.setPayNo(generatePayNo());
        payment.setOrderId(order.orderId());
        payment.setUserId(userId);
        payment.setOrderAmount(order.payAmount());
        payment.setPaidAmount(mockCharge);
        payment.setChannel(CHANNEL_MOCK);
        payment.setStatus(PaymentStatus.PENDING.name());
        payment.setExpireAt(resolveExpireAt(order.timeoutAt()));
        paymentMapper.insert(payment);
        log.info("支付单创建 payNo={}, orderId={}, orderAmount={}, paidAmount={}",
                payment.getPayNo(), order.orderId(), order.payAmount(), mockCharge);
        return toCreateView(payment, order);
    }

    /**
     * mock-pay：自身回调走验签链路；失败则降级直接置成功（ADR-0004）。
     *
     * <p>注意：本方法刻意不加事务——回调由另一个请求线程提交，若在长事务内读取会命中
     * REPEATABLE READ 旧快照，导致响应 payment.status 仍是 PENDING；降级分支单独用
     * TransactionTemplate 保证支付单与订单状态同一事务提交。
     */
    public PaymentView mockPay(Long userId, String payNo) {
        Payment payment = requireOwnedPayment(userId, payNo);
        if (PaymentStatus.SUCCESS.name().equals(payment.getStatus())) {
            return toView(payment);
        }
        if (PaymentStatus.FAILED.name().equals(payment.getStatus())
                || PaymentStatus.REFUNDED.name().equals(payment.getStatus())) {
            throw new BizException(ErrorCode.ORDER_STATUS_INVALID, "支付单已关闭，无法支付");
        }
        OrderLookupPort.OrderBrief order = orderLookupPort.get(payment.getOrderId());
        if (order == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "订单不存在");
        }
        if (!OrderStatus.PENDING_PAYMENT.name().equals(order.status())) {
            throw new BizException(ErrorCode.ORDER_STATUS_INVALID, "订单状态不允许支付");
        }
        String transactionNo = "MOCK" + UUID.randomUUID().toString().replace("-", "").substring(0, 24).toUpperCase();
        PaymentCallbackRequest callback = new PaymentCallbackRequest(payment.getPayNo(), transactionNo,
                mockCharge(payment.getOrderAmount()),
                LocalDateTime.now().atZone(ZoneId.of("Asia/Shanghai")).toOffsetDateTime().toString(),
                PaymentStatus.SUCCESS.name());
        boolean delivered = mockPayGatewayClient.sendCallback(callback);
        if (!delivered) {
            transactionTemplate.executeWithoutResult(status -> {
                Payment current = paymentMapper.selectById(payment.getId());
                if (current != null && PaymentStatus.PENDING.name().equals(current.getStatus())) {
                    markSuccess(current, transactionNo, LocalDateTime.now());
                }
            });
        }
        Payment latest = paymentMapper.selectById(payment.getId());
        return toView(latest);
    }

    /** 回调验签 + 金额校验 + 幂等处理。 */
    @Transactional(rollbackFor = Exception.class)
    public PaymentCallbackResult handleCallback(String rawBody, String timestampHeader, String nonce,
                                                String signature) {
        long timestamp = parseTimestamp(timestampHeader);
        long skewMillis = Math.abs(System.currentTimeMillis() - timestamp);
        if (skewMillis > appProperties.getPayment().getCallbackAllowedClockSkewSeconds() * 1000L) {
            throw new BizException(ErrorCode.PARAM_INVALID, "回调时间戳超出允许范围");
        }
        if (!StringUtils.hasText(nonce) || nonce.length() < 8 || nonce.length() > 64) {
            throw new BizException(ErrorCode.PARAM_INVALID, "回调 nonce 非法");
        }
        String expected = MockPayGatewayClient.hmacSha256Hex(appProperties.getPayment().getCallbackSecret(),
                timestampHeader + "\n" + nonce + "\n" + rawBody);
        if (!StringUtils.hasText(signature) || !constantTimeEquals(expected, signature)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "回调签名校验失败");
        }
        Boolean fresh = stringRedisTemplate.opsForValue()
                .setIfAbsent(NONCE_PREFIX + nonce, "1", NONCE_TTL);
        if (Boolean.FALSE.equals(fresh)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "回调 nonce 已使用");
        }

        PaymentCallbackRequest request = parseBody(rawBody);
        if (!StringUtils.hasText(request.payNo())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "回调缺少 payNo");
        }
        Payment payment = paymentMapper.selectOne(Wrappers.<Payment>lambdaQuery()
                .eq(Payment::getPayNo, request.payNo()));
        if (payment == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "支付单不存在");
        }
        OrderLookupPort.OrderBrief order = orderLookupPort.get(payment.getOrderId());
        if (order == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "订单不存在");
        }
        BigDecimal expectedPaid = mockCharge(payment.getOrderAmount());
        if (request.paidAmount() == null
                || request.paidAmount().setScale(2, RoundingMode.HALF_UP).compareTo(expectedPaid) != 0) {
            throw new BizException(ErrorCode.AMOUNT_MISMATCH, "回调金额与订单不符");
        }

        if (PaymentStatus.SUCCESS.name().equals(payment.getStatus())) {
            return new PaymentCallbackResult(true, payment.getPayNo(), payment.getStatus());
        }
        if (PaymentStatus.REFUNDED.name().equals(payment.getStatus())) {
            return new PaymentCallbackResult(true, payment.getPayNo(), payment.getStatus());
        }
        if (OrderStatus.CANCELLED.name().equals(order.status())
                || OrderStatus.REJECTED.name().equals(order.status())) {
            payment.setStatus(PaymentStatus.REFUNDED.name());
            payment.setCallbackAt(LocalDateTime.now());
            paymentMapper.updateById(payment);
            return new PaymentCallbackResult(true, payment.getPayNo(), PaymentStatus.REFUNDED.name());
        }

        LocalDateTime paidAt = parsePaidAt(request.paidAt());
        markSuccess(payment, request.transactionNo(), paidAt);
        return new PaymentCallbackResult(true, payment.getPayNo(), PaymentStatus.SUCCESS.name());
    }

    @Transactional(readOnly = true)
    public PaymentView get(Long userId, String payNo) {
        return toView(requireOwnedPayment(userId, payNo));
    }

    @Override
    public PaymentSnapshot latestByOrder(Long orderId) {
        Payment payment = latestByOrderEntity(orderId);
        if (payment == null) {
            return null;
        }
        OrderLookupPort.OrderBrief order = orderLookupPort.get(orderId);
        return new PaymentSnapshot(payment.getPayNo(), payment.getOrderId(),
                order == null ? null : order.orderType(), order == null ? null : order.orderNo(),
                payment.getOrderAmount(), payment.getPaidAmount(), payment.getChannel(), payment.getStatus(),
                payment.getTransactionNo(), payment.getPaidAt(), payment.getCallbackAt(),
                payment.getExpireAt(), payment.getCreatedAt());
    }

    @Override
    public void refundByOrder(Long orderId) {
        paymentMapper.refundByOrder(orderId);
    }

    @Override
    public void closePendingByOrder(Long orderId) {
        paymentMapper.closePendingByOrder(orderId);
    }

    private void markSuccess(Payment payment, String transactionNo, LocalDateTime paidAt) {
        payment.setStatus(PaymentStatus.SUCCESS.name());
        payment.setTransactionNo(transactionNo);
        payment.setPaidAt(paidAt == null ? LocalDateTime.now() : paidAt);
        payment.setCallbackAt(LocalDateTime.now());
        int rows = paymentMapper.updateById(payment);
        if (rows == 0) {
            Payment latest = paymentMapper.selectById(payment.getId());
            if (latest != null && PaymentStatus.SUCCESS.name().equals(latest.getStatus())) {
                return;
            }
            throw new BizException(ErrorCode.ORDER_STATUS_INVALID, "支付单状态已变更，请重试");
        }
        eventPublisher.publishEvent(new PaymentSucceededEvent(payment.getId(), payment.getPayNo(),
                payment.getOrderId(), null, payment.getPaidAmount(), payment.getPaidAt(),
                payment.getTransactionNo()));
    }

    private Payment latestByOrderEntity(Long orderId) {
        return paymentMapper.selectOne(Wrappers.<Payment>lambdaQuery()
                .eq(Payment::getOrderId, orderId)
                .orderByDesc(Payment::getId)
                .last("LIMIT 1"));
    }

    private Payment requireOwnedPayment(Long userId, String payNo) {
        Payment payment = paymentMapper.selectOne(Wrappers.<Payment>lambdaQuery().eq(Payment::getPayNo, payNo));
        if (payment == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "支付单不存在");
        }
        if (!payment.getUserId().equals(userId)) {
            throw new BizException(ErrorCode.RESOURCE_FORBIDDEN, "无权访问该支付单");
        }
        return payment;
    }

    private void requirePayable(OrderPayablePort.PayableOrder order) {
        if (!OrderStatus.PENDING_PAYMENT.name().equals(order.status())) {
            throw new BizException(ErrorCode.ORDER_STATUS_INVALID, "订单当前状态不允许支付");
        }
        if (order.timeoutAt() != null && order.timeoutAt().isBefore(LocalDateTime.now())) {
            throw new BizException(ErrorCode.ORDER_STATUS_INVALID, "订单已超时取消");
        }
    }

    private BigDecimal mockCharge(BigDecimal orderAmount) {
        BigDecimal configured = appProperties.getPayment().getMockChargeAmount();
        if (configured == null) {
            configured = new BigDecimal("0.01");
        }
        BigDecimal charge = configured.min(orderAmount);
        return charge.setScale(2, RoundingMode.HALF_UP);
    }

    private LocalDateTime resolveExpireAt(LocalDateTime orderTimeoutAt) {
        LocalDateTime fifteen = LocalDateTime.now().plusMinutes(15);
        if (orderTimeoutAt != null && orderTimeoutAt.isBefore(fifteen)) {
            return orderTimeoutAt;
        }
        return fifteen;
    }

    private PaymentCreateView toCreateView(Payment payment, OrderPayablePort.PayableOrder order) {
        return new PaymentCreateView(payment.getPayNo(), payment.getOrderId(), order.orderType(), order.orderNo(),
                payment.getOrderAmount(), payment.getPaidAmount(), payment.getChannel(), true,
                "演示环境：订单金额按实展示，实际模拟扣款 ¥" + payment.getPaidAmount().toPlainString(),
                payment.getExpireAt());
    }

    private PaymentView toView(Payment payment) {
        OrderLookupPort.OrderBrief order = orderLookupPort.get(payment.getOrderId());
        return new PaymentView(payment.getPayNo(), payment.getOrderId(),
                order == null ? null : order.orderType(), order == null ? null : order.orderNo(),
                payment.getOrderAmount(), payment.getPaidAmount(), payment.getChannel(), payment.getStatus(),
                payment.getTransactionNo(), payment.getPaidAt(), payment.getCallbackAt(),
                payment.getExpireAt(), payment.getCreatedAt());
    }

    private long parseTimestamp(String header) {
        try {
            return Long.parseLong(header);
        } catch (NumberFormatException | NullPointerException e) {
            throw new BizException(ErrorCode.PARAM_INVALID, "回调时间戳非法");
        }
    }

    private LocalDateTime parsePaidAt(String paidAt) {
        if (!StringUtils.hasText(paidAt)) {
            return LocalDateTime.now();
        }
        try {
            return java.time.OffsetDateTime.parse(paidAt).atZoneSameInstant(ZoneId.of("Asia/Shanghai")).toLocalDateTime();
        } catch (DateTimeParseException e) {
            try {
                return LocalDateTime.parse(paidAt);
            } catch (DateTimeParseException ignored) {
                return LocalDateTime.now();
            }
        }
    }

    private PaymentCallbackRequest parseBody(String rawBody) {
        try {
            return com.fasterxml.jackson.databind.json.JsonMapper.builder().findAndAddModules()
                    .build().readValue(rawBody, PaymentCallbackRequest.class);
        } catch (Exception e) {
            throw new BizException(ErrorCode.PARAM_INVALID, "回调报文格式错误");
        }
    }

    private boolean constantTimeEquals(String expected, String actual) {
        return java.security.MessageDigest.isEqual(expected.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                actual.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static String generatePayNo() {
        return "P" + LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"))
                + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
    }
}
