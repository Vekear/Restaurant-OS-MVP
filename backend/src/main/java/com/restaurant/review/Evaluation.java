package com.restaurant.review;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.restaurant.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/** 订单评价 {@code evaluation}（一单一评，image_urls 本期为空）。 */
@Getter
@Setter
@TableName("evaluation")
public class Evaluation extends BaseEntity {

    private Long orderId;

    private Long userId;

    private Long merchantId;

    private Integer rating;

    private String content;

    private String imageUrls;

    private String reply;

    private String status;

    @Version
    private Integer version;
}
