-- ============================================================================
-- V1__schema.sql
-- 餐厅点餐系统 MVP — 初始数据库结构（MySQL 8.0+ / InnoDB / utf8mb4）
--
-- 统一约定（详见 docs/03-data-model.md）：
--   * 表名：snake_case 单数；数据库列 snake_case，Java/JSON 字段 camelCase
--   * 主键：BIGINT AUTO_INCREMENT
--   * 金额：DECIMAL(10,2)；时间：DATETIME(3)；字符集：utf8mb4 / utf8mb4_0900_ai_ci
--   * 乐观锁：version INT NOT NULL DEFAULT 0（MyBatis-Plus @Version）
--   * 状态枚举用 VARCHAR 存字符串（布尔语义才用 TINYINT(1)），取值见 docs/03-data-model.md §6
--   * 命名：uk_<表>_<列> 唯一键 / idx_<表>_<列> 普通索引 / fk_<表>_<列> 外键 / chk_<表>_<列> 检查约束
--
-- 应用方式：由 Flyway 按版本号顺序自动执行（spring.flyway），禁止手工改已发布脚本。
-- ============================================================================

SET NAMES utf8mb4;

-- ----------------------------------------------------------------------------
-- 1. users — 三端统一账号（管理员 / 商家 / 顾客）
-- ----------------------------------------------------------------------------
CREATE TABLE users (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    username      VARCHAR(50)  NOT NULL                COMMENT '登录账号（唯一）',
    password      VARCHAR(100) NOT NULL                COMMENT 'BCrypt 密码哈希（禁止明文）',
    nickname      VARCHAR(50)  NOT NULL DEFAULT ''     COMMENT '昵称',
    phone         VARCHAR(20)  NULL                    COMMENT '手机号',
    avatar_url    VARCHAR(500) NULL                    COMMENT '头像 URL',
    role          VARCHAR(20)  NOT NULL                COMMENT '角色：ADMIN / MERCHANT / CUSTOMER',
    status        VARCHAR(20)  NOT NULL DEFAULT 'ENABLED' COMMENT '账号状态：ENABLED 启用 / DISABLED 禁用',
    last_login_at DATETIME(3)  NULL                    COMMENT '最后登录时间',
    version       INT          NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
    created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_username (username),
    KEY idx_users_role_status (role, status),
    CONSTRAINT chk_users_role CHECK (role IN ('ADMIN', 'MERCHANT', 'CUSTOMER')),
    CONSTRAINT chk_users_status CHECK (status IN ('ENABLED', 'DISABLED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '用户（三端统一账号）';

-- ----------------------------------------------------------------------------
-- 2. merchant — 商家（MVP 单商家演示，结构支持多商家；审核与启停分离）
-- ----------------------------------------------------------------------------
CREATE TABLE merchant (
    id               BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id          BIGINT        NOT NULL                COMMENT '商家账号 users.id（角色 MERCHANT）',
    name             VARCHAR(100)  NOT NULL                COMMENT '商家名称',
    description      VARCHAR(500)  NULL                    COMMENT '简介',
    phone            VARCHAR(20)   NULL                    COMMENT '联系电话',
    address          VARCHAR(255)  NULL                    COMMENT '门店地址',
    logo_url         VARCHAR(500)  NULL                    COMMENT 'Logo URL',
    delivery_fee     DECIMAL(10,2) NOT NULL DEFAULT 0.00   COMMENT '外卖配送费（元）',
    min_order_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00   COMMENT '外卖起送金额（元）',
    business_status  VARCHAR(20)   NOT NULL DEFAULT 'CLOSED'  COMMENT '营业状态：OPEN 营业中 / CLOSED 休息中',
    audit_status     VARCHAR(20)   NOT NULL DEFAULT 'PENDING' COMMENT '审核状态：PENDING / APPROVED / REJECTED',
    status           VARCHAR(20)   NOT NULL DEFAULT 'ENABLED' COMMENT '启用状态（管理后台）：ENABLED / DISABLED',
    version          INT           NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
    created_at       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_merchant_user (user_id),
    KEY idx_merchant_audit_status (audit_status),
    KEY idx_merchant_business_status (business_status, status),
    CONSTRAINT fk_merchant_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_merchant_business_status CHECK (business_status IN ('OPEN', 'CLOSED')),
    CONSTRAINT chk_merchant_audit_status CHECK (audit_status IN ('PENDING', 'APPROVED', 'REJECTED')),
    CONSTRAINT chk_merchant_status CHECK (status IN ('ENABLED', 'DISABLED')),
    CONSTRAINT chk_merchant_amount CHECK (delivery_fee >= 0 AND min_order_amount >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '商家';

-- ----------------------------------------------------------------------------
-- 3. category — 菜品分类（商家维度，逻辑删除）
-- ----------------------------------------------------------------------------
CREATE TABLE category (
    id          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    merchant_id BIGINT      NOT NULL                COMMENT '所属商家 merchant.id',
    name        VARCHAR(50) NOT NULL                COMMENT '分类名称',
    sort        INT         NOT NULL DEFAULT 0      COMMENT '排序值（升序）',
    status      VARCHAR(20) NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED 启用 / DISABLED 停用',
    deleted     TINYINT(1)  NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删 / 1 已删',
    version     INT         NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
    created_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_category_merchant_name (merchant_id, name, deleted),
    KEY idx_category_merchant_sort (merchant_id, sort),
    KEY idx_category_merchant_status (merchant_id, status),
    CONSTRAINT fk_category_merchant FOREIGN KEY (merchant_id) REFERENCES merchant (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_category_status CHECK (status IN ('ENABLED', 'DISABLED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '菜品分类';

-- ----------------------------------------------------------------------------
-- 4. dish — 菜品（库存 + 销量 + 上下架 + 逻辑删除）
-- ----------------------------------------------------------------------------
CREATE TABLE dish (
    id             BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    merchant_id    BIGINT        NOT NULL                COMMENT '所属商家 merchant.id',
    category_id    BIGINT        NULL                    COMMENT '所属分类 category.id（分类删除置空）',
    name           VARCHAR(100)  NOT NULL                COMMENT '菜品名称',
    description    VARCHAR(500)  NULL                    COMMENT '描述',
    price          DECIMAL(10,2) NOT NULL                COMMENT '现价/单价（元）',
    original_price DECIMAL(10,2) NULL                    COMMENT '划线原价（元，可空；须 >= price 由业务校验）',
    image_url      VARCHAR(500)  NULL                    COMMENT '图片 URL（OSS 或 local /files/**）',
    stock          INT           NOT NULL DEFAULT 0      COMMENT '库存（原子扣减，禁止负数）',
    sales          INT           NOT NULL DEFAULT 0      COMMENT '累计销量（API 字段 sales）',
    status         VARCHAR(20)   NOT NULL DEFAULT 'ON_SALE' COMMENT '状态：ON_SALE 在售 / OFF_SHELF 下架',
    sort           INT           NOT NULL DEFAULT 0      COMMENT '排序值（升序）',
    deleted        TINYINT(1)    NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删 / 1 已删',
    version        INT           NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
    created_at     DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at     DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_dish_merchant_category (merchant_id, category_id, status),
    KEY idx_dish_sales (sales),
    CONSTRAINT fk_dish_merchant FOREIGN KEY (merchant_id) REFERENCES merchant (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_dish_category FOREIGN KEY (category_id) REFERENCES category (id) ON DELETE SET NULL ON UPDATE RESTRICT,
    CONSTRAINT chk_dish_price CHECK (price >= 0),
    CONSTRAINT chk_dish_original_price CHECK (original_price IS NULL OR original_price >= 0),
    CONSTRAINT chk_dish_stock CHECK (stock >= 0),
    CONSTRAINT chk_dish_status CHECK (status IN ('ON_SALE', 'OFF_SHELF'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '菜品';

-- ----------------------------------------------------------------------------
-- 5. cart_item — 购物车（服务端持久化，刷新/换端不丢；UNIQUE(user_id, dish_id)）
-- ----------------------------------------------------------------------------
CREATE TABLE cart_item (
    id         BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id    BIGINT      NOT NULL                COMMENT '所属顾客 users.id',
    dish_id    BIGINT      NOT NULL                COMMENT '菜品 dish.id',
    quantity   INT         NOT NULL DEFAULT 1      COMMENT '数量（同一菜品合并为一行）',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_cart_item_user_dish (user_id, dish_id),
    KEY idx_cart_item_dish (dish_id),
    CONSTRAINT fk_cart_item_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_cart_item_dish FOREIGN KEY (dish_id) REFERENCES dish (id) ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT chk_cart_item_quantity CHECK (quantity > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '购物车明细';

-- ----------------------------------------------------------------------------
-- 6. address — 顾客收货地址（逻辑删除，历史订单保留快照）
-- ----------------------------------------------------------------------------
CREATE TABLE address (
    id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id    BIGINT       NOT NULL                COMMENT '所属顾客 users.id',
    consignee  VARCHAR(50)  NOT NULL                COMMENT '收货人',
    phone      VARCHAR(20)  NOT NULL                COMMENT '联系电话',
    province   VARCHAR(50)  NOT NULL DEFAULT ''     COMMENT '省',
    city       VARCHAR(50)  NOT NULL DEFAULT ''     COMMENT '市',
    district   VARCHAR(50)  NOT NULL DEFAULT ''     COMMENT '区/县',
    detail     VARCHAR(255) NOT NULL                COMMENT '详细地址',
    is_default TINYINT(1)   NOT NULL DEFAULT 0      COMMENT '是否默认地址：1 是 / 0 否',
    deleted    TINYINT(1)   NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删 / 1 已删',
    version    INT          NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
    created_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_address_user_default (user_id, is_default),
    CONSTRAINT fk_address_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '顾客收货地址';

-- ----------------------------------------------------------------------------
-- 7. table_info — 桌台（二维码签名盐 / 乐观锁；逻辑删除）
--    UNIQUE(merchant_id, table_no, deleted)：删除后允许重新启用同号桌台，
--    同一商家同一桌号仅能有一条未删除记录。
-- ----------------------------------------------------------------------------
CREATE TABLE table_info (
    id                 BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    merchant_id        BIGINT       NOT NULL                COMMENT '所属商家 merchant.id',
    table_no           VARCHAR(20)  NOT NULL                COMMENT '桌号（商家内唯一，如 A01）',
    name               VARCHAR(50)  NULL                    COMMENT '桌台名称（如 大厅 1 号桌）',
    zone               VARCHAR(50)  NULL                    COMMENT '区域（大厅 / 包间 / 露台）',
    capacity           INT          NOT NULL DEFAULT 4      COMMENT '容纳人数',
    status             VARCHAR(20)  NOT NULL DEFAULT 'FREE' COMMENT '桌台状态：FREE 空闲 / OCCUPIED 使用中',
    current_session_id BIGINT       NULL                    COMMENT '当前有效会话 table_session.id（空闲为 NULL）',
    qr_code_url        VARCHAR(500) NULL                    COMMENT '二维码图片 URL（API qrImageUrl，存 file_object）',
    qr_salt            VARCHAR(32)  NOT NULL                COMMENT '二维码签名盐（重签时更换，旧码立即失效）',
    qr_version         INT          NOT NULL DEFAULT 0      COMMENT '二维码签名版本（独立于乐观锁 version；API qrVersion）',
    qr_signed_at       DATETIME(3)  NULL                    COMMENT '二维码最近重签时间（API signedAt）',
    deleted            TINYINT(1)   NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删 / 1 已删',
    version            INT          NOT NULL DEFAULT 0      COMMENT '乐观锁版本号（并发扫码/绑定）',
    created_at         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_table_info_merchant_no (merchant_id, table_no, deleted),
    KEY idx_table_info_current_session (current_session_id),
    KEY idx_table_info_merchant_status (merchant_id, status),
    CONSTRAINT fk_table_info_merchant FOREIGN KEY (merchant_id) REFERENCES merchant (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_table_info_capacity CHECK (capacity > 0),
    CONSTRAINT chk_table_info_status CHECK (status IN ('FREE', 'OCCUPIED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '桌台';

-- ----------------------------------------------------------------------------
-- 8. table_session — 桌台会话（扫码绑定 → ACTIVE，完成/取消/超时清理 → CLOSED/EXPIRED）
--    小程序请求头 X-Table-Session 携带 session_token，由服务端校验有效性。
-- ----------------------------------------------------------------------------
CREATE TABLE table_session (
    id                BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    table_id          BIGINT      NOT NULL                COMMENT '桌台 table_info.id',
    merchant_id       BIGINT      NOT NULL                COMMENT '所属商家 merchant.id',
    session_token     VARCHAR(64) NOT NULL                COMMENT '会话令牌（X-Table-Session，唯一）',
    status            VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '会话状态：ACTIVE 进行中 / CLOSED 已关闭 / EXPIRED 已过期',
    guest_count       INT         NOT NULL DEFAULT 1      COMMENT '就餐人数',
    opened_by         VARCHAR(64) NULL                    COMMENT '开台来源（OPENID / 手机号 / SCAN）',
    current_order_id  BIGINT      NULL                    COMMENT '当前待处理订单 orders.id（不建外键，避免与 orders 循环依赖）',
    opened_at         DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '开台时间',
    last_order_at     DATETIME(3) NULL                    COMMENT '最近有效下单时间（超时清理依据）',
    closed_at         DATETIME(3) NULL                    COMMENT '关台时间',
    expire_at         DATETIME(3) NOT NULL                COMMENT '会话过期时间（Spring Task 清理依据）',
    version           INT         NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
    created_at        DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at        DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_table_session_token (session_token),
    KEY idx_table_session_table_status (table_id, status),
    KEY idx_table_session_merchant_status (merchant_id, status),
    KEY idx_table_session_status_last_order (status, last_order_at),
    KEY idx_table_session_current_order (current_order_id),
    CONSTRAINT fk_table_session_table FOREIGN KEY (table_id) REFERENCES table_info (id) ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_table_session_merchant FOREIGN KEY (merchant_id) REFERENCES merchant (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_table_session_status CHECK (status IN ('ACTIVE', 'CLOSED', 'EXPIRED')),
    CONSTRAINT chk_table_session_guest CHECK (guest_count > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '桌台会话';

-- table_info.current_session_id ⇄ table_session.id 互相引用，故会话表建好后再补外键
ALTER TABLE table_info
    ADD CONSTRAINT fk_table_info_current_session FOREIGN KEY (current_session_id) REFERENCES table_session (id) ON DELETE SET NULL ON UPDATE RESTRICT;

-- ----------------------------------------------------------------------------
-- 9. coupon — 优惠券模板（管理后台 CRUD；本期仅满减）
-- ----------------------------------------------------------------------------
CREATE TABLE coupon (
    id               BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    name             VARCHAR(100)  NOT NULL                COMMENT '优惠券名称',
    type             VARCHAR(20)   NOT NULL DEFAULT 'FULL_REDUCTION' COMMENT '类型：FULL_REDUCTION 满减（MVP）',
    threshold_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00    COMMENT '使用门槛金额（元）',
    discount_amount  DECIMAL(10,2) NOT NULL DEFAULT 0.00    COMMENT '优惠金额（元，满减类型）',
    start_at         DATETIME(3)   NOT NULL                COMMENT '生效时间',
    end_at           DATETIME(3)   NOT NULL                COMMENT '失效时间',
    total            INT           NOT NULL DEFAULT 0      COMMENT '发放总量',
    remaining        INT           NOT NULL DEFAULT 0      COMMENT '剩余可领数量（原子扣减）',
    status           VARCHAR(20)   NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED 启用 / DISABLED 停用',
    version          INT           NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
    created_at       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at       DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_coupon_status_time (status, start_at, end_at),
    CONSTRAINT chk_coupon_type CHECK (type IN ('FULL_REDUCTION')),
    CONSTRAINT chk_coupon_amount CHECK (threshold_amount >= 0 AND discount_amount >= 0),
    CONSTRAINT chk_coupon_stock CHECK (remaining >= 0 AND total >= 0),
    CONSTRAINT chk_coupon_status CHECK (status IN ('ENABLED', 'DISABLED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '优惠券模板';

-- ----------------------------------------------------------------------------
-- 10. user_coupon — 用户领取的优惠券（UNIQUE(user_id, coupon_id) 兜住每人限领 1 张）
-- ----------------------------------------------------------------------------
CREATE TABLE user_coupon (
    id          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id     BIGINT      NOT NULL                COMMENT '所属用户 users.id',
    coupon_id   BIGINT      NOT NULL                COMMENT '优惠券 coupon.id',
    status      VARCHAR(20) NOT NULL DEFAULT 'UNUSED' COMMENT '状态：UNUSED 未使用 / USED 已使用 / EXPIRED 已过期',
    order_id    BIGINT      NULL                    COMMENT '使用的订单 orders.id',
    received_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '领取时间',
    used_at     DATETIME(3) NULL                    COMMENT '使用时间',
    version     INT         NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
    created_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_coupon_user_coupon (user_id, coupon_id),
    KEY idx_user_coupon_user_status (user_id, status),
    KEY idx_user_coupon_coupon (coupon_id),
    CONSTRAINT fk_user_coupon_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_user_coupon_coupon FOREIGN KEY (coupon_id) REFERENCES coupon (id) ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT chk_user_coupon_status CHECK (status IN ('UNUSED', 'USED', 'EXPIRED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '用户优惠券';

-- ----------------------------------------------------------------------------
-- 11. orders — 订单主表（外卖 + 堂食共用，order_type 区分；两套状态机由应用层保证）
--     pay_amount = total_amount - discount_amount + delivery_fee（外卖），报表/展示以此为准
-- ----------------------------------------------------------------------------
CREATE TABLE orders (
    id                BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    order_no          VARCHAR(32)   NOT NULL                COMMENT '订单号（唯一，如 20261027123000001）',
    user_id           BIGINT        NOT NULL                COMMENT '下单顾客 users.id',
    merchant_id       BIGINT        NOT NULL                COMMENT '商家 merchant.id',
    order_type        VARCHAR(20)   NOT NULL                COMMENT '订单类型：TAKEOUT 外卖 / DINE_IN 堂食',
    status            VARCHAR(30)   NOT NULL DEFAULT 'PENDING_PAYMENT' COMMENT '订单状态（见 docs/03-data-model.md §7 状态机）',
    table_id          BIGINT        NULL                    COMMENT '堂食桌台 table_info.id（外卖为 NULL）',
    table_no          VARCHAR(20)   NULL                    COMMENT '桌号快照（堂食）',
    table_session_id  BIGINT        NULL                    COMMENT '堂食会话 table_session.id',
    address_id        BIGINT        NULL                    COMMENT '外卖地址 address.id',
    consignee         VARCHAR(50)   NULL                    COMMENT '收货人快照',
    consignee_phone   VARCHAR(20)   NULL                    COMMENT '收货电话快照',
    delivery_address  VARCHAR(255)  NULL                    COMMENT '收货地址快照（省市区+详细地址拼接）',
    total_amount      DECIMAL(10,2) NOT NULL DEFAULT 0.00    COMMENT '商品总金额（元）',
    discount_amount   DECIMAL(10,2) NOT NULL DEFAULT 0.00    COMMENT '优惠金额（元）',
    delivery_fee      DECIMAL(10,2) NOT NULL DEFAULT 0.00    COMMENT '配送费（元，堂食为 0）',
    pay_amount        DECIMAL(10,2) NOT NULL DEFAULT 0.00    COMMENT '真实应付金额（元，报表/展示口径）',
    coupon_id         BIGINT        NULL                    COMMENT '使用的优惠券模板 coupon.id（冗余便于统计）',
    user_coupon_id    BIGINT        NULL                    COMMENT '使用的用户优惠券 user_coupon.id',
    remark            VARCHAR(255)  NULL                    COMMENT '顾客备注',
    cancel_reason     VARCHAR(255)  NULL                    COMMENT '取消/拒单原因（API cancelReason）',
    timeout_at        DATETIME(3)   NULL                    COMMENT '支付超时时间（Spring Task 扫描）',
    paid_at           DATETIME(3)   NULL                    COMMENT '支付完成时间',
    finished_at       DATETIME(3)   NULL                    COMMENT '完成/取消/拒单时间',
    version           INT           NOT NULL DEFAULT 0      COMMENT '乐观锁版本号（状态机并发控制）',
    created_at        DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at        DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_orders_order_no (order_no),
    KEY idx_orders_user_status_created (user_id, status, created_at),
    KEY idx_orders_merchant_status_created (merchant_id, status, created_at),
    KEY idx_orders_status_created (status, created_at),
    KEY idx_orders_status_timeout (status, timeout_at),
    KEY idx_orders_type_created (order_type, created_at),
    KEY idx_orders_table_session (table_session_id),
    CONSTRAINT fk_orders_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_orders_merchant FOREIGN KEY (merchant_id) REFERENCES merchant (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_orders_table FOREIGN KEY (table_id) REFERENCES table_info (id) ON DELETE SET NULL ON UPDATE RESTRICT,
    CONSTRAINT fk_orders_table_session FOREIGN KEY (table_session_id) REFERENCES table_session (id) ON DELETE SET NULL ON UPDATE RESTRICT,
    CONSTRAINT fk_orders_address FOREIGN KEY (address_id) REFERENCES address (id) ON DELETE SET NULL ON UPDATE RESTRICT,
    CONSTRAINT chk_orders_type CHECK (order_type IN ('TAKEOUT', 'DINE_IN')),
    CONSTRAINT chk_orders_status CHECK (status IN ('PENDING_PAYMENT', 'PAID', 'ACCEPTED', 'MAKING', 'DELIVERING', 'SERVED', 'COMPLETED', 'CANCELLED', 'REJECTED')),
    CONSTRAINT chk_orders_amount CHECK (total_amount >= 0 AND discount_amount >= 0 AND delivery_fee >= 0 AND pay_amount >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '订单主表';

-- user_coupon.order_id → orders.id 回填外键（两表互相引用，订单表建好后再补）
ALTER TABLE user_coupon
    ADD CONSTRAINT fk_user_coupon_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE SET NULL ON UPDATE RESTRICT;

-- orders.coupon_id / user_coupon_id 外键（优惠券删除后订单保留快照金额）
ALTER TABLE orders
    ADD CONSTRAINT fk_orders_coupon FOREIGN KEY (coupon_id) REFERENCES coupon (id) ON DELETE SET NULL ON UPDATE RESTRICT,
    ADD CONSTRAINT fk_orders_user_coupon FOREIGN KEY (user_coupon_id) REFERENCES user_coupon (id) ON DELETE SET NULL ON UPDATE RESTRICT;

-- ----------------------------------------------------------------------------
-- 12. order_item — 订单明细（菜品名称/图片/单价快照，菜品删除不丢历史）
-- ----------------------------------------------------------------------------
CREATE TABLE order_item (
    id         BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    order_id   BIGINT        NOT NULL                COMMENT '订单 orders.id',
    dish_id    BIGINT        NULL                    COMMENT '菜品 dish.id（删除置空）',
    dish_name  VARCHAR(100)  NOT NULL                COMMENT '菜品名称快照',
    dish_image VARCHAR(500)  NULL                    COMMENT '菜品图片快照',
    price      DECIMAL(10,2) NOT NULL                COMMENT '下单单价快照（元）',
    quantity   INT           NOT NULL                COMMENT '数量',
    subtotal   DECIMAL(10,2) NOT NULL                COMMENT '小计 = price × quantity（元）',
    created_at DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_order_item_order (order_id),
    KEY idx_order_item_dish (dish_id),
    CONSTRAINT fk_order_item_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_order_item_dish FOREIGN KEY (dish_id) REFERENCES dish (id) ON DELETE SET NULL ON UPDATE RESTRICT,
    CONSTRAINT chk_order_item_quantity CHECK (quantity > 0),
    CONSTRAINT chk_order_item_price CHECK (price >= 0 AND subtotal >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '订单明细';

-- ----------------------------------------------------------------------------
-- 13. order_status_log — 订单状态流转日志（时间线）
-- ----------------------------------------------------------------------------
CREATE TABLE order_status_log (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    order_id      BIGINT       NOT NULL                COMMENT '订单 orders.id',
    from_status   VARCHAR(30)  NULL                    COMMENT '原状态（创建时为空）',
    to_status     VARCHAR(30)  NOT NULL                COMMENT '目标状态',
    operator_id   BIGINT       NULL                    COMMENT '操作人 users.id（系统任务为 NULL）',
    operator_role VARCHAR(20)  NULL                    COMMENT '操作人角色：ADMIN / MERCHANT / CUSTOMER / SYSTEM',
    remark        VARCHAR(255) NULL                    COMMENT '备注（取消原因等）',
    created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '发生时间',
    PRIMARY KEY (id),
    KEY idx_order_status_log_order_time (order_id, id),
    KEY idx_order_status_log_to_status (to_status, created_at),
    CONSTRAINT fk_order_status_log_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '订单状态流转日志';

-- ----------------------------------------------------------------------------
-- 14. payment — 支付单（真实订单金额 vs 模拟扣款 0.01 元；回调幂等键 pay_no）
-- ----------------------------------------------------------------------------
CREATE TABLE payment (
    id             BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    pay_no         VARCHAR(32)   NOT NULL                COMMENT '支付单号（唯一，回调幂等键）',
    order_id       BIGINT        NOT NULL                COMMENT '订单 orders.id',
    user_id        BIGINT        NOT NULL                COMMENT '支付用户 users.id',
    order_amount   DECIMAL(10,2) NOT NULL                COMMENT '订单真实应付金额（元）',
    paid_amount    DECIMAL(10,2) NOT NULL DEFAULT 0.00   COMMENT '实际扣款金额（元，模拟支付 = min(0.01, order_amount)）',
    channel        VARCHAR(20)   NOT NULL DEFAULT 'MOCK' COMMENT '支付渠道：MOCK（MVP 模拟支付）',
    status         VARCHAR(20)   NOT NULL DEFAULT 'PENDING' COMMENT '支付状态：PENDING / SUCCESS / FAILED / REFUNDED',
    transaction_no VARCHAR(64)   NULL                    COMMENT '第三方交易号（模拟网关生成）',
    expire_at      DATETIME(3)   NULL                    COMMENT '支付单过期时间（默认 = 下单后 15 分钟；API expireAt）',
    paid_at        DATETIME(3)   NULL                    COMMENT '支付成功时间',
    callback_at    DATETIME(3)   NULL                    COMMENT '回调到达时间',
    version        INT           NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
    created_at     DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at     DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_payment_pay_no (pay_no),
    KEY idx_payment_order (order_id),
    KEY idx_payment_status_created (status, created_at),
    CONSTRAINT fk_payment_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_payment_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_payment_amount CHECK (order_amount >= 0 AND paid_amount >= 0),
    CONSTRAINT chk_payment_paid_le_order CHECK (paid_amount <= order_amount),
    CONSTRAINT chk_payment_status CHECK (status IN ('PENDING', 'SUCCESS', 'FAILED', 'REFUNDED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '支付单';

-- ----------------------------------------------------------------------------
-- 15. wx_user — 微信小程序用户（openid 唯一键保证登录幂等；session_key 绝不下发）
-- ----------------------------------------------------------------------------
CREATE TABLE wx_user (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    openid      VARCHAR(64)  NOT NULL                COMMENT '微信 openid（唯一）',
    unionid     VARCHAR(64)  NULL                    COMMENT '微信 unionid',
    session_key VARCHAR(128) NULL                    COMMENT '微信 session_key（服务端保存，绝不下发前端）',
    nickname    VARCHAR(100) NULL                    COMMENT '昵称',
    avatar_url  VARCHAR(500) NULL                    COMMENT '头像 URL',
    user_id     BIGINT       NULL                    COMMENT '关联平台顾客账号 users.id',
    version     INT          NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
    created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_wx_user_openid (openid),
    KEY idx_wx_user_user (user_id),
    CONSTRAINT fk_wx_user_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE SET NULL ON UPDATE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '微信小程序用户';

-- ----------------------------------------------------------------------------
-- 16. evaluation — 订单评价（一单一评；image_urls 逗号分隔，API 输出 images: string[]）
-- ----------------------------------------------------------------------------
CREATE TABLE evaluation (
    id          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    order_id    BIGINT        NOT NULL                COMMENT '订单 orders.id（唯一，一单一评）',
    user_id     BIGINT        NOT NULL                COMMENT '评价顾客 users.id',
    merchant_id BIGINT        NOT NULL                COMMENT '商家 merchant.id',
    rating      TINYINT       NOT NULL                COMMENT '评分 1-5 星',
    content     VARCHAR(500)  NULL                    COMMENT '评价内容',
    image_urls  VARCHAR(1000) NULL                    COMMENT '评价图片 URL，逗号分隔（API 输出 images）',
    reply       VARCHAR(500)  NULL                    COMMENT '商家回复（本期预留）',
    reply_at    DATETIME(3)   NULL                    COMMENT '回复时间（本期预留）',
    status      VARCHAR(20)   NOT NULL DEFAULT 'ENABLED' COMMENT '显示状态：ENABLED 显示 / DISABLED 隐藏',
    version     INT           NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
    created_at  DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at  DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_evaluation_order (order_id),
    KEY idx_evaluation_merchant_created (merchant_id, created_at),
    CONSTRAINT fk_evaluation_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_evaluation_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_evaluation_merchant FOREIGN KEY (merchant_id) REFERENCES merchant (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_evaluation_rating CHECK (rating BETWEEN 1 AND 5),
    CONSTRAINT chk_evaluation_status CHECK (status IN ('ENABLED', 'DISABLED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '订单评价';

-- ----------------------------------------------------------------------------
-- 17. file_object — 文件对象（OSS 直传 / local 本地存储统一登记）
-- ----------------------------------------------------------------------------
CREATE TABLE file_object (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    biz_type      VARCHAR(50)  NOT NULL                COMMENT '业务类型：dish / merchant / avatar（内部 table 存二维码 PNG）',
    object_key    VARCHAR(255) NOT NULL                COMMENT '对象键（biz/yyyy/MM/uuid.ext，唯一）',
    url           VARCHAR(500) NOT NULL                COMMENT '访问 URL（OSS 或 /files/**）',
    original_name VARCHAR(255) NULL                    COMMENT '原始文件名',
    content_type  VARCHAR(100) NULL                    COMMENT 'MIME 类型（jpeg/png/webp 白名单）',
    size          BIGINT       NOT NULL DEFAULT 0      COMMENT '文件大小（字节，≤5MB）',
    storage_type  VARCHAR(20)  NOT NULL DEFAULT 'local' COMMENT '存储类型：local / oss',
    uploader_id   BIGINT       NULL                    COMMENT '上传人 users.id',
    created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_file_object_key (object_key),
    KEY idx_file_object_uploader (uploader_id, created_at),
    KEY idx_file_object_biz (biz_type),
    CONSTRAINT chk_file_object_size CHECK (size >= 0),
    CONSTRAINT chk_file_object_storage CHECK (storage_type IN ('local', 'oss'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '文件对象';

-- ----------------------------------------------------------------------------
-- 18. audit_log — 审计日志（登录、订单状态变更、管理端写操作、二维码重签）
-- ----------------------------------------------------------------------------
CREATE TABLE audit_log (
    id          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id     BIGINT        NULL                    COMMENT '操作人 users.id（未登录为 NULL）',
    username    VARCHAR(50)   NULL                    COMMENT '操作人账号快照',
    role        VARCHAR(20)   NULL                    COMMENT '操作人角色快照',
    action      VARCHAR(50)   NOT NULL                COMMENT '动作：LOGIN / ORDER_STATUS_CHANGE / ADMIN_WRITE / TABLE_QR_RESIGN 等',
    target_type VARCHAR(50)   NULL                    COMMENT '目标类型：USER / MERCHANT / ORDER / DISH / TABLE 等',
    target_id   VARCHAR(64)   NULL                    COMMENT '目标 ID（字符串以兼容多种主键）',
    trace_id    VARCHAR(64)   NULL                    COMMENT '链路追踪 ID（跨端排障）',
    detail      VARCHAR(1000) NULL                    COMMENT '详情（JSON 或文本摘要，禁止敏感信息）',
    ip          VARCHAR(64)   NULL                    COMMENT '客户端 IP',
    user_agent  VARCHAR(255)  NULL                    COMMENT 'User-Agent',
    success     TINYINT(1)    NOT NULL DEFAULT 1      COMMENT '是否成功：1 成功 / 0 失败',
    created_at  DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '发生时间',
    PRIMARY KEY (id),
    KEY idx_audit_log_user_created (user_id, created_at),
    KEY idx_audit_log_action_created (action, created_at),
    KEY idx_audit_log_target (target_type, target_id),
    KEY idx_audit_log_trace (trace_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '审计日志';

-- ----------------------------------------------------------------------------
-- 19. idempotency_record — 幂等记录（下单等写接口的 Idempotency-Key）
-- ----------------------------------------------------------------------------
CREATE TABLE idempotency_record (
    id              BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    idempotency_key VARCHAR(64) NOT NULL                COMMENT '幂等键（Idempotency-Key 请求头值，唯一键保证幂等）',
    user_id         BIGINT      NULL                    COMMENT '请求用户 users.id',
    endpoint        VARCHAR(100) NOT NULL DEFAULT ''    COMMENT '接口路径（如 POST /api/v1/orders）',
    status          VARCHAR(20) NOT NULL DEFAULT 'PROCESSING' COMMENT '处理状态：PROCESSING / SUCCESS / FAILED',
    order_id        BIGINT      NULL                    COMMENT '首次成功生成的订单 orders.id',
    response_code   INT         NULL                    COMMENT '首次响应业务码',
    response_body   MEDIUMTEXT  NULL                    COMMENT '首次响应体快照（重复请求直接返回）',
    expires_at      DATETIME(3) NULL                    COMMENT '记录过期时间（24h，供清理任务）',
    created_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_idempotency_record_key (idempotency_key),
    KEY idx_idempotency_record_user (user_id, created_at),
    KEY idx_idempotency_record_expires (expires_at),
    CONSTRAINT chk_idempotency_record_status CHECK (status IN ('PROCESSING', 'SUCCESS', 'FAILED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '幂等记录';
