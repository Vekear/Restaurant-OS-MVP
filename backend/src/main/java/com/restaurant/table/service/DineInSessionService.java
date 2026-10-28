package com.restaurant.table.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.restaurant.audit.AuditActions;
import com.restaurant.audit.AuditService;
import com.restaurant.common.api.ErrorCode;
import com.restaurant.common.config.AppProperties;
import com.restaurant.common.exception.BizException;
import com.restaurant.contract.MerchantPort;
import com.restaurant.contract.OrderLookupPort;
import com.restaurant.contract.TableSessionPort;
import com.restaurant.security.RoleGuard;
import com.restaurant.security.UserPrincipal;
import com.restaurant.table.TableInfo;
import com.restaurant.table.TableQrService;
import com.restaurant.table.TableSession;
import com.restaurant.table.dto.DineInBindRequest;
import com.restaurant.table.dto.DineInScanRequest;
import com.restaurant.table.mapper.TableInfoMapper;
import com.restaurant.table.mapper.TableSessionMapper;
import com.restaurant.table.vo.DineInSessionView;
import com.restaurant.table.vo.SessionClosedView;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;

/**
 * 堂食开桌/关台（{@code docs/02-api.md §10}）：扫码验签 → 乐观锁占桌 → 建会话。
 *
 * <p>并发保障：{@code table_info.version} 条件更新，只有一个请求能占桌成功（ADR-0007）；
 * 同一用户换桌时若旧会话存在进行中订单则拒绝（40917），否则自动关旧桌。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DineInSessionService {

    private static final int TOKEN_BYTES = 32;

    private static final SecureRandom RANDOM = new SecureRandom();

    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final TableInfoMapper tableInfoMapper;

    private final TableSessionMapper tableSessionMapper;

    private final TableQrService tableQrService;

    private final TableReleaseService tableReleaseService;

    private final TableSessionPort tableSessionPort;

    private final RoleGuard roleGuard;

    private final AuditService auditService;

    private final MerchantPort merchantPort;

    private final OrderLookupPort orderLookupPort;

    private final AppProperties appProperties;

    /** 扫码开桌：校验 HMAC 签名与时间窗后占桌。 */
    @Transactional(rollbackFor = Exception.class)
    public DineInSessionView scan(DineInScanRequest request) {
        UserPrincipal user = roleGuard.currentUser();
        Long tableId;
        try {
            tableId = Long.valueOf(request.getP());
        } catch (NumberFormatException e) {
            throw new BizException(ErrorCode.PARAM_INVALID, "桌台参数非法");
        }
        TableInfo table = requireTable(tableId);
        try {
            tableQrService.requireValid(table, request.getS(), request.getT());
        } catch (BizException e) {
            auditService.event(user, AuditActions.TABLE_QR_INVALID, "TABLE", String.valueOf(tableId),
                    "二维码校验失败: " + e.getMessage(), false);
            throw e;
        }
        return bindTable(table, user, "扫码开桌", AuditActions.TABLE_SCAN_BIND);
    }

    /** 手输桌号开桌（需服务端开启 allow-manual-input，二维码签名可选）。 */
    @Transactional(rollbackFor = Exception.class)
    public DineInSessionView bind(DineInBindRequest request) {
        if (!appProperties.getTable().isAllowManualInput()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "手输桌号已关闭，请扫码");
        }
        UserPrincipal user = roleGuard.currentUser();
        TableInfo table = tableInfoMapper.selectOne(Wrappers.<TableInfo>lambdaQuery()
                .eq(TableInfo::getMerchantId, appProperties.getDefaultMerchantId())
                .eq(TableInfo::getTableNo, request.getTableNo().trim())
                .last("LIMIT 1"));
        if (table == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "桌台不存在");
        }
        if (StringUtils.hasText(request.getS())) {
            try {
                tableQrService.requireValid(table, request.getS(), request.getT());
            } catch (BizException e) {
                auditService.event(user, AuditActions.TABLE_QR_INVALID, "TABLE", String.valueOf(table.getId()),
                        "二维码校验失败: " + e.getMessage(), false);
                throw e;
            }
        }
        return bindTable(table, user, "手输桌号开桌", AuditActions.TABLE_MANUAL_BIND);
    }

    /** 查询当前会话（请求头优先，否则取用户最近 ACTIVE 会话）。 */
    public DineInSessionView session(String sessionToken) {
        Long userId = roleGuard.currentUserId();
        TableSessionPort.TableSessionSnapshot snapshot = StringUtils.hasText(sessionToken)
                ? tableSessionPort.requireActive(sessionToken)
                : tableSessionPort.findActiveByUser(userId);
        if (snapshot == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "当前没有进行中的桌台会话");
        }
        TableInfo table = requireTable(snapshot.tableId());
        Long currentOrderId = orderLookupPort.activeOrderIdBySession(snapshot.sessionId());
        return toView(snapshot, table, currentOrderId);
    }

    /** 用户主动关台（存在进行中订单时拒绝，40916）。 */
    @Transactional(rollbackFor = Exception.class)
    public SessionClosedView close(String sessionToken) {
        UserPrincipal user = roleGuard.currentUser();
        TableSessionPort.TableSessionSnapshot snapshot = StringUtils.hasText(sessionToken)
                ? tableSessionPort.requireActive(sessionToken)
                : tableSessionPort.findActiveByUser(user.getId());
        if (snapshot == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "当前没有进行中的桌台会话");
        }
        if (orderLookupPort.hasActiveOrderForSession(snapshot.sessionId())) {
            throw new BizException(ErrorCode.SESSION_HAS_ACTIVE_ORDER, "会话存在进行中订单，暂不能关台");
        }
        tableReleaseService.release(snapshot.tableId(), snapshot.sessionId());
        auditService.event(user, AuditActions.TABLE_CLOSE, "TABLE", String.valueOf(snapshot.tableId()),
                "关台 sessionId=" + snapshot.sessionId(), true);
        return new SessionClosedView(true, snapshot.tableId(), snapshot.sessionId(), LocalDateTime.now());
    }

    private DineInSessionView bindTable(TableInfo table, UserPrincipal user, String remark, String action) {
        MerchantPort.MerchantSnapshot merchant = merchantPort.get(table.getMerchantId());
        if (merchant == null || !"APPROVED".equals(merchant.auditStatus()) || !"ENABLED".equals(merchant.status())) {
            throw new BizException(ErrorCode.MERCHANT_UNAVAILABLE, "商家不存在或不可用");
        }
        String userId = String.valueOf(user.getId());
        table = releaseExpiredOccupant(table);
        if ("OCCUPIED".equals(table.getStatus())) {
            TableSession current = table.getCurrentSessionId() == null
                    ? null : tableSessionMapper.selectById(table.getCurrentSessionId());
            if (current != null && "ACTIVE".equals(current.getStatus()) && userId.equals(current.getOpenedBy())) {
                renew(current);
                auditService.event(user, action, "TABLE", String.valueOf(table.getId()),
                        remark + "（复用会话）", true);
                return toView(toSnapshot(current), table, orderLookupPort.activeOrderIdBySession(current.getId()));
            }
            throw new BizException(ErrorCode.TABLE_OCCUPIED, "桌台已被占用，请稍后再试");
        }

        TableInfo locked = tableInfoMapper.selectForUpdate(table.getId());
        if (locked == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "桌台不存在");
        }
        if ("OCCUPIED".equals(locked.getStatus())) {
            throw new BizException(ErrorCode.TABLE_OCCUPIED, "桌台已被占用，请稍后再试");
        }
        table = locked;

        List<TableSession> mine = tableSessionMapper.selectList(Wrappers.<TableSession>lambdaQuery()
                .eq(TableSession::getStatus, "ACTIVE")
                .eq(TableSession::getOpenedBy, userId));
        for (TableSession old : mine) {
            if (old.getTableId().equals(table.getId())) {
                renew(old);
                auditService.event(user, action, "TABLE", String.valueOf(table.getId()),
                        remark + "（复用会话）", true);
                return toView(toSnapshot(old), table, orderLookupPort.activeOrderIdBySession(old.getId()));
            }
            if (orderLookupPort.hasActiveOrderForSession(old.getId())) {
                throw new BizException(ErrorCode.USER_HAS_ACTIVE_SESSION,
                        "您有未完成的堂食订单，请先完成或取消");
            }
            tableReleaseService.release(old.getTableId(), old.getId());
        }

        LocalDateTime now = LocalDateTime.now();
        TableSession session = new TableSession();
        session.setTableId(table.getId());
        session.setMerchantId(table.getMerchantId());
        session.setSessionToken(randomToken());
        session.setStatus("ACTIVE");
        session.setGuestCount(1);
        session.setOpenedBy(userId);
        session.setOpenedAt(now);
        session.setLastOrderAt(now);
        session.setExpireAt(now.plusMinutes(appProperties.getTable().getSessionIdleTimeoutMinutes()));
        session.setVersion(0);
        tableSessionMapper.insert(session);

        if (tableInfoMapper.occupy(table.getId(), session.getId(), table.getVersion()) == 0) {
            throw new BizException(ErrorCode.TABLE_OCCUPIED, "桌台已被占用，请重新扫码");
        }
        auditService.event(user, action, "TABLE", String.valueOf(table.getId()),
                remark + " sessionId=" + session.getId(), true);
        log.info("开桌成功 tableId={}, tableNo={}, sessionId={}, userId={}",
                table.getId(), table.getTableNo(), session.getId(), user.getId());
        TableInfo latest = tableInfoMapper.selectById(table.getId());
        return toView(toSnapshot(session), latest == null ? table : latest, null);
    }

    /**
     * 桌台被“已过期但未释放”的会话占用时先惰性释放，使重新扫码/绑桌成功而不是 40910
     * （T31 缺口：旧逻辑只把会话置 EXPIRED，桌台长期 OCCUPIED）。
     */
    private TableInfo releaseExpiredOccupant(TableInfo table) {
        if (!"OCCUPIED".equals(table.getStatus()) || table.getCurrentSessionId() == null) {
            return table;
        }
        TableSession current = tableSessionMapper.selectById(table.getCurrentSessionId());
        if (current == null) {
            return table;
        }
        boolean expiredStatus = "EXPIRED".equals(current.getStatus());
        boolean activeExpired = "ACTIVE".equals(current.getStatus())
                && current.getExpireAt() != null && current.getExpireAt().isBefore(LocalDateTime.now());
        if (!expiredStatus && !activeExpired) {
            return table;
        }
        tableReleaseService.releaseExpired(table.getId(), current.getId());
        return requireTable(table.getId());
    }

    private void renew(TableSession session) {
        LocalDateTime expireAt = LocalDateTime.now()
                .plusMinutes(appProperties.getTable().getSessionIdleTimeoutMinutes());
        tableSessionMapper.renew(session.getId(), expireAt);
        session.setExpireAt(expireAt);
    }

    private TableSessionPort.TableSessionSnapshot toSnapshot(TableSession session) {
        TableInfo table = tableInfoMapper.selectById(session.getTableId());
        MerchantPort.MerchantSnapshot merchant = merchantPort.get(session.getMerchantId());
        return new TableSessionPort.TableSessionSnapshot(session.getId(), session.getTableId(),
                table == null ? null : table.getTableNo(), table == null ? null : table.getName(),
                session.getMerchantId(), merchant == null ? null : merchant.name(),
                table == null ? null : table.getCapacity(), session.getSessionToken(), session.getStatus(),
                session.getOpenedAt(), session.getExpireAt());
    }

    private DineInSessionView toView(TableSessionPort.TableSessionSnapshot snapshot, TableInfo table,
                                     Long currentOrderId) {
        return new DineInSessionView(snapshot.tableId(), snapshot.tableNo(), snapshot.tableName(),
                snapshot.merchantId(), snapshot.merchantName(), snapshot.capacity(), snapshot.sessionId(),
                snapshot.sessionToken(), snapshot.status(), snapshot.openedAt(), snapshot.expireAt(),
                currentOrderId, table == null ? null : table.getStatus());
    }

    private TableInfo requireTable(Long tableId) {
        TableInfo table = tableInfoMapper.selectById(tableId);
        if (table == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "桌台不存在");
        }
        return table;
    }

    private static String randomToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return URL_ENCODER.encodeToString(bytes);
    }
}
