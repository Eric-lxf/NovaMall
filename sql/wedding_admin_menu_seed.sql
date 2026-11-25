-- 吾喜备婚运营管理菜单种子脚本（幂等可重复执行，不会覆盖已有菜单与角色授权）
-- 执行目标数据库: nova_mall
SET NAMES utf8mb4;

START TRANSACTION;

-- 1. 一级目录：吾喜备婚
INSERT INTO sys_menu (
  menu_name, parent_id, order_num, path, component, route_name,
  is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark
)
SELECT
  '吾喜备婚', 0, 8, 'wedding-admin', NULL, 'WeddingAdmin',
  1, 0, 'M', '0', '0', '', 'guide', 'admin', NOW(), '吾喜备婚小程序运营管理目录（通过内网代理调用 wedding-backend）'
WHERE NOT EXISTS (
  SELECT 1 FROM sys_menu WHERE parent_id = 0 AND path = 'wedding-admin'
);

SET @wedding_root_id = (
  SELECT MIN(menu_id) FROM sys_menu
  WHERE parent_id = 0 AND path = 'wedding-admin' AND menu_type = 'M'
);

-- 2. 二级页面：支付订单管理（置于首位）
INSERT INTO sys_menu (
  menu_name, parent_id, order_num, path, component, route_name,
  is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark
)
SELECT
  '支付订单管理', @wedding_root_id, 1, 'orders', 'wedding/orders/index', 'WeddingOrders',
  1, 0, 'C', '0', '0', 'wedding:order:list', 'money', 'admin', NOW(), '吾喜备婚微信虚拟支付订单查询与营收统计'
WHERE @wedding_root_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE parent_id = @wedding_root_id AND path = 'orders'
  );

SET @wedding_orders_id = (
  SELECT MIN(menu_id) FROM sys_menu
  WHERE parent_id = @wedding_root_id AND path = 'orders' AND component = 'wedding/orders/index'
);

-- 3. 二级页面：微信用户管理（保留兼容）
INSERT INTO sys_menu (
  menu_name, parent_id, order_num, path, component, route_name,
  is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark
)
SELECT
  '微信用户管理', @wedding_root_id, 2, 'users', 'wedding/users/index', 'WeddingUsers',
  1, 0, 'C', '0', '0', 'wedding:user:list', 'peoples', 'admin', NOW(), '吾喜备婚小程序微信用户与婚礼空间查看'
WHERE @wedding_root_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE parent_id = @wedding_root_id AND path = 'users'
  );

SET @wedding_users_id = (
  SELECT MIN(menu_id) FROM sys_menu
  WHERE parent_id = @wedding_root_id AND path = 'users' AND component = 'wedding/users/index'
);

INSERT INTO sys_menu (
  menu_name, parent_id, order_num, path, component, route_name,
  is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark
)
SELECT '编辑用户状态与备注', @wedding_users_id, 1, '#', '', '',
       1, 0, 'F', '0', '0', 'wedding:user:edit', '#', 'admin', NOW(), '微信用户状态与备注修改权限'
WHERE @wedding_users_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM sys_menu existing WHERE existing.perms = 'wedding:user:edit'
  );

-- 4. 二级页面：激活码管理（保留兼容）
INSERT INTO sys_menu (
  menu_name, parent_id, order_num, path, component, route_name,
  is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark
)
SELECT
  '激活码管理', @wedding_root_id, 3, 'redeem', 'wedding/redeem/index', 'WeddingRedeem',
  1, 0, 'C', '0', '0', 'wedding:redeem:list', 'password', 'admin', NOW(), '吾喜备婚6位Pro激活码批次生产与核销管理'
WHERE @wedding_root_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE parent_id = @wedding_root_id AND path = 'redeem'
  );

SET @wedding_redeem_id = (
  SELECT MIN(menu_id) FROM sys_menu
  WHERE parent_id = @wedding_root_id AND path = 'redeem' AND component = 'wedding/redeem/index'
);

INSERT INTO sys_menu (
  menu_name, parent_id, order_num, path, component, route_name,
  is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark
)
SELECT desired.label, @wedding_redeem_id, desired.sort_order, '#', '', '',
       1, 0, 'F', '0', '0', desired.permission, '#', 'admin', NOW(), '激活码管理按钮权限'
FROM (
  SELECT '批量生成激活码' AS label, 1 AS sort_order, 'wedding:redeem:create' AS permission
  UNION ALL
  SELECT '作废激活码', 2, 'wedding:redeem:void'
) desired
WHERE @wedding_redeem_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM sys_menu existing WHERE existing.perms = desired.permission
  );

COMMIT;

SELECT @wedding_root_id AS wedding_root_id,
       @wedding_orders_id AS wedding_orders_id,
       @wedding_users_id AS wedding_users_id,
       @wedding_redeem_id AS wedding_redeem_id;
