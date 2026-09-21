-- ============================================================
-- 43_V2026_41__app_change_event_shortcut.sql
-- 移动端「变更事件」快捷入口与孤儿页打通
-- ============================================================

INSERT IGNORE INTO msg_available_shortcut (id, name, icon, route_path, sort_order, status, create_time) VALUES
(20, '变更事件', 'icon-change-event', '/pages/contract/change-event/index', 20, 'ENABLED', NOW());
