package com.restaurant.table.controller;

import com.restaurant.common.api.Result;
import com.restaurant.common.constant.ApiConstants;
import com.restaurant.table.dto.TableCreateRequest;
import com.restaurant.table.dto.TableUpdateRequest;
import com.restaurant.table.service.TableService;
import com.restaurant.table.vo.DeletedView;
import com.restaurant.table.vo.TableQrView;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 商家端桌台管理（{@code docs/02-api.md §17}）。 */
@RestController
@RequestMapping(ApiConstants.API_V1 + "/merchant/tables")
@RequiredArgsConstructor
@Tag(name = "商家-桌台管理")
@SecurityRequirement(name = "bearerAuth")
public class MerchantTableController {

    private final TableService tableService;

    @GetMapping
    @Operation(summary = "桌台列表")
    public Result<List<TableView>> list() {
        return Result.success(tableService.listForMerchant());
    }

    @PostMapping
    @Operation(summary = "创建桌台（自动生成二维码）")
    public Result<TableView> create(@Valid @RequestBody TableCreateRequest request) {
        return Result.success(tableService.create(request));
    }

    @GetMapping("/{id}")
    @Operation(summary = "桌台详情")
    public Result<TableView> get(@PathVariable Long id) {
        return Result.success(tableService.get(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新桌台（改桌号自动重签二维码）")
    public Result<TableView> update(@PathVariable Long id,
                                   @Valid @RequestBody TableUpdateRequest request) {
        return Result.success(tableService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除桌台")
    public Result<DeletedView> delete(@PathVariable Long id) {
        return Result.success(tableService.delete(id));
    }

    @GetMapping("/{id}/qr")
    @Operation(summary = "查看桌台二维码")
    public Result<TableQrView> qr(@PathVariable Long id) {
        return Result.success(tableService.qr(id));
    }

    @PostMapping("/{id}/qr")
    @Operation(summary = "重签桌台二维码（旧码立即失效）")
    public Result<TableQrView> regenerateQr(@PathVariable Long id) {
        return Result.success(tableService.regenerateQr(id));
    }
}
