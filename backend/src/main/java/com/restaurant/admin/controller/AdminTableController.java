package com.restaurant.admin.controller;

import com.restaurant.common.api.Result;
import com.restaurant.common.constant.ApiConstants;
import com.restaurant.table.dto.TableCreateRequest;
import com.restaurant.table.dto.TableUpdateRequest;
import com.restaurant.table.service.TableService;
import com.restaurant.table.vo.DeletedView;
import com.restaurant.table.vo.TableView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 管理后台桌台维护（{@code docs/02-api.md §17} 的 ADMIN 补充入口）。 */
@RestController
@RequestMapping(ApiConstants.API_V1 + "/admin/tables")
@RequiredArgsConstructor
@Tag(name = "管理后台-桌台")
@SecurityRequirement(name = "bearerAuth")
public class AdminTableController {

    private final TableService tableService;

    @GetMapping
    @Operation(summary = "查询桌台（merchantId 可选，缺省返回全部）")
    public Result<List<TableView>> list(@RequestParam(required = false) Long merchantId) {
        return Result.success(tableService.listByMerchant(merchantId));
    }

    @PostMapping
    @Operation(summary = "管理员创建桌台")
    public Result<TableView> create(@Valid @RequestBody TableCreateRequest request) {
        return Result.success(tableService.createAsAdmin(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "管理员更新桌台")
    public Result<TableView> update(@PathVariable Long id,
                                    @Valid @RequestBody TableUpdateRequest request) {
        return Result.success(tableService.updateAsAdmin(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "管理员删除桌台")
    public Result<DeletedView> delete(@PathVariable Long id) {
        return Result.success(tableService.deleteAsAdmin(id));
    }
}
