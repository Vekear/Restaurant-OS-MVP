package com.restaurant.storage.vo;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * 上传策略视图（{@code docs/02-api.md §2.8}）。
 *
 * <p>local 模式返回 {@code uploadMode=backend + uploadUrl}；oss 模式返回 PostPolicy 表单字段。
 * {@code accessKeyId} 是 OSS 协议要求的公开密钥标识（非 Secret），AccessKeySecret 永不下发。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FilePolicyView(String storageType,
                             String uploadMode,
                             String uploadUrl,
                             String host,
                             String dir,
                             String objectKey,
                             String policy,
                             String signature,
                             String accessKeyId,
                             String expireAt,
                             Integer maxSizeMb,
                             List<String> allowedTypes,
                             String successActionStatus) {

    public static FilePolicyView local(String uploadUrl, int maxSizeMb, List<String> allowedTypes) {
        return new FilePolicyView("local", "backend", uploadUrl, null, null, null, null, null, null, null,
                maxSizeMb, allowedTypes, null);
    }

    public static FilePolicyView oss(String host, String dir, String objectKey, String policy, String signature,
                                     String accessKeyId, String expireAt, int maxSizeMb,
                                     List<String> allowedTypes, String successActionStatus) {
        return new FilePolicyView("oss", "direct", null, host, dir, objectKey, policy, signature, accessKeyId,
                expireAt, maxSizeMb, allowedTypes, successActionStatus);
    }
}
