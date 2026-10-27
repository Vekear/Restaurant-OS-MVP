package com.restaurant.common.config;

import com.restaurant.common.log.TraceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.util.List;

/**
 * Web MVC 配置：开发 CORS、本地存储静态资源映射。
 *
 * <p>/files/** 映射到 {@code app.storage.local.root}，供本地兜底存储的图片预览；
 * 生产若走 oss 直传则不使用该映射。
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final AppProperties appProperties;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns(allowedOrigins().toArray(String[]::new))
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders(TraceContext.HEADER_NAME, "Idempotency-Replayed", "Retry-After")
                .allowCredentials(false)
                .maxAge(appProperties.getCors().getMaxAgeSeconds());
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        AppProperties.Storage.Local local = appProperties.getStorage().getLocal();
        Path root = Path.of(local.getRoot()).toAbsolutePath().normalize();
        String location = root.toUri().toString();
        if (!location.endsWith("/")) {
            location = location + "/";
        }
        String urlPrefix = local.getUrlPrefix();
        String pattern = urlPrefix.endsWith("/") ? urlPrefix + "**" : urlPrefix + "/**";
        registry.addResourceHandler(pattern)
                .addResourceLocations(location)
                .setCachePeriod(3600);
    }

    /**
     * Spring Security 的 CORS 入口复用同一份来源配置，避免两处漂移。
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(allowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(List.of(TraceContext.HEADER_NAME, "Idempotency-Replayed", "Retry-After"));
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(appProperties.getCors().getMaxAgeSeconds());
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    private List<String> allowedOrigins() {
        return appProperties.getCors().getAllowedOriginPatterns();
    }
}
