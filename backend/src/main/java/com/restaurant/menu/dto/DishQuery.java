package com.restaurant.menu.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 公开菜单分页查询参数（参与缓存 key 生成）。
 */
@Data
public class DishQuery {

    private Long merchantId;

    private Long categoryId;

    @Size(max = 30, message = "keyword 不能超过 30")
    private String keyword;

    /** default / price_asc / price_desc / sales_desc。 */
    @Pattern(regexp = "default|price_asc|price_desc|sales_desc", message = "sort 取值不合法")
    private String sort = "default";

    @Min(value = 1, message = "page 最小为 1")
    @Max(value = 10000, message = "page 最大为 10000")
    private long page = 1L;

    @Min(value = 1, message = "size 最小为 1")
    @Max(value = 100, message = "size 最大为 100")
    private long size = 12L;

    /** 缓存 key：merchantId:categoryId:sort:keywordHash:page:size。 */
    public String cacheKey() {
        String keywordHash = keyword == null || keyword.isBlank()
                ? "-"
                : Integer.toHexString(keyword.trim().toLowerCase().hashCode());
        return merchantId + ":" + (categoryId == null ? "-" : categoryId) + ":" + sort
                + ":" + keywordHash + ":" + page + ":" + size;
    }
}
