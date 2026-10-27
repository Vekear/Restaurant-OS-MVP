package com.restaurant.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurant.common.config.AppProperties;
import com.restaurant.storage.service.FileStorageFacade;
import com.restaurant.storage.service.LocalFileStorage;
import com.restaurant.storage.service.OssFileStorage;
import com.restaurant.storage.service.OssPostPolicyService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 存储策略解析单元测试：local / oss / oss 配置缺失降级 local。
 */
class FileStorageFacadeTest {

    @Test
    void localByDefault() {
        AppProperties appProperties = new AppProperties();
        assertThat(facade(appProperties).type()).isEqualTo("local");
        assertThat(facade(appProperties).uploadMode()).isEqualTo("backend");
    }

    @Test
    void ossConfiguredSwitchesToDirectUpload() {
        AppProperties appProperties = new AppProperties();
        configureOss(appProperties);
        FileStorageFacade facade = facade(appProperties);
        assertThat(facade.type()).isEqualTo("oss");
        assertThat(facade.uploadMode()).isEqualTo("direct");
    }

    @Test
    void ossWithoutCredentialsDowngradesToLocal() {
        AppProperties appProperties = new AppProperties();
        appProperties.getStorage().setType("oss");
        FileStorageFacade facade = facade(appProperties);
        assertThat(facade.type()).isEqualTo("local");
        assertThat(facade.uploadMode()).isEqualTo("backend");
    }

    private FileStorageFacade facade(AppProperties appProperties) {
        OssPostPolicyService policyService = new OssPostPolicyService(appProperties, new ObjectMapper());
        LocalFileStorage local = new LocalFileStorage(appProperties, null);
        OssFileStorage oss = new OssFileStorage(appProperties, policyService, null);
        FileStorageFacade facade = new FileStorageFacade(appProperties, local, policyService, oss);
        facade.initialize();
        return facade;
    }

    private void configureOss(AppProperties appProperties) {
        appProperties.getStorage().setType("oss");
        appProperties.getStorage().getOss().setEndpoint("oss-cn-hangzhou.aliyuncs.com");
        appProperties.getStorage().getOss().setBucket("demo-bucket");
        appProperties.getStorage().getOss().setAccessKeyId("AKID-DEMO");
        appProperties.getStorage().getOss().setAccessKeySecret("SECRET-DEMO");
    }
}
