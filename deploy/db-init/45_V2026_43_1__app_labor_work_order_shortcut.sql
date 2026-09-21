-- ============================================================
-- 45_V2026_43__app_labor_work_order_shortcut.sql
-- 移动端「劳务点工」快捷入口注册
-- ============================================================

INSERT IGNORE INTO msg_available_shortcut (id, name, icon, route_path, sort_order, status, create_time) VALUES
(22, '劳务点工', 'icon-labor-work-order', '/pages/labor/work-order/index', 22, 'ENABLED', NOW());
