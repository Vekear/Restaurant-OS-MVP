package com.restaurant.storage.service;

import com.restaurant.common.config.AppProperties;
import com.restaurant.storage.dto.FileConfirmRequest;
import com.restaurant.storage.vo.FileObjectView;
import com.restaurant.storage.vo.FilePolicyView;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.Locale;

/**
 * 存储策略解析：按 {@code app.storage.type} + OSS 配置完整性选择 local / oss。
 *
 * <p>启动时输出一行存储模式日志（OM-09 AC1）：
 * <ul>
 *   <li>{@code storage.type=oss} — OSS 配置齐全；</li>
 *   <li>{@code storage.type=local (OSS 未配置，已降级本地存储)} — 配置为 oss 但关键项缺失；</li>
 *   <li>{@code storage.type=local} — 显式配置为本地存储。</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FileStorageFacade implements FileStorageService {

    private final AppProperties appProperties;

    private final LocalFileStorage localFileStorage;

    private final OssPostPolicyService ossPostPolicyService;

    private final OssFileStorage ossFileStorage;

    private FileStorageService delegate;

    @PostConstruct
    public void initialize() {
        String configuredType = appProperties.getStorage().getType() == null
                ? "local"
                : appProperties.getStorage().getType().trim().toLowerCase(Locale.ROOT);
        boolean ossConfigured = ossPostPolicyService.configured();
        if ("oss".equals(configuredType) && ossConfigured) {
            delegate = ossFileStorage;
            log.info("storage.type=oss (bucket={})", appProperties.getStorage().getOss().getBucket());
        } else if ("oss".equals(configuredType)) {
            delegate = localFileStorage;
            log.warn("storage.type=local (OSS 未配置，已降级本地存储)");
        } else {
            delegate = localFileStorage;
            log.info("storage.type=local (root={})", appProperties.getStorage().getLocal().getRoot());
        }
    }

    @Override
    public String type() {
        return delegate.type();
    }

    @Override
    public String uploadMode() {
        return delegate.uploadMode();
    }

    @Override
    public int maxSizeMb() {
        return delegate.maxSizeMb();
    }

    @Override
    public FileObjectView store(MultipartFile file, String bizType, Long uploaderId) {
        return delegate.store(file, bizType, uploaderId);
    }

    @Override
    public FilePolicyView policy(String bizType, String ext) {
        return delegate.policy(bizType, ext);
    }

    @Override
    public FileObjectView confirm(FileConfirmRequest request, Long uploaderId) {
        return delegate.confirm(request, uploaderId);
    }
}
