package com.restaurant.admin.controller;

import com.restaurant.admin.dto.AdminUserStatusRequest;
import com.restaurant.admin.service.AdminUserService;
import com.restaurant.admin.vo.AdminUserView;
import com.restaurant.common.constant.ApiConstants;
import com.restaurant.common.api.PageResult;
import com.restaurant.common.api.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 管理后台用户管理（{@code docs/02-api.md §20.1–20.2}）。 */
@Validated
@RestController
@RequestMapping(ApiConstants.API_V1 + "/admin/users")
@RequiredArgsConstructor
@Tag(name = "管理-用户", description = "用户列表与启用禁用")
@SecurityRequirement(name = "bearerAuth")
public class AdminUserController {

    private final AdminUserService adminUserService;

    @Operation(summary = "用户列表")
    @GetMapping
    public Result<PageResult<AdminUserView>> list(@RequestParam(required = false) String keyword,
                                                  @RequestParam(required = false) String role,
                                                  @RequestParam(required = false) String status,
                                                  @RequestParam(defaultValue = "1") @Min(1) @Max(10000) int page,
                                                  @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size) {
        return Result.success(adminUserService.list(keyword, role, status, page, size));
    }

    @Operation(summary = "启用/禁用用户")
    @PatchMapping("/{id}/status")
    public Result<AdminUserView> updateStatus(@PathVariable Long id,
                                              @Valid @RequestBody AdminUserStatusRequest request) {
        return Result.success(adminUserService.updateStatus(id, request));
    }
}
