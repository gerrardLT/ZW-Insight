-- ============================================================
-- V2026_39__app_material_return_shortcut.sql
-- 2026-08-16 孤儿页补全评审修复：移动端「材料退货」快捷入口（Flyway 轨道）
--
-- 与 deploy/db-init/41_V2026_39__app_material_return_shortcut.sql 内容一致：
-- db-init 仅在 MySQL 卷首次初始化执行，已部署环境需 Flyway 轨道才能生效，
-- 否则材料退货页在现网仍不可达（评审 Critical）。INSERT IGNORE 幂等，双轨道安全。
-- ============================================================

INSERT IGNORE INTO msg_available_shortcut (id, name, icon, route_path, sort_order, status, create_time) VALUES
(19, '材料退货', 'icon-material-return', '/pages/material/return', 19, 'ENABLED', NOW());
