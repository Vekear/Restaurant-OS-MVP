package com.restaurant.table.controller;

import com.restaurant.common.api.Result;
import com.restaurant.common.constant.ApiConstants;
import com.restaurant.table.dto.DineInBindRequest;
import com.restaurant.table.dto.DineInScanRequest;
import com.restaurant.table.service.DineInSessionService;
import com.restaurant.table.vo.DineInSessionView;
import com.restaurant.table.vo.SessionClosedView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 堂食开桌/会话（{@code docs/02-api.md §10}）。 */
@RestController
@RequestMapping(ApiConstants.API_V1 + "/dine-in")
@RequiredArgsConstructor
@Tag(name = "堂食-桌台会话")
@SecurityRequirement(name = "bearerAuth")
public class DineInController {

    private final DineInSessionService dineInSessionService;

    @PostMapping("/scan")
    @Operation(summary = "扫码开桌（HMAC 验签 + 乐观锁占桌）")
    public Result<DineInSessionView> scan(@Valid @RequestBody DineInScanRequest request) {
        return Result.success(dineInSessionService.scan(request));
    }

    @PostMapping("/bind")
    @Operation(summary = "手输桌号开桌")
    public Result<DineInSessionView> bind(@Valid @RequestBody DineInBindRequest request) {
        return Result.success(dineInSessionService.bind(request));
    }

    @GetMapping("/session")
    @Operation(summary = "当前桌台会话")
    public Result<DineInSessionView> session(
            @RequestHeader(value = ApiConstants.HEADER_TABLE_SESSION, required = false) String sessionToken) {
        return Result.success(dineInSessionService.session(sessionToken));
    }

    @PostMapping("/session/close")
    @Operation(summary = "关台（存在进行中订单时拒绝）")
    public Result<SessionClosedView> close(
            @RequestHeader(value = ApiConstants.HEADER_TABLE_SESSION, required = false) String sessionToken) {
        return Result.success(dineInSessionService.close(sessionToken));
    }
}
