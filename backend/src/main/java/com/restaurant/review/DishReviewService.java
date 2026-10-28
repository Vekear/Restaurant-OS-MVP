package com.restaurant.review;

import com.restaurant.common.api.ErrorCode;
import com.restaurant.common.api.PageResult;
import com.restaurant.common.exception.BizException;
import com.restaurant.menu.Dish;
import com.restaurant.menu.mapper.DishMapper;
import com.restaurant.review.mapper.EvaluationMapper;
import com.restaurant.review.vo.ReviewView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 公开菜品评价（{@code docs/02-api.md §6.4}）。
 *
 * <p>仅返回 {@code evaluation.status = 'ENABLED'} 且订单包含该菜品的评价；OFF_SHELF 菜品仍可查看评价。
 * 排序固定 {@code createdAt DESC, id DESC}，同一订单内该菜品多行通过 DISTINCT 去重。
 */
@Service
@RequiredArgsConstructor
public class DishReviewService {

    private final DishMapper dishMapper;

    private final EvaluationMapper evaluationMapper;

    private final ReviewViewAssembler reviewViewAssembler;

    @Transactional(readOnly = true)
    public PageResult<ReviewView> list(Long dishId, int page, int size) {
        Dish dish = dishMapper.selectById(dishId);
        if (dish == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "菜品不存在");
        }
        long total = evaluationMapper.countEnabledByDish(dishId);
        if (total == 0) {
            return PageResult.empty(page, size);
        }
        long offset = (long) (page - 1) * size;
        List<Evaluation> evaluations = evaluationMapper.selectEnabledPageByDish(dishId, offset, size);
        return PageResult.of(reviewViewAssembler.assemble(evaluations), total, page, size);
    }
}
