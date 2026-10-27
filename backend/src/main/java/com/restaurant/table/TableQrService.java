package com.restaurant.table;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.restaurant.common.api.ErrorCode;
import com.restaurant.common.config.AppProperties;
import com.restaurant.common.exception.BizException;
import com.restaurant.storage.service.FileStorageFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 桌台二维码签名与图片生成（{@code docs/01-architecture.md §10}、ADR-0007）。
 *
 * <p>签名：{@code BASE64URL(HMAC_SHA256(qrSecret, "{tableId}.{qrSalt}"))}（无 padding，常量时间比较）。
 * 二维码内容为落地页 URL：{@code {qrBaseUrl}?p={tableId}&s={sign}&t={epochSecond}}；
 * {@code t} 用于时间窗校验，重签时轮换 {@code qrSalt} 使旧二维码立即失效。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TableQrService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private static final int QR_SIZE = 300;

    private static final long FUTURE_SKEW_SECONDS = 300L;

    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final AppProperties appProperties;

    private final FileStorageFacade fileStorageService;

    /** 计算二维码签名。 */
    public String sign(Long tableId, String qrSalt) {
        String secret = appProperties.getTable().getQrSecret();
        if (secret == null || secret.isBlank()) {
            throw new BizException(ErrorCode.STORAGE_ERROR, "桌台二维码密钥未配置");
        }
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            byte[] digest = mac.doFinal((tableId + "." + qrSalt).getBytes(StandardCharsets.UTF_8));
            return URL_ENCODER.encodeToString(digest);
        } catch (Exception e) {
            log.error("桌台二维码签名失败 tableId={}", tableId, e);
            throw new BizException(ErrorCode.INTERNAL_ERROR, "二维码生成失败，请重试");
        }
    }

    /** 常量时间校验签名。 */
    public boolean matches(Long tableId, String qrSalt, String signature) {
        if (signature == null || signature.isBlank()) {
            return false;
        }
        try {
            String expected = sign(tableId, qrSalt);
            return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                    signature.getBytes(StandardCharsets.UTF_8));
        } catch (BizException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("二维码签名比较异常 tableId={}: {}", tableId, e.getMessage());
            return false;
        }
    }

    /**
     * 校验二维码（签名 + 时间窗）。{@code t} 缺省时兼容合同旧请求（不校验时间窗）。
     *
     * @param table 桌台
     * @param signature 请求签名
     * @param timestamp 签名时间戳（epoch 秒，可空）
     */
    public void requireValid(TableInfo table, String signature, Long timestamp) {
        if (signature == null || signature.length() < 16 || signature.length() > 256
                || !matches(table.getId(), table.getQrSalt(), signature)) {
            throw new BizException(ErrorCode.TABLE_QR_INVALID, "二维码签名校验失败，请重新扫码");
        }
        int validityDays = appProperties.getTable().getQrValidityDays();
        if (timestamp == null || validityDays <= 0) {
            return;
        }
        long now = Instant.now().getEpochSecond();
        if (timestamp > now + FUTURE_SKEW_SECONDS) {
            throw new BizException(ErrorCode.TABLE_QR_INVALID, "二维码签名时间非法");
        }
        if (now - timestamp > validityDays * 86400L) {
            throw new BizException(ErrorCode.TABLE_QR_INVALID, "二维码已过期，请使用商家最新桌码");
        }
    }

    /** 生成二维码落地页内容。 */
    public String buildQrContent(Long tableId, String qrSalt) {
        StringBuilder builder = new StringBuilder(appProperties.getTable().getQrBaseUrl());
        builder.append(builder.indexOf("?") >= 0 ? '&' : '?');
        builder.append("p=").append(tableId);
        builder.append("&s=").append(sign(tableId, qrSalt));
        builder.append("&t=").append(Instant.now().getEpochSecond());
        return builder.toString();
    }

    /**
     * 生成二维码 PNG 并落库（file_object，bizType=table，仅内部使用）。
     *
     * @param table 桌台
     * @param content 二维码内容
     * @return 文件 URL（{@code /files/...}）
     */
    public String generatePng(TableInfo table, String content) {
        byte[] png = renderPng(content);
        String originalName = "table-" + table.getTableNo() + "-qr.png";
        return fileStorageService.storeBytes(png, "image/png", originalName, "table", null).url();
    }

    private byte[] renderPng(String content) {
        try {
            Map<EncodeHintType, Object> hints = new LinkedHashMap<>();
            hints.put(EncodeHintType.CHARACTER_SET, StandardCharsets.UTF_8.name());
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
            hints.put(EncodeHintType.MARGIN, 1);
            BitMatrix matrix = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, QR_SIZE, QR_SIZE, hints);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", output);
            return output.toByteArray();
        } catch (WriterException | IOException e) {
            log.error("二维码图片生成失败", e);
            throw new BizException(ErrorCode.STORAGE_ERROR, "二维码图片生成失败，请重试");
        }
    }
}
