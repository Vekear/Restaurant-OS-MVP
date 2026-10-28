package com.restaurant.review;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.restaurant.common.api.ErrorCode;
import com.restaurant.common.api.PageResult;
import com.restaurant.common.exception.BizException;
import com.restaurant.merchant.MerchantGuard;
import com.restaurant.review.mapper.EvaluationMapper;
import com.restaurant.review.vo.ReviewView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 商家评价列表（{@code docs/02-api.md §19.1}）。 */
@Service
@RequiredArgsConstructor
public class MerchantReviewService {

    private final EvaluationMapper evaluationMapper;

    private final MerchantGuard merchantGuard;

    private final ReviewViewAssembler reviewViewAssembler;

    @Transactional(readOnly = true)
    public PageResult<ReviewView> list(Integer rating, int page, int size) {
        if (rating != null && (rating < 1 || rating > 5)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "rating 取值范围为 1-5");
        }
        Long merchantId = merchantGuard.requireActiveMerchantId();
        IPage<Evaluation> result = evaluationMapper.selectPage(new Page<>(page, size),
                Wrappers.<Evaluation>lambdaQuery()
                        .eq(Evaluation::getMerchantId, merchantId)
                        .eq(rating != null, Evaluation::getRating, rating)
                        .orderByDesc(Evaluation::getCreatedAt)
                        .orderByDesc(Evaluation::getId));
        return PageResult.of(reviewViewAssembler.assemble(result.getRecords()),
                result.getTotal(), page, size);
    }
}
