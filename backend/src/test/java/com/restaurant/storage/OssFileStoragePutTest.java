package com.restaurant.storage;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.model.ObjectMetadata;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurant.common.api.ErrorCode;
import com.restaurant.common.config.AppProperties;
import com.restaurant.common.exception.BizException;
import com.restaurant.storage.mapper.FileObjectMapper;
import com.restaurant.storage.service.OssClientProvider;
import com.restaurant.storage.service.OssFileStorage;
import com.restaurant.storage.service.OssPostPolicyService;
import com.restaurant.storage.vo.FileObjectView;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OSS 服务端 putObject 单元测试（无外网/无真实凭证）。
 *
 * <p>用 mock 的 {@link OssClientProvider} 与 {@link OSS} 验证：objectKey 规则、元数据登记、
 * 失败映射为 STORAGE_ERROR 且不落库、AccessKeySecret 不出现在日志中。
 */
class OssFileStoragePutTest {

    private static final String SECRET = "SECRET-DEMO-DO-NOT-LEAK";

    /** 真实 PNG 文件头（magic number 校验用，无需完整图片）。 */
    private static final byte[] PNG_BYTES = {
            (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A,
            0x00, 0x00, 0x00, 0x0D, 'I', 'H', 'D', 'R'
    };

    @Test
    void storeBytesPutsObjectAndRegistersOssMetadata() {
        AppProperties appProperties = ossProperties();
        OssPostPolicyService policyService = new OssPostPolicyService(appProperties, new ObjectMapper());
        FileObjectMapper fileObjectMapper = mock(FileObjectMapper.class);
        when(fileObjectMapper.insert(any(FileObject.class))).thenAnswer(invocation -> {
            FileObject fileObject = invocation.getArgument(0);
            fileObject.setId(42L);
            fileObject.setCreatedAt(LocalDateTime.of(2026, 10, 28, 19, 0));
            return 1;
        });
        OSS ossClient = mock(OSS.class);
        OssClientProvider provider = mock(OssClientProvider.class);
        when(provider.createClient()).thenReturn(ossClient);
        OssFileStorage storage = new OssFileStorage(appProperties, policyService, fileObjectMapper, provider);

        ListAppender<ILoggingEvent> logs = attachLogs();
        FileObjectView view;
        try {
            view = storage.storeBytes(PNG_BYTES, "image/png", "table-A01-qr.png", "table", 7L);
        } finally {
            detachLogs(logs);
        }

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<ObjectMetadata> metadataCaptor = ArgumentCaptor.forClass(ObjectMetadata.class);
        verify(ossClient).putObject(eq("demo-bucket"), keyCaptor.capture(),
                any(InputStream.class), metadataCaptor.capture());
        verify(ossClient).shutdown();
        String objectKey = keyCaptor.getValue();
        assertThat(objectKey).matches("table/\\d{4}/\\d{2}/[0-9a-f]{32}\\.png");
        assertThat(metadataCaptor.getValue().getContentType()).isEqualTo("image/png");
        assertThat(metadataCaptor.getValue().getContentLength()).isEqualTo(PNG_BYTES.length);

        assertThat(view.id()).isEqualTo(42L);
        assertThat(view.bizType()).isEqualTo("table");
        assertThat(view.objectKey()).isEqualTo(objectKey);
        assertThat(view.storageType()).isEqualTo("oss");
        assertThat(view.size()).isEqualTo(PNG_BYTES.length);
        assertThat(view.contentType()).isEqualTo("image/png");
        assertThat(view.originalName()).isEqualTo("table-A01-qr.png");
        assertThat(view.url()).isEqualTo("https://cdn.example.com/" + objectKey);

        ArgumentCaptor<FileObject> entityCaptor = ArgumentCaptor.forClass(FileObject.class);
        verify(fileObjectMapper).insert(entityCaptor.capture());
        assertThat(entityCaptor.getValue().getStorageType()).isEqualTo("oss");
        assertThat(entityCaptor.getValue().getObjectKey()).isEqualTo(objectKey);
        assertThat(logsText(logs)).doesNotContain(SECRET).doesNotContain("AKID-DEMO");
    }

    @Test
    void ossFailureMapsToStorageErrorAndSkipsInsert() {
        AppProperties appProperties = ossProperties();
        OssPostPolicyService policyService = new OssPostPolicyService(appProperties, new ObjectMapper());
        FileObjectMapper fileObjectMapper = mock(FileObjectMapper.class);
        OSS ossClient = mock(OSS.class);
        when(ossClient.putObject(anyString(), anyString(), any(InputStream.class), any(ObjectMetadata.class)))
                .thenThrow(new OSSException("simulated oss failure"));
        OssClientProvider provider = mock(OssClientProvider.class);
        when(provider.createClient()).thenReturn(ossClient);
        OssFileStorage storage = new OssFileStorage(appProperties, policyService, fileObjectMapper, provider);

        ListAppender<ILoggingEvent> logs = attachLogs();
        try {
            assertThatThrownBy(() -> storage.storeBytes(PNG_BYTES, "image/png", "qr.png", "table", 7L))
                    .isInstanceOfSatisfying(BizException.class, e -> {
                        assertThat(e.getCode()).isEqualTo(ErrorCode.STORAGE_ERROR.getCode());
                        assertThat(e.getMessage()).contains("OSS");
                    });
        } finally {
            detachLogs(logs);
        }
        verify(fileObjectMapper, never()).insert(any(FileObject.class));
        verify(ossClient).shutdown();
        String text = logsText(logs);
        assertThat(text).contains("OSS 服务端上传失败")
                .containsPattern("objectKey=table/\\d{4}/\\d{2}/[0-9a-f]{32}\\.png")
                .doesNotContain(SECRET)
                .doesNotContain("AKID-DEMO");
    }

    @Test
    void missingCredentialsRejectedBeforeCreatingClient() {
        AppProperties appProperties = new AppProperties();
        appProperties.getStorage().setType("oss");
        OssPostPolicyService policyService = new OssPostPolicyService(appProperties, new ObjectMapper());
        FileObjectMapper fileObjectMapper = mock(FileObjectMapper.class);
        OssClientProvider provider = mock(OssClientProvider.class);
        OssFileStorage storage = new OssFileStorage(appProperties, policyService, fileObjectMapper, provider);

        assertThatThrownBy(() -> storage.storeBytes(PNG_BYTES, "image/png", "qr.png", "table", 7L))
                .isInstanceOfSatisfying(BizException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(ErrorCode.STORAGE_ERROR.getCode());
                    assertThat(e.getMessage()).contains("未配置");
                });
        verify(provider, never()).createClient();
        verify(fileObjectMapper, never()).insert(any(FileObject.class));
    }

