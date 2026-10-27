package com.restaurant.storage.service;

import com.restaurant.common.api.ErrorCode;
import com.restaurant.common.config.AppProperties;
import com.restaurant.common.exception.BizException;
import com.restaurant.storage.FileObject;
import com.restaurant.storage.dto.FileConfirmRequest;
import com.restaurant.storage.mapper.FileObjectMapper;
import com.restaurant.storage.vo.FileObjectView;
import com.restaurant.storage.vo.FilePolicyView;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

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

    @Override
    public FilePolicyView policy(String bizType, String ext) {
        String normalizedBizType = FileValidation.requireAnyBizType(bizType);
        OssPostPolicyService.Policy policy = ossPostPolicyService.buildPolicy(normalizedBizType, ext);
        return FilePolicyView.oss(policy.host(), policy.dir(), policy.objectKey(), policy.policy(),
                policy.signature(), policy.accessKeyId(), policy.expireAt(), maxSizeMb(),
                allowedContentTypes(), "200");
    }

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

    private List<String> allowedContentTypes() {
        List<String> configured = appProperties.getUpload().getAllowedContentTypes();
        if (configured == null || configured.isEmpty()) {
            return FileValidation.defaultContentTypes();
        }
        return configured.stream().map(FileValidation::normalizeContentType).toList();
    }
}
