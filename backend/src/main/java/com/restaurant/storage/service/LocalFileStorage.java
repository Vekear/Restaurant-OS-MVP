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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * 本地磁盘存储（OSS 未配置时的兜底）：写入 {@code app.storage.local.root} 并登记 file_object。
 *
 * <p>安全约束：只允许 dish/merchant/avatar 业务类型；校验大小、MIME 白名单与文件头魔数；
 * 对象键完全由服务端生成，客户端传入路径被忽略。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LocalFileStorage implements FileStorageService {

    private final AppProperties appProperties;

    private final FileObjectMapper fileObjectMapper;

    @Override
    public String type() {
        return "local";
    }

    @Override
    public String uploadMode() {
        return "backend";
    }

    @Override
    public int maxSizeMb() {
        return (int) appProperties.getUpload().getMaxSize().toMegabytes();
    }

    @Override
    public FileObjectView store(MultipartFile file, String bizType, Long uploaderId) {
        String normalizedBizType = FileValidation.requireClientBizType(bizType);
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.FILE_INVALID, "上传文件不能为空");
        }
        long maxBytes = appProperties.getUpload().getMaxSize().toBytes();
        if (file.getSize() > maxBytes) {
            throw new BizException(ErrorCode.FILE_INVALID, "文件不能超过 " + maxSizeMb() + "MB");
        }
        String contentType = FileValidation.normalizeContentType(file.getContentType());
        List<String> allowedTypes = allowedContentTypes();
        if (!allowedTypes.contains(contentType)) {
            throw new BizException(ErrorCode.FILE_INVALID, "仅支持 " + String.join("、", allowedTypes));
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            log.error("读取上传文件失败, bizType={}", normalizedBizType, e);
            throw new BizException(ErrorCode.STORAGE_ERROR, "文件读取失败，请重试");
        }
        FileValidation.verifyMagicNumber(bytes, contentType);
        String extension = FileValidation.extensionForContentType(contentType);
        String objectKey = normalizedBizType + "/" + FileValidation.monthPath() + "/"
                + FileValidation.uuid() + "." + extension;
        Path root = localRoot();
        Path target = root.resolve(objectKey).normalize();
        if (!target.startsWith(root)) {
            throw new BizException(ErrorCode.FILE_INVALID, "非法的对象键");
        }
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, bytes);
        } catch (IOException e) {
            log.error("写入本地文件失败, objectKey={}", objectKey, e);
            throw new BizException(ErrorCode.STORAGE_ERROR, "文件保存失败，请重试");
        }

        FileObject fileObject = new FileObject();
        fileObject.setBizType(normalizedBizType);
        fileObject.setObjectKey(objectKey);
        fileObject.setUrl(FileValidation.normalizeUrlPrefix(appProperties.getStorage().getLocal().getUrlPrefix())
                + "/" + objectKey);
        fileObject.setOriginalName(safeOriginalName(file.getOriginalFilename()));
        fileObject.setContentType(contentType);
        fileObject.setSize((long) bytes.length);
        fileObject.setStorageType("local");
        fileObject.setUploaderId(uploaderId);
        try {
            fileObjectMapper.insert(fileObject);
        } catch (RuntimeException e) {
            try {
                Files.deleteIfExists(target);
            } catch (IOException cleanupError) {
                log.warn("回滚本地文件失败, objectKey={}", objectKey, cleanupError);
            }
            throw e;
        }
        log.info("本地文件已保存, objectKey={}, size={}, uploaderId={}", objectKey, bytes.length, uploaderId);
        return toView(fileObject);
    }

    @Override
    public FilePolicyView policy(String bizType, String ext) {
        FileValidation.requireAnyBizType(bizType);
        FileValidation.requireExtension(ext);
        return FilePolicyView.local("/api/v1/files/upload", maxSizeMb(), allowedContentTypes());
    }

    @Override
    public FileObjectView confirm(FileConfirmRequest request, Long uploaderId) {
        throw new BizException(ErrorCode.STORAGE_ERROR, "当前为本地存储模式，无需确认直传结果");
    }

    private List<String> allowedContentTypes() {
        List<String> configured = appProperties.getUpload().getAllowedContentTypes();
        if (configured == null || configured.isEmpty()) {
            return FileValidation.defaultContentTypes();
        }
        return configured.stream()
                .map(FileValidation::normalizeContentType)
                .toList();
    }

    private Path localRoot() {
        return Paths.get(appProperties.getStorage().getLocal().getRoot()).toAbsolutePath().normalize();
    }

    private static String safeOriginalName(String originalName) {
        if (originalName == null || originalName.isBlank()) {
            return null;
        }
        String sanitized = originalName.replace('\\', '/');
        int slash = sanitized.lastIndexOf('/');
        if (slash >= 0) {
            sanitized = sanitized.substring(slash + 1);
        }
        sanitized = sanitized.trim();
        if (sanitized.isEmpty()) {
            return null;
        }
        return sanitized.length() > 255 ? sanitized.substring(0, 255) : sanitized;
    }

    private static FileObjectView toView(FileObject fileObject) {
        return new FileObjectView(fileObject.getId(), fileObject.getBizType(), fileObject.getObjectKey(),
                fileObject.getUrl(), fileObject.getStorageType(),
                fileObject.getSize() == null ? 0L : fileObject.getSize(),
                fileObject.getContentType(), fileObject.getOriginalName(), fileObject.getCreatedAt());
    }

}
