package com.restaurant.storage.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** OSS 直传确认请求（{@code docs/02-api.md §13.3}）。 */
@Data
public class FileConfirmRequest {

    @NotBlank(message = "bizType 不能为空")
    private String bizType;

    @NotBlank(message = "objectKey 不能为空")
    @Size(max = 255, message = "objectKey 不能超过 255")
    private String objectKey;

    @NotNull(message = "size 不能为空")
    @Min(value = 1, message = "文件大小不合法")
    @Max(value = 5 * 1024 * 1024L, message = "文件不能超过 5MB")
    private Long size;

    @NotBlank(message = "contentType 不能为空")
    private String contentType;

    @Size(max = 120, message = "原始文件名不能超过 120")
    private String originalName;
}
