-- ============================================================
-- 41_V2026_39__app_material_return_shortcut.sql
-- 2026-08-16 P3 方向2 孤儿页补全：移动端「材料退货」快捷入口
--
-- 背景：app 端 material/return.vue（材料退货）补全并注册路由后，
-- msg_available_shortcut 字典缺少对应入口（既有 id 1-18 未含材料退货）。
-- 补一条字典数据（id=19，沿用 20_V2026_14 的 INSERT IGNORE 幂等风格），
-- 用户即可在首页「常用功能-编辑」中添加该入口。
-- ============================================================

INSERT IGNORE INTO msg_available_shortcut (id, name, icon, route_path, sort_order, status, create_time) VALUES
(19, '材料退货', 'icon-material-return', '/pages/material/return', 19, 'ENABLED', NOW());
