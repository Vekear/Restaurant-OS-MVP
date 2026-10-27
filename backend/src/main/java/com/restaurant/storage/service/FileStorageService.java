package com.restaurant.storage.service;

import com.restaurant.storage.dto.FileConfirmRequest;
import com.restaurant.storage.vo.FileObjectView;
import com.restaurant.storage.vo.FilePolicyView;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件存储抽象（{@code docs/01-architecture.md §15}）。
 *
 * <p>local：后台上传（multipart）→ 本地磁盘 + file_object；
 * oss：Post Policy 直传（前端直传 OSS）→ /files/confirm 登记 file_object。
 * AccessKeySecret 只在服务端参与签名，永不出现在任何响应中。
 */
public interface FileStorageService {

    /** local / oss。 */
    String type();

    /** backend（后台上传）/ direct（前端直传）。 */
    String uploadMode();

    /** 单文件大小上限（MB）。 */
    int maxSizeMb();

    FileObjectView store(MultipartFile file, String bizType, Long uploaderId);

    /**
     * 服务端生成内容并落库（如桌台二维码 PNG，仅 local 模式支持）。
     *
     * @param bytes        文件内容
     * @param contentType  MIME（image/png 等）
     * @param originalName 原始文件名（可空）
     * @param bizType      dish/merchant/avatar/table
     * @param uploaderId   上传用户（系统生成可为 {@code null}）
     * @return 文件对象视图
     */
    FileObjectView storeBytes(byte[] bytes, String contentType, String originalName, String bizType, Long uploaderId);

    FilePolicyView policy(String bizType, String ext);

    FileObjectView confirm(FileConfirmRequest request, Long uploaderId);
}
