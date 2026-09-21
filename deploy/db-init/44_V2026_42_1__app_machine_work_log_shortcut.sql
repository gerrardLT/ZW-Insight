-- ============================================================
-- 44_V2026_42__app_machine_work_log_shortcut.sql
-- 移动端「机械台班」快捷入口注册
-- ============================================================

INSERT IGNORE INTO msg_available_shortcut (id, name, icon, route_path, sort_order, status, create_time) VALUES
(21, '机械台班', 'icon-machine-work-log', '/pages/machine/work-log/index', 21, 'ENABLED', NOW());
