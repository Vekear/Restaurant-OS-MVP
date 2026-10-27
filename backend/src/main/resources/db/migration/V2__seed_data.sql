-- ============================================================================
-- V2__seed_data.sql
-- 餐厅点餐系统 MVP — 演示种子数据（依赖 V1__schema.sql）
--
-- 演示账号（密码 BCrypt cost=10，明文仅用于演示环境说明）：
--   admin    / admin123     角色 ADMIN
--   merchant / merchant123  角色 MERCHANT（绑定商家「川味小馆」）
--   customer / customer123  角色 CUSTOMER
--
-- 数据规模：1 商家 / 6 分类 / 15 菜品 / 8 桌台 / 2 优惠券 / 1 演示地址 / 2 张已领优惠券
--
-- 说明：
--   * 菜品图片使用 picsum.photos 外链占位图（离线时前端按「图片加载失败」兜底显示）；
--     真实上传的图片由 file_object + OSS/local 存储接管。
--   * 桌台 qr_salt 为固定演示盐，后端按 HMAC_SHA256(TABLE_QR_SECRET, f"{tableId}.{qrSalt}") 生成签名；
--     重签二维码时更换 qr_salt 并 qr_version+1 即可作废旧码，不占用乐观锁 version。
--   * 桌台二维码图片 qr_code_url 留空，由商家端「生成/重签二维码」接口生成后回填。
--   * 种子 ID 固定，便于演示脚本与测试断言；新增数据从当前 AUTO_INCREMENT 继续。
-- ============================================================================

SET NAMES utf8mb4;

-- ----------------------------
-- 1. 用户（三端统一账号）
-- ----------------------------
INSERT INTO users (id, username, password, nickname, phone, avatar_url, role, status) VALUES
    (1, 'admin',    '$2b$10$84N5zyN/1IaqMGY1KOTC9Oe3Ut/RHRVcsRx3y3orF3C4Ih6r8SIsC', '平台管理员', '13800000001', NULL, 'ADMIN',    'ENABLED'),
    (2, 'merchant', '$2b$10$8acg1cLO.YzvoajfVNJQrOLfFksXrgWQjLhLPL0aj5IaiJzgeD.Qu', '川味小馆',   '13800000002', NULL, 'MERCHANT', 'ENABLED'),
    (3, 'customer', '$2b$10$D8IhLoyWLQwmi/WGf2FWfOq1TQ/7YVBJcl7Xxm068FT2T2hkKnz1y', '演示顾客',   '13900000003', NULL, 'CUSTOMER', 'ENABLED');

-- ----------------------------
-- 2. 商家（merchant 账号归属，营业中 + 已审核；配送费 5 元、起送 20 元）
-- ----------------------------
INSERT INTO merchant (id, user_id, name, description, phone, address, logo_url, delivery_fee, min_order_amount, business_status, audit_status, status) VALUES
    (1, 2, '川味小馆', '地道川味家常菜，现点现做，支持外卖与堂食扫码点餐。', '13800000002',
     '四川省成都市武侯区天府大道北段 888 号 1 栋 1 层', NULL, 5.00, 20.00, 'OPEN', 'APPROVED', 'ENABLED');

-- ----------------------------
-- 3. 菜品分类（6 类）
-- ----------------------------
INSERT INTO category (id, merchant_id, name, sort, status, deleted) VALUES
    (1, 1, '招牌推荐', 1, 'ENABLED', 0),
    (2, 1, '热菜',     2, 'ENABLED', 0),
    (3, 1, '凉菜',     3, 'ENABLED', 0),
    (4, 1, '主食',     4, 'ENABLED', 0),
    (5, 1, '汤品',     5, 'ENABLED', 0),
    (6, 1, '饮品',     6, 'ENABLED', 0);