    @Test
    void invalidMagicNumberRejectedWithoutUpload() {
        AppProperties appProperties = ossProperties();
        OssPostPolicyService policyService = new OssPostPolicyService(appProperties, new ObjectMapper());
        FileObjectMapper fileObjectMapper = mock(FileObjectMapper.class);
        OssClientProvider provider = mock(OssClientProvider.class);
        OssFileStorage storage = new OssFileStorage(appProperties, policyService, fileObjectMapper, provider);

        assertThatThrownBy(() -> storage.storeBytes(new byte[]{1, 2, 3}, "image/png", "qr.png", "table", 7L))
                .isInstanceOfSatisfying(BizException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(ErrorCode.FILE_INVALID.getCode());
                    assertThat(e.getMessage()).contains("文件内容");
                });
        verify(provider, never()).createClient();
        verify(fileObjectMapper, never()).insert(any(FileObject.class));
    }

    private AppProperties ossProperties() {
        AppProperties appProperties = new AppProperties();
        appProperties.getStorage().setType("oss");
        AppProperties.Storage.Oss oss = appProperties.getStorage().getOss();
        oss.setEndpoint("oss-cn-hangzhou.aliyuncs.com");
        oss.setBucket("demo-bucket");
        oss.setAccessKeyId("AKID-DEMO");
        oss.setAccessKeySecret(SECRET);
        oss.setPublicBaseUrl("https://cdn.example.com/");
        return appProperties;
    }

    private ListAppender<ILoggingEvent> attachLogs() {
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        ((Logger) LoggerFactory.getLogger(OssFileStorage.class)).addAppender(appender);
        return appender;
    }

    private void detachLogs(ListAppender<ILoggingEvent> appender) {
        ((Logger) LoggerFactory.getLogger(OssFileStorage.class)).detachAppender(appender);
    }

    private String logsText(ListAppender<ILoggingEvent> appender) {
        StringBuilder builder = new StringBuilder();
        for (ILoggingEvent event : appender.list) {
            builder.append(event.getFormattedMessage()).append('\n');
        }
        return builder.toString();
    }
}
