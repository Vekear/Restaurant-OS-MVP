package com.restaurant.stat.row;

import lombok.Data;

import java.math.BigDecimal;

/** 热销菜品聚合行。 */
@Data
public class HotDishRow {

    private Long dishId;

    private String dishName;

    private Long quantity;

    private BigDecimal amount;
}
