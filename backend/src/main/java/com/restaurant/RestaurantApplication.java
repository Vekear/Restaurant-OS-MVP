package com.restaurant;

import com.restaurant.common.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * 餐厅点餐系统 MVP 后端启动类。
 *
 * <p>Web 外卖端（顾客 / 商家 / 管理后台）与堂食小程序共用同一套 REST API，
 * API 前缀统一为 {@code /api/v1}，健康检查为 {@code /actuator/health}。
 */
@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
public class RestaurantApplication {

    public static void main(String[] args) {
        SpringApplication.run(RestaurantApplication.class, args);
    }
}
