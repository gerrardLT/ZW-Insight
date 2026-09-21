-- ============================================================
-- 47_V2026_45__app_cost_control_dashboard_shortcut.sql
-- 移动端「成本控制看板」快捷入口注册
-- ============================================================

INSERT IGNORE INTO msg_available_shortcut (id, name, icon, route_path, sort_order, status, create_time) VALUES
(24, '成本看板', 'icon-cost-control-dashboard', '/pages/project/cost-control/index', 24, 'ENABLED', NOW());
