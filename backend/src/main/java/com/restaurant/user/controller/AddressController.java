package com.restaurant.user.controller;

import com.restaurant.common.api.Result;
import com.restaurant.common.constant.ApiConstants;
import com.restaurant.user.dto.AddressCreateRequest;
import com.restaurant.user.dto.AddressUpdateRequest;
import com.restaurant.user.service.AddressService;
import com.restaurant.user.vo.AddressView;
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
import java.util.Map;

/** 收货地址（docs/02-api.md §5）。 */
@RestController
@RequestMapping(ApiConstants.API_V1 + "/addresses")
@RequiredArgsConstructor
@Tag(name = "地址-顾客", description = "收货地址 CRUD（仅本人数据）")
@SecurityRequirement(name = "bearerAuth")
public class AddressController {

    private final AddressService addressService;

    @GetMapping
    @Operation(summary = "地址列表", description = "默认地址在前，其余按更新时间倒序")
    public Result<List<AddressView>> list() {
        return Result.success(addressService.list());
    }

    @PostMapping
    @Operation(summary = "新增地址")
    public Result<AddressView> create(@Valid @RequestBody AddressCreateRequest request) {
        return Result.success(addressService.create(request));
    }

    @GetMapping("/{id}")
    @Operation(summary = "地址详情")
    public Result<AddressView> get(@PathVariable Long id) {
        return Result.success(addressService.get(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新地址")
    public Result<AddressView> update(@PathVariable Long id, @Valid @RequestBody AddressUpdateRequest request) {
        return Result.success(addressService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除地址（逻辑删除，不自动提升默认地址）")
    public Result<Map<String, Boolean>> delete(@PathVariable Long id) {
        return Result.success(addressService.delete(id));
    }
}
