package com.restaurant.storage.controller;

import com.restaurant.common.api.ErrorCode;
import com.restaurant.common.api.Result;
import com.restaurant.common.constant.ApiConstants;
import com.restaurant.common.exception.BizException;
import com.restaurant.security.RoleGuard;
import com.restaurant.storage.FileObject;
import com.restaurant.storage.dto.FileConfirmRequest;
import com.restaurant.storage.mapper.FileObjectMapper;
import com.restaurant.storage.service.FileStorageFacade;
import com.restaurant.storage.vo.FileObjectView;
import com.restaurant.storage.vo.FilePolicyView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件上传接口（{@code docs/02-api.md §13}）。
 *
 * <p>local 模式走后台上传（multipart）；oss 模式走 Post Policy 直传 + confirm 登记。
 * 两种模式返回的视图都不包含 AccessKeySecret。
 */
@RestController
@RequestMapping(ApiConstants.API_V1 + "/files")
@RequiredArgsConstructor
@Tag(name = "文件上传")
public class FileController {

    private final FileStorageFacade fileStorageFacade;

    private final FileObjectMapper fileObjectMapper;

    private final RoleGuard roleGuard;

    @GetMapping("/policy")
    @Operation(summary = "获取上传策略")
    @SecurityRequirement(name = "bearerAuth")
    public Result<FilePolicyView> policy(@RequestParam String bizType,
                                         @RequestParam String ext) {
        return Result.success(fileStorageFacade.policy(bizType, ext));
    }

    @PostMapping({"", "/upload"})
    @Operation(summary = "后台上传文件（local 模式）")
    @SecurityRequirement(name = "bearerAuth")
    public Result<FileObjectView> upload(@RequestPart("file") MultipartFile file,
                                         @RequestParam(value = "bizType", required = false) String bizType) {
        return Result.success(fileStorageFacade.store(file, bizType, roleGuard.currentUserId()));
    }

    @PostMapping("/confirm")
    @Operation(summary = "确认 OSS 直传结果（oss 模式）")
    @SecurityRequirement(name = "bearerAuth")
    public Result<FileObjectView> confirm(@Valid @RequestBody FileConfirmRequest request) {
        return Result.success(fileStorageFacade.confirm(request, roleGuard.currentUserId()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询文件元数据")
    @SecurityRequirement(name = "bearerAuth")
    public Result<FileObjectView> get(@PathVariable Long id) {
        FileObject fileObject = fileObjectMapper.selectById(id);
        if (fileObject == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "文件不存在");
        }
        return Result.success(new FileObjectView(fileObject.getId(), fileObject.getBizType(),
                fileObject.getObjectKey(), fileObject.getUrl(), fileObject.getStorageType(),
                fileObject.getSize() == null ? 0L : fileObject.getSize(), fileObject.getContentType(),
                fileObject.getOriginalName(), fileObject.getCreatedAt()));
    }
}
