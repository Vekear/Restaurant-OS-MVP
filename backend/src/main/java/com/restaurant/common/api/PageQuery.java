package com.restaurant.common.api;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.io.Serializable;

/**
 * 通用分页查询参数（{@code docs/02-api.md §0.4}）。
 *
 * <p>约束：{@code page} 从 1 开始且 ≤ 10000；{@code size} 默认 10，最大 100。
 * 具体端点可覆盖默认 size（如菜单列表默认 12）。
 */
@Data
public class PageQuery implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final long DEFAULT_PAGE = 1L;

    public static final long DEFAULT_SIZE = 10L;

    public static final long MAX_PAGE = 10_000L;

    public static final long MAX_SIZE = 100L;

    @Min(value = 1, message = "page 必须大于等于 1")
    @Max(value = MAX_PAGE, message = "page 不能超过 10000")
    private long page = DEFAULT_PAGE;

    @Min(value = 1, message = "size 必须大于等于 1")
    @Max(value = MAX_SIZE, message = "size 不能超过 100")
    private long size = DEFAULT_SIZE;

    /** 转为 MyBatis-Plus 分页对象。 */
    public <T> Page<T> toPage() {
        return new Page<>(page, size);
    }
}