-- ----------------------------
-- 4. 菜品（15 道，含图片占位、划线原价、库存与销量）
-- ----------------------------
INSERT INTO dish (id, merchant_id, category_id, name, description, price, original_price, image_url, stock, sales, status, sort, deleted) VALUES
    (1,  1, 1, '宫保鸡丁', '鸡腿肉配油酥花生，酸甜微辣，招牌下饭菜。',       32.00, 38.40, 'https://picsum.photos/seed/restaurant-dish-01/640/480', 100, 128, 'ON_SALE', 1, 0),
    (2,  1, 1, '水煮牛肉', '嫩滑牛肉片，麻辣鲜香，配黄豆芽垫底。',           58.00, 69.60, 'https://picsum.photos/seed/restaurant-dish-02/640/480',  60,  96, 'ON_SALE', 2, 0),
    (3,  1, 2, '麻婆豆腐', '石磨嫩豆腐，郫县豆瓣调味，麻、辣、烫、香。',       22.00, 26.40, 'https://picsum.photos/seed/restaurant-dish-03/640/480', 120, 210, 'ON_SALE', 1, 0),
    (4,  1, 2, '回锅肉',   '二刀肉配蒜苗，肥而不腻，经典川味。',             38.00, 45.60, 'https://picsum.photos/seed/restaurant-dish-04/640/480',  80, 150, 'ON_SALE', 2, 0),
    (5,  1, 2, '鱼香肉丝', '酸甜鱼香味，配木耳与笋丝，超级下饭。',           28.00, 33.60, 'https://picsum.photos/seed/restaurant-dish-05/640/480',  90, 175, 'ON_SALE', 3, 0),
    (6,  1, 3, '口水鸡',   '农家土鸡冷吃，红油麻辣，撒花生碎与葱花。',       32.00, 38.40, 'https://picsum.photos/seed/restaurant-dish-06/640/480',  50,  88, 'ON_SALE', 1, 0),
    (7,  1, 3, '拍黄瓜',   '清爽脆嫩，蒜香微辣，解腻小菜。',                 12.00, 14.40, 'https://picsum.photos/seed/restaurant-dish-07/640/480', 200, 240, 'ON_SALE', 2, 0),
    (8,  1, 3, '夫妻肺片', '牛杂拼盘，红油香辣，川味凉菜代表。',             36.00, 43.20, 'https://picsum.photos/seed/restaurant-dish-08/640/480',  40,  66, 'ON_SALE', 3, 0),
    (9,  1, 4, '蛋炒饭',   '粒粒分明，蛋香浓郁，配葱花。',                   16.00, 19.20, 'https://picsum.photos/seed/restaurant-dish-09/640/480', 150, 320, 'ON_SALE', 1, 0),
    (10, 1, 4, '担担面',   '细面配肉臊，芝麻酱与红油交织，麻辣鲜香。',       18.00, 21.60, 'https://picsum.photos/seed/restaurant-dish-10/640/480', 120, 280, 'ON_SALE', 2, 0),
    (11, 1, 4, '酸辣粉',   '红薯粉爽滑，酸辣开胃，配花生与香菜。',           14.00, 16.80, 'https://picsum.photos/seed/restaurant-dish-11/640/480', 120, 190, 'ON_SALE', 3, 0),
    (12, 1, 5, '番茄蛋汤', '酸甜番茄配嫩滑蛋花，家常暖胃汤。',               15.00, 18.00, 'https://picsum.photos/seed/restaurant-dish-12/640/480',  80,  70, 'ON_SALE', 1, 0),
    (13, 1, 5, '酸辣汤',   '木耳、豆腐、笋丝，胡椒酸辣，开胃暖身。',         15.00, 18.00, 'https://picsum.photos/seed/restaurant-dish-13/640/480',  80,  65, 'ON_SALE', 2, 0),
    (14, 1, 6, '酸梅汤',   '古法熬制，冰镇后饮用更佳，解辣消暑。',           8.00,  9.60,  'https://picsum.photos/seed/restaurant-dish-14/640/480', 300, 400, 'ON_SALE', 1, 0),
    (15, 1, 6, '红糖冰粉', '手搓冰粉配红糖浆、花生碎与山楂片。',             10.00, 12.00, 'https://picsum.photos/seed/restaurant-dish-15/640/480', 200, 260, 'ON_SALE', 2, 0);

-- ----------------------------
-- 5. 桌台（8 桌：A01–A08，空闲状态，含二维码签名盐；二维码由商家端生成）
-- ----------------------------
INSERT INTO table_info (id, merchant_id, table_no, name, zone, capacity, status, current_session_id, qr_code_url, qr_salt, qr_version, deleted, version) VALUES
    (1, 1, 'A01', '大厅 1 号桌', '大厅', 2, 'FREE', NULL, NULL, 'a01c4f7b9e2d6318', 1, 0, 0),
    (2, 1, 'A02', '大厅 2 号桌', '大厅', 2, 'FREE', NULL, NULL, 'b02d5a8c0f3e7429', 1, 0, 0),
    (3, 1, 'A03', '大厅 3 号桌', '大厅', 4, 'FREE', NULL, NULL, 'c03e6b9d1a4f8530', 1, 0, 0),
    (4, 1, 'A04', '大厅 4 号桌', '大厅', 4, 'FREE', NULL, NULL, 'd04f7c0e2b5a9641', 1, 0, 0),
    (5, 1, 'A05', '包间 1 号桌', '包间', 6, 'FREE', NULL, NULL, 'e0508d1f3c6b0752', 1, 0, 0),
    (6, 1, 'A06', '包间 2 号桌', '包间', 6, 'FREE', NULL, NULL, 'f0619e2a4d7c1863', 1, 0, 0),
    (7, 1, 'A07', '露台 1 号桌', '露台', 8, 'FREE', NULL, NULL, 'a0720f3b5e8d2974', 1, 0, 0),
    (8, 1, 'A08', '露台 2 号桌', '露台', 8, 'FREE', NULL, NULL, 'b0831a4c6f9e3085', 1, 0, 0);

-- ----------------------------
-- 6. 优惠券模板（2 张满减券，有效期覆盖演示期）
-- ----------------------------
INSERT INTO coupon (id, name, type, threshold_amount, discount_amount, start_at, end_at, total, remaining, status) VALUES
    (1, '新客满 50 减 10',  'FULL_REDUCTION',  50.00, 10.00, '2025-01-01 00:00:00.000', '2030-12-31 23:59:59.000', 1000, 1000, 'ENABLED'),
    (2, '满 100 减 25 券', 'FULL_REDUCTION', 100.00, 25.00, '2025-01-01 00:00:00.000', '2030-12-31 23:59:59.000',  500,  500, 'ENABLED');

-- ----------------------------
-- 7. 演示顾客已领取的优惠券（便于结算页直接演示优惠）
-- ----------------------------
INSERT INTO user_coupon (id, user_id, coupon_id, status, order_id, received_at) VALUES
    (1, 3, 1, 'UNUSED', NULL, CURRENT_TIMESTAMP(3)),
    (2, 3, 2, 'UNUSED', NULL, CURRENT_TIMESTAMP(3));

-- ----------------------------
-- 8. 演示收货地址（默认地址）
-- ----------------------------
INSERT INTO address (id, user_id, consignee, phone, province, city, district, detail, is_default, deleted) VALUES
    (1, 3, '李小明', '13900000003', '四川省', '成都市', '武侯区', '天府大道北段 888 号 1 栋 101 室', 1, 0);
