package com.restaurant.table.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.restaurant.audit.AuditActions;
import com.restaurant.audit.AuditService;
import com.restaurant.common.api.ErrorCode;
import com.restaurant.common.enums.Role;
import com.restaurant.common.exception.BizException;
import com.restaurant.contract.OrderLookupPort;
import com.restaurant.security.RoleGuard;
import com.restaurant.security.UserPrincipal;
import com.restaurant.table.TableInfo;
import com.restaurant.table.TableQrService;
import com.restaurant.table.TableSession;
import com.restaurant.table.dto.TableCreateRequest;
import com.restaurant.table.dto.TableUpdateRequest;
import com.restaurant.table.mapper.TableInfoMapper;
import com.restaurant.table.mapper.TableSessionMapper;
import com.restaurant.table.vo.DeletedView;
import com.restaurant.table.vo.TableQrView;
import com.restaurant.table.vo.TableView;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Objects;

/**
 * 商家/管理员桌台管理（{@code docs/02-api.md §17}）。
 *
 * <p>创建桌台即生成二维码 PNG（file_object, bizType=table）；重签时轮换 salt 并递增 qrVersion，
 * 旧二维码签名立即失效（ADR-0007）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TableService {

    private static final int SALT_BYTES = 16;

    private static final SecureRandom RANDOM = new SecureRandom();

    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final TableInfoMapper tableInfoMapper;

    private final TableSessionMapper tableSessionMapper;

    private final TableQrService tableQrService;

    private final RoleGuard roleGuard;

    private final AuditService auditService;

    private final OrderLookupPort orderLookupPort;

    /** 商家端桌台列表（tableNo 升序，最多 200）。 */
    public List<TableView> listForMerchant() {
        return listByMerchant(roleGuard.requireMerchantId());
    }

    /** 管理员按商家查询桌台列表。 */
    public List<TableView> listByMerchant(Long merchantId) {
        if (merchantId == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "merchantId 不能为空");
        }
        List<TableInfo> tables = tableInfoMapper.selectList(Wrappers.<TableInfo>lambdaQuery()
                .eq(TableInfo::getMerchantId, merchantId)
                .orderByAsc(TableInfo::getTableNo)
                .last("LIMIT 200"));
        return tables.stream().map(this::toView).toList();
    }

    /** 商家创建桌台。 */
    @Transactional(rollbackFor = Exception.class)
    public TableView create(TableCreateRequest request) {
        Long merchantId = roleGuard.requireMerchantId();
        return createForMerchant(merchantId, request);
    }

    /** 管理员创建桌台（需在请求中指定 merchantId）。 */
    @Transactional(rollbackFor = Exception.class)
    public TableView createAsAdmin(TableCreateRequest request) {
        UserPrincipal user = roleGuard.requireAnyRole(Role.ADMIN);
        if (request.getMerchantId() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "merchantId 不能为空");
        }
        return createForMerchant(request.getMerchantId(), request);
    }

    /** 商家更新桌台。 */
    @Transactional(rollbackFor = Exception.class)
    public TableView update(Long id, TableUpdateRequest request) {
        Long merchantId = roleGuard.requireMerchantId();
        return updateForMerchant(merchantId, id, request);
    }

    /** 管理员更新桌台（跨商家，无需归属校验）。 */
    @Transactional(rollbackFor = Exception.class)
    public TableView updateAsAdmin(Long id, TableUpdateRequest request) {
        roleGuard.requireAnyRole(Role.ADMIN);
        return updateForMerchant(null, id, request);
    }

    /** 商家删除桌台（存在 ACTIVE 会话或进行中订单时 40909）。 */
    @Transactional(rollbackFor = Exception.class)
    public DeletedView delete(Long id) {
        Long merchantId = roleGuard.requireMerchantId();
        UserPrincipal user = roleGuard.currentUser();
        TableInfo table = requireOwnedTable(id, merchantId);
        requireNoActiveSession(table);
        tableInfoMapper.deleteById(table.getId());
        auditService.event(user, AuditActions.TABLE_DELETE, "TABLE", String.valueOf(id),
                "删除桌台 " + table.getTableNo(), true);
        return new DeletedView(true);
    }

    /** 管理员删除桌台。 */
    @Transactional(rollbackFor = Exception.class)
    public DeletedView deleteAsAdmin(Long id) {
        UserPrincipal user = roleGuard.requireAnyRole(Role.ADMIN);
        TableInfo table = requireOwnedTable(id, null);
        requireNoActiveSession(table);
        tableInfoMapper.deleteById(table.getId());
        auditService.event(user, AuditActions.TABLE_DELETE, "TABLE", String.valueOf(id),
                "管理员删除桌台 " + table.getTableNo(), true);
        return new DeletedView(true);
    }

    /** 商家查询桌台详情。 */
    public TableView get(Long id) {
        return toView(requireOwnedTable(id, roleGuard.requireMerchantId()));
    }

    /** 商家查看桌台二维码（缺失时补生成）。 */
    @Transactional(rollbackFor = Exception.class)
    public TableQrView qr(Long id) {
        Long merchantId = roleGuard.requireMerchantId();
        TableInfo table = requireOwnedTable(id, merchantId);
        if (!StringUtils.hasText(table.getQrCodeUrl())) {
            signQr(table, false);
        }
        return toQrView(table);
    }

    /** 商家重签二维码：旧签名立即失效。 */
    @Transactional(rollbackFor = Exception.class)
    public TableQrView regenerateQr(Long id) {
        Long merchantId = roleGuard.requireMerchantId();
        UserPrincipal user = roleGuard.currentUser();
        TableInfo table = requireOwnedTable(id, merchantId);
        signQr(table, true);
        auditService.event(user, AuditActions.TABLE_QR_REGENERATE, "TABLE", String.valueOf(id),
                "重签桌台二维码 qrVersion=" + table.getQrVersion(), true);
        return toQrView(table);
    }

    private TableView createForMerchant(Long merchantId, TableCreateRequest request) {
        UserPrincipal user = roleGuard.currentUser();
        String tableNo = request.getTableNo().trim();
        ensureTableNoUnique(merchantId, tableNo, null);
        TableInfo table = new TableInfo();
        table.setMerchantId(merchantId);
        table.setTableNo(tableNo);
        table.setName(StringUtils.hasText(request.getName()) ? request.getName().trim() : tableNo);
        table.setCapacity(request.getCapacity() == null ? 4 : request.getCapacity());
        table.setZone(StringUtils.hasText(request.getZone()) ? request.getZone().trim() : null);
        table.setStatus("FREE");
        table.setQrSalt(randomSalt());
        table.setQrVersion(0);
        table.setQrSignedAt(LocalDateTime.now());
        table.setDeleted(0);
        table.setVersion(0);
        tableInfoMapper.insert(table);
        signQr(table, false);
        auditService.event(user, AuditActions.TABLE_CREATE, "TABLE", String.valueOf(table.getId()),
                "创建桌台 " + tableNo, true);
        log.info("桌台已创建 tableId={}, merchantId={}, tableNo={}", table.getId(), merchantId, tableNo);
        return toView(table);
    }

    private TableView updateForMerchant(Long merchantId, Long id, TableUpdateRequest request) {
        UserPrincipal user = roleGuard.currentUser();
        TableInfo table = requireOwnedTable(id, merchantId);
        boolean tableNoChanged = StringUtils.hasText(request.getTableNo())
                && !request.getTableNo().trim().equals(table.getTableNo());
        if (tableNoChanged) {
            ensureTableNoUnique(table.getMerchantId(), request.getTableNo().trim(), id);
            requireNoActiveSession(table);
        }
        if (StringUtils.hasText(request.getTableNo())) {
            table.setTableNo(request.getTableNo().trim());
        }
        if (StringUtils.hasText(request.getName())) {
            table.setName(request.getName().trim());
        }
        if (request.getCapacity() != null) {
            table.setCapacity(request.getCapacity());
        }
        if (StringUtils.hasText(request.getZone())) {
            table.setZone(request.getZone().trim());
        }
        if (tableInfoMapper.updateById(table) == 0) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "桌台信息已变更，请刷新后重试");
        }
        TableInfo latest = requireOwnedTable(id, null);
        if (tableNoChanged) {
            signQr(latest, false);
        }
        auditService.event(user, AuditActions.TABLE_UPDATE, "TABLE", String.valueOf(id),
                "更新桌台 " + latest.getTableNo(), true);
        return toView(latest);
    }

    private void signQr(TableInfo table, boolean rotate) {
        String salt = randomSalt();
        int qrVersion = table.getQrVersion() == null ? 1 : table.getQrVersion() + 1;
        String content = tableQrService.buildQrContent(table.getId(), salt);
        String url = tableQrService.generatePng(table, content);
        int updated = tableInfoMapper.updateQr(table.getId(), table.getVersion(), salt, qrVersion, url,
                LocalDateTime.now());
        if (updated == 0) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "桌台信息已变更，请刷新后重试");
        }
        table.setQrSalt(salt);
        table.setQrVersion(qrVersion);
        table.setQrCodeUrl(url);
        table.setQrSignedAt(LocalDateTime.now());
        table.setVersion(table.getVersion() == null ? 1 : table.getVersion() + 1);
        if (!rotate) {
            log.debug("桌台二维码已生成 tableId={}, qrVersion={}", table.getId(), qrVersion);
        }
    }

    private void requireNoActiveSession(TableInfo table) {
        long sessions = tableSessionMapper.selectCount(Wrappers.<TableSession>lambdaQuery()
                .eq(TableSession::getTableId, table.getId())
                .eq(TableSession::getStatus, "ACTIVE"));
        if (sessions > 0 || orderLookupPort.hasActiveOrderForTable(table.getId())) {
            throw new BizException(ErrorCode.TABLE_SESSION_ACTIVE, "桌台有进行中会话或订单，暂不能操作");
        }
    }

    private void ensureTableNoUnique(Long merchantId, String tableNo, Long excludeId) {
        var query = Wrappers.<TableInfo>lambdaQuery()
                .eq(TableInfo::getMerchantId, merchantId)
                .eq(TableInfo::getTableNo, tableNo);
        if (excludeId != null) {
            query.ne(TableInfo::getId, excludeId);
        }
        if (tableInfoMapper.selectCount(query) > 0) {
            throw new BizException(ErrorCode.UNIQUE_CONFLICT, "桌号已存在");
        }
    }

    private TableInfo requireOwnedTable(Long id, Long merchantId) {
        TableInfo table = tableInfoMapper.selectById(id);
        if (table == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "桌台不存在");
        }
        if (merchantId != null && !Objects.equals(merchantId, table.getMerchantId())) {
            throw new BizException(ErrorCode.RESOURCE_FORBIDDEN, "无权操作该桌台");
        }
        return table;
    }

    private TableView toView(TableInfo table) {
        String qrContent = table.getQrSalt() == null ? null
                : tableQrService.buildQrContent(table.getId(), table.getQrSalt());
        return new TableView(table.getId(), table.getMerchantId(), table.getTableNo(), table.getName(),
                table.getCapacity(), table.getZone(), table.getStatus(), table.getCurrentSessionId(),
                table.getQrVersion(), qrContent, table.getQrCodeUrl(), table.getVersion(), table.getCreatedAt());
    }

    private TableQrView toQrView(TableInfo table) {
        String content = tableQrService.buildQrContent(table.getId(), table.getQrSalt());
        return new TableQrView(table.getId(), table.getTableNo(), table.getQrVersion(), content, content,
                table.getQrCodeUrl(), table.getQrSignedAt());
    }

    private static String randomSalt() {
        byte[] bytes = new byte[SALT_BYTES];
        RANDOM.nextBytes(bytes);
        return URL_ENCODER.encodeToString(bytes);
    }
}
