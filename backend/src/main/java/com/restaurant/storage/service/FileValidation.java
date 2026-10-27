package com.restaurant.storage.service;

import com.restaurant.common.api.ErrorCode;
import com.restaurant.common.exception.BizException;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 文件校验与对象键生成（local / oss 共用）。
 */
public final class FileValidation {

    public static final Set<String> CLIENT_BIZ_TYPES = Set.of("dish", "merchant", "avatar");

    public static final Set<String> ALL_BIZ_TYPES = Set.of("dish", "merchant", "avatar", "table");

    public static final Set<String> EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");

    private static final Map<String, String> EXTENSION_BY_CONTENT_TYPE = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp");

    private static final Map<String, String> CONTENT_TYPE_BY_EXTENSION = Map.of(
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "png", "image/png",
            "webp", "image/webp");

    private static final DateTimeFormatter MONTH_PATH = DateTimeFormatter.ofPattern("yyyy/MM");

    private FileValidation() {
    }

    /** 客户端上传允许的业务类型。 */
    public static String requireClientBizType(String bizType) {
        if (bizType == null || !CLIENT_BIZ_TYPES.contains(bizType)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "bizType 只能为 dish/merchant/avatar");
        }
        return bizType;
    }

    /** policy 允许的业务类型（含内部 table）。 */
    public static String requireAnyBizType(String bizType) {
        if (bizType == null || !ALL_BIZ_TYPES.contains(bizType)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "bizType 只能为 dish/merchant/avatar/table");
        }
        return bizType;
    }

    public static String requireExtension(String ext) {
        String normalized = ext == null ? "" : ext.toLowerCase(Locale.ROOT);
        if (!EXTENSIONS.contains(normalized)) {
            throw new BizException(ErrorCode.FILE_INVALID, "仅支持 jpg/jpeg/png/webp 扩展名");
        }
        return normalized;
    }

    public static String contentTypeForExtension(String ext) {
        return CONTENT_TYPE_BY_EXTENSION.get(ext.toLowerCase(Locale.ROOT));
    }

    public static String normalizeContentType(String contentType) {
        return contentType == null ? "" : contentType.toLowerCase(Locale.ROOT).trim();
    }

    public static String extensionForContentType(String contentType) {
        return EXTENSION_BY_CONTENT_TYPE.get(normalizeContentType(contentType));
    }

    /** 校验文件头魔数与声明的 MIME 是否一致。 */
    public static void verifyMagicNumber(byte[] bytes, String contentType) {
        boolean matched = switch (normalizeContentType(contentType)) {
            case "image/jpeg" -> bytes.length >= 3
                    && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF;
            case "image/png" -> bytes.length >= 8
                    && (bytes[0] & 0xFF) == 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4E && bytes[3] == 0x47
                    && bytes[4] == 0x0D && bytes[5] == 0x0A && bytes[6] == 0x1A && bytes[7] == 0x0A;
            case "image/webp" -> bytes.length >= 12
                    && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                    && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P';
            default -> false;
        };
        if (!matched) {
            throw new BizException(ErrorCode.FILE_INVALID, "文件内容与声明的类型不匹配");
        }
    }

    /** 当天日期路径 yyyy/MM。 */
    public static String monthPath() {
        return LocalDate.now().format(MONTH_PATH);
    }

    public static String uuid() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public static String normalizeUrlPrefix(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return "/files";
        }
        String normalized = prefix.trim();
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    /** 允许的 MIME 白名单（与配置一致时的兜底常量）。 */
    public static List<String> defaultContentTypes() {
        return List.of("image/jpeg", "image/png", "image/webp");
    }
}
