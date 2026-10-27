package com.restaurant.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * SpringDoc / OpenAPI 配置。
 *
 * <p>Swagger UI：{@code /swagger-ui.html}，OpenAPI JSON：{@code /v3/api-docs}。
 * 声明 {@code bearerAuth} HTTP Bearer（JWT）安全方案，Swagger UI 右上角 Authorize 可直接粘贴 token 调试；
 * 具体端点的授权要求由 T06 在 Controller/Docket 上标注。
 */
@Configuration
public class OpenApiConfig {

    /** 与 Spring Security / Swagger 约定的安全方案名。 */
    public static final String BEARER_AUTH_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI restaurantOpenApi(AppProperties appProperties) {
        return new OpenAPI()
                .info(new Info()
                        .title("餐厅点餐系统 MVP API")
                        .version(appProperties.getVersion())
                        .description("""
                                外卖 Web（顾客/商家/管理后台）与堂食小程序共用的 REST API。
                                统一前缀 /api/v1；响应体 {code,message,data,traceId}；分页 data={list,total,page,size,pages}；
                                时间 ISO-8601 带偏移（Asia/Shanghai）；金额 BigDecimal（元）。
                                """)
                        .contact(new Contact().name("restaurant-mvp 团队")))
                .components(new Components().addSecuritySchemes(BEARER_AUTH_SCHEME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("登录后获得的 JWT，请求头 Authorization: Bearer <token>")))
                .servers(List.of(new Server().url("/").description("当前服务")));
    }
}
