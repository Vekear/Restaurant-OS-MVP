package com.restaurant.system.controller;

import com.restaurant.common.api.Result;
import com.restaurant.common.config.AppProperties;
import com.restaurant.common.constant.ApiConstants;
import com.restaurant.storage.service.FileStorageFacade;
import com.restaurant.system.vo.PublicConfigView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 公开运行时配置（{@code docs/02-api.md §2.2}），前端启动时读取一次，无需登录。
 *
 * <p>模拟支付提示与存储模式由服务端配置驱动，保证前后端对金额/上传方式理解一致。
 */
@RestController
@RequestMapping(ApiConstants.API_V1 + "/public")
@RequiredArgsConstructor
@Tag(name = "公开配置")
public class PublicConfigController {

    private static final String APP_NAME = "餐厅点餐系统";

    private static final String CURRENCY = "CNY";

    private static final String TIMEZONE = "Asia/Shanghai";

    private static final String MOCK_PAYMENT_HINT = "演示环境：订单金额按实展示，实际模拟扣款 ¥0.01";

    private final AppProperties appProperties;

    private final FileStorageFacade fileStorageFacade;

    @GetMapping("/config")
    @Operation(summary = "前端运行时配置")
    public Result<PublicConfigView> config() {
        List<String> allowedTypes = appProperties.getUpload().getAllowedContentTypes();
        PublicConfigView view = new PublicConfigView(
                APP_NAME,
                CURRENCY,
                TIMEZONE,
                appProperties.getDefaultMerchantId(),
                new PublicConfigView.MockPayment(true,
                        appProperties.getPayment().getMockChargeAmount(), CURRENCY, MOCK_PAYMENT_HINT),
                new PublicConfigView.Order(appProperties.getOrder().getPaymentTimeoutMinutes()),
                new PublicConfigView.Table(appProperties.getTable().getSessionIdleTimeoutMinutes(),
                        appProperties.getTable().isAllowManualInput()),
                new PublicConfigView.Storage(fileStorageFacade.type(), fileStorageFacade.uploadMode(),
                        fileStorageFacade.maxSizeMb(), allowedTypes),
                new PublicConfigView.Websocket(ApiConstants.WS_ENDPOINT, 10));
        return Result.success(view);
    }
}
