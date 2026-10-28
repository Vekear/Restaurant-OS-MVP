package com.restaurant.storage.service;

import com.aliyun.oss.ClientBuilderConfiguration;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.restaurant.common.config.AppProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * OSS 客户端工厂：集中创建带超时配置的 {@link OSS} 客户端。
 *
 * <p>单独抽出以便在无外网/无凭证环境下用 mock 客户端验证服务端 putObject 调用路径
 * （见 {@code OssFileStoragePutTest}）。AccessKey 仅从后端配置读取，绝不写入日志。
 */
@Component
@RequiredArgsConstructor
public class OssClientProvider {

    /** 建立连接超时（秒）。 */
    private static final int CONNECTION_TIMEOUT_SECONDS = 5;

    /** 读写超时（秒）。 */
    private static final int SOCKET_TIMEOUT_SECONDS = 15;

    /** 连接池上限。 */
    private static final int MAX_CONNECTIONS = 32;

    private final AppProperties appProperties;

    /** 创建一次性的 OSS 客户端，调用方负责 {@link OSS#shutdown()}。 */
    public OSS createClient() {
        AppProperties.Storage.Oss oss = appProperties.getStorage().getOss();
        ClientBuilderConfiguration configuration = new ClientBuilderConfiguration();
        configuration.setConnectionTimeout(CONNECTION_TIMEOUT_SECONDS);
        configuration.setSocketTimeout(SOCKET_TIMEOUT_SECONDS);
        configuration.setMaxConnections(MAX_CONNECTIONS);
        return new OSSClientBuilder().build(
                oss.getEndpoint(), oss.getAccessKeyId(), oss.getAccessKeySecret(), configuration);
    }
}
