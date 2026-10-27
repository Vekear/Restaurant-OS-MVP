package com.restaurant.review;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.restaurant.contract.ReviewQueryPort;
import com.restaurant.review.mapper.EvaluationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** {@link ReviewQueryPort} 的评价域实现。 */
@Component
@RequiredArgsConstructor
public class ReviewQueryAdapter implements ReviewQueryPort {

    private final EvaluationMapper evaluationMapper;

    @Override
    public Long findReviewIdByOrder(Long orderId) {
        Evaluation evaluation = evaluationMapper.selectOne(Wrappers.<Evaluation>lambdaQuery()
                .eq(Evaluation::getOrderId, orderId)
                .last("LIMIT 1"));
        return evaluation == null ? null : evaluation.getId();
    }
}
