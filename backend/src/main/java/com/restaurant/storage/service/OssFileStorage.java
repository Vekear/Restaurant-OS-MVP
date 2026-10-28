package com.restaurant.storage.service;

import com.restaurant.common.api.ErrorCode;
import com.restaurant.common.config.AppProperties;
import com.restaurant.common.exception.BizException;
import com.restaurant.storage.FileObject;
import com.restaurant.storage.dto.FileConfirmRequest;
import com.restaurant.storage.mapper.FileObjectMapper;
import com.restaurant.storage.vo.FileObjectView;
import com.restaurant.storage.vo.FilePolicyView;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.model.ObjectMetadata;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.util.List;

/**
 * OSS 直传存储：签发 Post Policy，前端直传后调用 confirm 登记 file_object。
 *
 * <p>直传模式不接受后端 multipart 上传（避免绕过 OSS 流量与配额策略）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OssFileStorage implements FileStorageService {

    private final AppProperties appProperties;

    private final OssPostPolicyService ossPostPolicyService;

    private final FileObjectMapper fileObjectMapper;

    private final OssClientProvider ossClientProvider;

    @Override
    public String type() {
        return "oss";
    }

    @Override
    public String uploadMode() {
        return "direct";
    }

    @Override
    public int maxSizeMb() {
        return (int) appProperties.getUpload().getMaxSize().toMegabytes();
    }

    @Override
    public FileObjectView store(MultipartFile file, String bizType, Long uploaderId) {
        throw new BizException(ErrorCode.STORAGE_ERROR, "OSS 直传模式不支持后端上传，请先获取上传策略");
    }

    /**
     * 服务端生成文件（如桌台二维码 PNG）直接 putObject 到 OSS。
     *
     * <p>对象键规则与 local 保持一致：{@code {bizType}/yyyy/MM/{uuid}.{ext}}；写入成功后才登记
     * file_object，登记失败会尽力删除刚上传的对象，避免产生孤儿文件。异常只暴露 objectKey 与
     * 错误摘要，不输出 AccessKey。
     */
    @Override
    public FileObjectView storeBytes(byte[] bytes, String contentType, String originalName,
                                     String bizType, Long uploaderId) {
        String normalizedBizType = FileValidation.requireAnyBizType(bizType);
        if (!ossPostPolicyService.configured()) {
            throw new BizException(ErrorCode.STORAGE_ERROR, "OSS 未配置，已降级本地存储");
        }
        if (bytes == null || bytes.length == 0) {
            throw new BizException(ErrorCode.FILE_INVALID, "文件内容不能为空");
        }
        long maxBytes = appProperties.getUpload().getMaxSize().toBytes();
        if (bytes.length > maxBytes) {
            throw new BizException(ErrorCode.FILE_INVALID, "文件不能超过 " + maxSizeMb() + "MB");
        }
        String normalizedType = FileValidation.normalizeContentType(contentType);
        List<String> allowedTypes = allowedContentTypes();
        if (!allowedTypes.contains(normalizedType)) {
            throw new BizException(ErrorCode.FILE_INVALID, "仅支持 " + String.join("、", allowedTypes));
        }
        FileValidation.verifyMagicNumber(bytes, normalizedType);
        String extension = FileValidation.extensionForContentType(normalizedType);
        String objectKey = normalizedBizType + "/" + FileValidation.monthPath() + "/"
                + FileValidation.uuid() + "." + extension;
        AppProperties.Storage.Oss oss = appProperties.getStorage().getOss();
        OSS client = null;
        try {
            client = ossClientProvider.createClient();
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentType(normalizedType);
            metadata.setContentLength(bytes.length);
            client.putObject(oss.getBucket(), objectKey,
                    new ByteArrayInputStream(bytes), metadata);
        } catch (RuntimeException e) {
            log.warn("OSS 服务端上传失败, objectKey={}, error={}", objectKey, e.getMessage());
            throw new BizException(ErrorCode.STORAGE_ERROR, "文件上传到 OSS 失败，请重试");
        } finally {
            shutdownQuietly(client);
        }

        FileObject fileObject = new FileObject();
        fileObject.setBizType(normalizedBizType);
        fileObject.setObjectKey(objectKey);
        fileObject.setUrl(ossPostPolicyService.publicHost() + "/" + objectKey);
        fileObject.setOriginalName(safeOriginalName(originalName));
        fileObject.setContentType(normalizedType);
        fileObject.setSize((long) bytes.length);
        fileObject.setStorageType("oss");
        fileObject.setUploaderId(uploaderId);
        try {
            fileObjectMapper.insert(fileObject);
        } catch (RuntimeException e) {
            deleteObjectQuietly(objectKey);
            throw e;
        }
        log.info("OSS 文件已保存(服务端生成), objectKey={}, size={}", objectKey, bytes.length);
        return new FileObjectView(fileObject.getId(), fileObject.getBizType(), fileObject.getObjectKey(),
                fileObject.getUrl(), fileObject.getStorageType(), fileObject.getSize(),
                fileObject.getContentType(), fileObject.getOriginalName(), fileObject.getCreatedAt());
    }

    @Override
    public FilePolicyView policy(String bizType, String ext) {
        String normalizedBizType = FileValidation.requireAnyBizType(bizType);
        OssPostPolicyService.Policy policy = ossPostPolicyService.buildPolicy(normalizedBizType, ext);
        return FilePolicyView.oss(policy.host(), policy.dir(), policy.objectKey(), policy.policy(),
                policy.signature(), policy.accessKeyId(), policy.expireAt(), maxSizeMb(),
                allowedContentTypes(), "200");
    }

    /**
     * 登记前端直传完成的文件。
     *
     * <p>M-02：登记前先 HeadObject 校验对象确实存在（不能只信前端回传的 objectKey/size）；
     * 对象不存在返回 40401，SDK/网络异常返回 STORAGE_ERROR，且日志不含 AccessKey。
     */
    @Override
    public FileObjectView confirm(FileConfirmRequest request, Long uploaderId) {
        String bizType = FileValidation.requireAnyBizType(request.getBizType());
        String objectKey = request.getObjectKey() == null ? "" : request.getObjectKey().trim();
        if (!objectKey.startsWith(bizType + "/") || objectKey.contains("..")
                || objectKey.length() > 255) {
            throw new BizException(ErrorCode.FILE_INVALID, "objectKey 必须以业务目录开头且长度不超过 255");
        }
        long maxBytes = appProperties.getUpload().getMaxSize().toBytes();
        if (request.getSize() == null || request.getSize() < 1 || request.getSize() > maxBytes) {
            throw new BizException(ErrorCode.FILE_INVALID, "文件不能超过 " + maxSizeMb() + "MB");
        }
        String contentType = FileValidation.normalizeContentType(request.getContentType());
        if (!allowedContentTypes().contains(contentType)) {
            throw new BizException(ErrorCode.FILE_INVALID, "仅支持 " + String.join("、", allowedContentTypes()));
        }
        String originalName = request.getOriginalName();
        if (originalName != null && originalName.length() > 120) {
            originalName = originalName.substring(0, 120);
        }
        verifyObjectExists(objectKey);
        FileObject fileObject = new FileObject();
        fileObject.setBizType(bizType);
        fileObject.setObjectKey(objectKey);
        fileObject.setUrl(ossPostPolicyService.publicHost() + "/" + objectKey);
        fileObject.setOriginalName(originalName);
        fileObject.setContentType(contentType);
        fileObject.setSize(request.getSize());
        fileObject.setStorageType("oss");
        fileObject.setUploaderId(uploaderId);
        fileObjectMapper.insert(fileObject);
        log.info("OSS 直传已确认, objectKey={}, size={}, uploaderId={}", objectKey, request.getSize(), uploaderId);
        return new FileObjectView(fileObject.getId(), fileObject.getBizType(), fileObject.getObjectKey(),
                fileObject.getUrl(), fileObject.getStorageType(), fileObject.getSize(),
                fileObject.getContentType(), fileObject.getOriginalName(), fileObject.getCreatedAt());
    }

    private void verifyObjectExists(String objectKey) {
        OSS client = null;
        ObjectMetadata metadata;
        try {
            client = ossClientProvider.createClient();
            metadata = client.headObject(appProperties.getStorage().getOss().getBucket(), objectKey);
        } catch (OSSException e) {
            if (isObjectMissing(e)) {
                throw new BizException(ErrorCode.NOT_FOUND, "文件不存在或上传未完成，请重新上传");
            }
            log.warn("OSS 文件存在性校验失败, objectKey={}, errorCode={}, error={}",
                    objectKey, e.getErrorCode(), e.getMessage());
            throw new BizException(ErrorCode.STORAGE_ERROR, "OSS 存储校验失败，请稍后重试");
        } catch (RuntimeException e) {
            log.warn("OSS 文件存在性校验失败, objectKey={}, error={}", objectKey, e.getMessage());
            throw new BizException(ErrorCode.STORAGE_ERROR, "OSS 存储校验失败，请稍后重试");
        } finally {
            shutdownQuietly(client);
        }
        if (metadata == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "文件不存在或上传未完成，请重新上传");
        }
    }

    private boolean isObjectMissing(OSSException exception) {
        String errorCode = exception.getErrorCode();
        if (errorCode != null
                && ("NoSuchKey".equalsIgnoreCase(errorCode) || "NotFound".equalsIgnoreCase(errorCode))) {
            return true;
        }
        String message = exception.getMessage();
        return message != null && message.contains("NoSuchKey");
    }

    private void deleteObjectQuietly(String objectKey) {
        OSS client = null;
        try {
            client = ossClientProvider.createClient();
            client.deleteObject(appProperties.getStorage().getOss().getBucket(), objectKey);
        } catch (RuntimeException e) {
            log.warn("回滚 OSS 对象失败, objectKey={}, error={}", objectKey, e.getMessage());
        } finally {
            shutdownQuietly(client);
        }
    }

    private void shutdownQuietly(OSS client) {
        if (client == null) {
            return;
        }
        try {
            client.shutdown();
        } catch (RuntimeException e) {
            log.warn("关闭 OSS 客户端失败: {}", e.getMessage());
        }
    }

    private String safeOriginalName(String originalName) {
        if (originalName == null || originalName.isBlank()) {
            return "file";
        }
        String normalized = originalName.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        if (slash >= 0) {
            normalized = normalized.substring(slash + 1);
        }
        normalized = normalized.trim();
        if (normalized.isEmpty()) {
            normalized = "file";
        }
        return normalized.length() > 120 ? normalized.substring(0, 120) : normalized;
    }

    private List<String> allowedContentTypes() {
        List<String> configured = appProperties.getUpload().getAllowedContentTypes();
        if (configured == null || configured.isEmpty()) {
            return FileValidation.defaultContentTypes();
        }
        return configured.stream().map(FileValidation::normalizeContentType).toList();
    }
}
