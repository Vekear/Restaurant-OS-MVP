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
import com.restaurant.storage.dto.FileConfirmRequest;
import com.restaurant.storage.mapper.FileObjectMapper;
import com.restaurant.storage.service.OssClientProvider;
import com.restaurant.storage.service.OssFileStorage;
import com.restaurant.storage.service.OssPostPolicyService;
import com.restaurant.storage.vo.FileObjectView;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * M-02：OSS 直传 confirm 的 HeadObject 存在性校验单元测试（无外网/无真实凭证）。
 *
 * <p>覆盖存在/不存在/HeadObject 异常/null 元数据四条路径，并验证失败时不落库、
 * 客户端总是 shutdown、日志不含 AccessKey。
 */
class OssFileStorageConfirmTest {

    private static final String SECRET = "SECRET-DEMO-DO-NOT-LEAK";

    private static final String OBJECT_KEY = "table/2026/10/0123456789abcdef0123456789abcdef.png";

    @Test
    void confirmChecksHeadObjectAndRegistersFile() {
        FileObjectMapper fileObjectMapper = mock(FileObjectMapper.class);
        when(fileObjectMapper.insert(any(FileObject.class))).thenAnswer(invocation -> {
            FileObject fileObject = invocation.getArgument(0);
            fileObject.setId(88L);
            fileObject.setCreatedAt(LocalDateTime.of(2026, 10, 28, 20, 0));
            return 1;
        });
        OSS ossClient = mock(OSS.class);
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(723L);
        when(ossClient.headObject("demo-bucket", OBJECT_KEY)).thenReturn(metadata);
        OssFileStorage storage = newStorage(fileObjectMapper, ossClient);

        FileObjectView view = storage.confirm(confirmRequest(), 7L);

        verify(ossClient).headObject("demo-bucket", OBJECT_KEY);
        verify(ossClient).shutdown();
        assertThat(view.id()).isEqualTo(88L);
        assertThat(view.bizType()).isEqualTo("table");
        assertThat(view.objectKey()).isEqualTo(OBJECT_KEY);
        assertThat(view.storageType()).isEqualTo("oss");
        assertThat(view.size()).isEqualTo(723L);
        assertThat(view.contentType()).isEqualTo("image/png");
        assertThat(view.url()).isEqualTo("https://cdn.example.com/" + OBJECT_KEY);

        ArgumentCaptor<FileObject> captor = ArgumentCaptor.forClass(FileObject.class);
        verify(fileObjectMapper).insert(captor.capture());
        assertThat(captor.getValue().getStorageType()).isEqualTo("oss");
        assertThat(captor.getValue().getUploaderId()).isEqualTo(7L);
    }

    @Test
    void missingObjectReturnsNotFoundWithoutRegistering() {
        FileObjectMapper fileObjectMapper = mock(FileObjectMapper.class);
        OSS ossClient = mock(OSS.class);
        OSSException noSuchKey = mock(OSSException.class);
        when(noSuchKey.getErrorCode()).thenReturn("NoSuchKey");
        when(noSuchKey.getMessage()).thenReturn("The specified key does not exist.");
        when(ossClient.headObject(eq("demo-bucket"), eq(OBJECT_KEY))).thenThrow(noSuchKey);
        OssFileStorage storage = newStorage(fileObjectMapper, ossClient);

        assertThatThrownBy(() -> storage.confirm(confirmRequest(), 7L))
                .isInstanceOfSatisfying(BizException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(ErrorCode.NOT_FOUND.getCode());
                    assertThat(e.getMessage()).contains("文件不存在");
                });
        verify(fileObjectMapper, never()).insert(any(FileObject.class));
        verify(ossClient).shutdown();
    }

    @Test
    void nullHeadObjectMetadataReturnsNotFound() {
        FileObjectMapper fileObjectMapper = mock(FileObjectMapper.class);
        OSS ossClient = mock(OSS.class);
        when(ossClient.headObject("demo-bucket", OBJECT_KEY)).thenReturn(null);
        OssFileStorage storage = newStorage(fileObjectMapper, ossClient);

        assertThatThrownBy(() -> storage.confirm(confirmRequest(), 7L))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.NOT_FOUND.getCode()));
        verify(fileObjectMapper, never()).insert(any(FileObject.class));
        verify(ossClient).shutdown();
    }

    @Test
    void headObjectFailureMapsToStorageErrorWithSafeWarning() {
        FileObjectMapper fileObjectMapper = mock(FileObjectMapper.class);
        OSS ossClient = mock(OSS.class);
        when(ossClient.headObject("demo-bucket", OBJECT_KEY))
                .thenThrow(new OSSException("simulated headObject failure"));
        OssFileStorage storage = newStorage(fileObjectMapper, ossClient);

        ListAppender<ILoggingEvent> logs = attachLogs();
        try {
            assertThatThrownBy(() -> storage.confirm(confirmRequest(), 7L))
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
        assertThat(text).contains("OSS 文件存在性校验失败")
                .contains(OBJECT_KEY)
                .doesNotContain(SECRET)
                .doesNotContain("AKID-DEMO");
    }

    private OssFileStorage newStorage(FileObjectMapper fileObjectMapper, OSS ossClient) {
        AppProperties appProperties = ossProperties();
        OssPostPolicyService policyService = new OssPostPolicyService(appProperties, new ObjectMapper());
        OssClientProvider provider = mock(OssClientProvider.class);
        when(provider.createClient()).thenReturn(ossClient);
        return new OssFileStorage(appProperties, policyService, fileObjectMapper, provider);
    }

    private FileConfirmRequest confirmRequest() {
        FileConfirmRequest request = new FileConfirmRequest();
        request.setBizType("table");
        request.setObjectKey(OBJECT_KEY);
        request.setSize(723L);
        request.setContentType("image/png");
        request.setOriginalName("table-A01-qr.png");
        return request;
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
