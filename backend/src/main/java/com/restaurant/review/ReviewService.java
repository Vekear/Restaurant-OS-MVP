package com.restaurant.review;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.restaurant.common.api.ErrorCode;
import com.restaurant.common.enums.OrderStatus;
import com.restaurant.common.exception.BizException;
import com.restaurant.contract.OrderLookupPort;
import com.restaurant.contract.UserLookupPort;
import com.restaurant.review.dto.ReviewCreateRequest;
import com.restaurant.review.mapper.EvaluationMapper;
import com.restaurant.review.vo.ReviewView;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 订单评价：一单一评，仅已完成订单可评。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewService {

    private final EvaluationMapper evaluationMapper;

    private final OrderLookupPort orderLookupPort;

    private final UserLookupPort userLookupPort;

    @Transactional(rollbackFor = Exception.class)
    public ReviewView create(Long userId, Long orderId, ReviewCreateRequest request) {
        OrderLookupPort.OrderBrief order = requireOwnedCompletedOrder(userId, orderId);
        Long existing = evaluationMapper.selectCount(Wrappers.<Evaluation>lambdaQuery()
                .eq(Evaluation::getOrderId, orderId));
        if (existing != null && existing > 0) {
            throw new BizException(ErrorCode.ORDER_ALREADY_REVIEWED, "该订单已评价");
        }
        Evaluation evaluation = new Evaluation();
        evaluation.setOrderId(orderId);
        evaluation.setUserId(userId);
        evaluation.setMerchantId(order.merchantId());
        evaluation.setRating(request.getRating());
        evaluation.setContent(request.getContent());
        evaluation.setImageUrls("");
        evaluation.setStatus("ENABLED");
        try {
            evaluationMapper.insert(evaluation);
        } catch (DuplicateKeyException e) {
            throw new BizException(ErrorCode.ORDER_ALREADY_REVIEWED, "该订单已评价");
        }
        log.info("订单评价成功 orderId={}, userId={}, rating={}", orderId, userId, request.getRating());
        return toView(evaluation, order);
    }

    @Transactional(readOnly = true)
    public ReviewView get(Long userId, Long orderId) {
        OrderLookupPort.OrderBrief order = orderLookupPort.get(orderId);
        if (order == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "订单不存在");
        }
        if (!order.userId().equals(userId)) {
            throw new BizException(ErrorCode.RESOURCE_FORBIDDEN, "无权访问该订单");
        }
        Evaluation evaluation = evaluationMapper.selectOne(Wrappers.<Evaluation>lambdaQuery()
                .eq(Evaluation::getOrderId, orderId)
                .last("LIMIT 1"));
        if (evaluation == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "评价不存在");
        }
        return toView(evaluation, order);
    }

    private OrderLookupPort.OrderBrief requireOwnedCompletedOrder(Long userId, Long orderId) {
        OrderLookupPort.OrderBrief order = orderLookupPort.get(orderId);
        if (order == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "订单不存在");
        }
        if (!order.userId().equals(userId)) {
            throw new BizException(ErrorCode.RESOURCE_FORBIDDEN, "无权评价该订单");
        }
        if (!OrderStatus.COMPLETED.name().equals(order.status())) {
            throw new BizException(ErrorCode.ORDER_NOT_COMPLETED, "订单未完成，暂不可评价");
        }
        return order;
    }

    private ReviewView toView(Evaluation evaluation, OrderLookupPort.OrderBrief order) {
        return new ReviewView(evaluation.getId(), evaluation.getOrderId(), order.orderType(), order.orderNo(),
                evaluation.getUserId(), userLookupPort.nickname(evaluation.getUserId()),
                evaluation.getRating(), evaluation.getContent(), evaluation.getImageUrls(),
                orderLookupPort.dishNames(order.id()), evaluation.getReply(), evaluation.getCreatedAt());
    }
}
