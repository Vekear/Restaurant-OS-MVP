package com.restaurant.common.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.BlockAttackInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置。
 *
 * <ul>
 *   <li>分页：MyBatis-Plus 内置 {@link PaginationInnerInterceptor}（不使用 PageHelper），单页上限 100；</li>
 *   <li>乐观锁：{@code @Version} 字段（桌台并发扫码、订单状态流转等）；</li>
 *   <li>全表更新/删除拦截：避免误写全表。</li>
 * </ul>
 *
 * <p>Mapper 接口统一放在各业务域的 {@code mapper} 子包下，例如
 * {@code com.restaurant.order.mapper.OrderMapper}。
 */
@Configuration
@MapperScan("com.restaurant.**.mapper")
public class MybatisPlusConfig {

    /** 单页最大条数，避免 size 过大拖垮数据库。 */
    public static final long MAX_PAGE_SIZE = 100L;

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.MYSQL);
        pagination.setMaxLimit(MAX_PAGE_SIZE);
        pagination.setOverflow(false);
        interceptor.addInnerInterceptor(pagination);
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        interceptor.addInnerInterceptor(new BlockAttackInnerInterceptor());
        return interceptor;
    }
}
