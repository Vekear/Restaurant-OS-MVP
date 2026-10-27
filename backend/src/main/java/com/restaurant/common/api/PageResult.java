package com.restaurant.common.api;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.Getter;

import java.io.Serializable;
import java.util.List;

/**
 * 统一分页响应结构（{@code docs/02-api.md §0.2}），运行在 {@link Result#getData()} 中。
 *
 * <p>字段固定为 {@code list / total / page / size / pages}，页码从 1 开始；
 * MyBatis-Plus 查询结果用 {@link #from(IPage)} 转换。
 *
 * @param <T> 记录类型
 */
@Getter
public class PageResult<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 当前页记录。 */
    private final List<T> list;

    /** 总记录数。 */
    private final long total;

    /** 当前页码，从 1 开始。 */
    private final long page;

    /** 每页条数。 */
    private final long size;

    /** 总页数。 */
    private final long pages;

    private PageResult(List<T> list, long total, long page, long size) {
        this.list = list;
        this.total = total;
        this.page = page;
        this.size = size;
        this.pages = size <= 0 ? 0 : (total + size - 1) / size;
    }

    public static <T> PageResult<T> of(List<T> list, long total, long page, long size) {
        List<T> safeList = list == null ? List.of() : list;
        return new PageResult<>(safeList, Math.max(total, 0), Math.max(page, 1), Math.max(size, 0));
    }

    public static <T> PageResult<T> from(IPage<T> iPage) {
        return of(iPage.getRecords(), iPage.getTotal(), iPage.getCurrent(), iPage.getSize());
    }

    public static <T> PageResult<T> empty(long page, long size) {
        return of(List.of(), 0, page, size);
    }
}
