-- =============================================================================
-- V2026_99__completion_acceptance_menu.sql
-- 竣工验收页菜单（2026-10-12）
--
-- 背景：补齐「竣工验收无前端入口」缺口时新增了 views/site/completion.vue
--   （接 zw-site 的 CompletionController：GET /page、POST、POST /{id}/submit），
--   使 UI 能把项目推进到 COMPLETED。菜单树由 sys_menu ⋈ sys_role_menu 驱动
--   （GET /system/menu/user），故必须在库里补菜单行，否则线上侧边栏看不到入口。
--   deploy/db-init/99_data-menu.sql 只在全新装库时生效，已上线库靠本迁移补。
--
-- 注意 id 选择：1201/1202/1203 已被现场管理的既有菜单占用（含 1203「质量安全检查」），
--   故取下一空闲 1204；sys_role_menu 取下一空闲 93。
--
-- 幂等：INSERT IGNORE（重复执行报 1062 被忽略）；sys_menu 的 tenant_id 为 NULL，
--   与既有菜单行一致，故不显式写入该列。
-- =============================================================================

INSERT IGNORE INTO sys_menu (id, menu_name, menu_type, parent_id, path, component, icon, sort_order, status, hidden)
VALUES (1204, '竣工验收', 'MENU', 12, 'completion', 'views/site/completion', 'CircleCheck', 4, 1, 0);

-- 授权超级管理员（与 99_data-menu.sql 的 role_id=1 约定一致；其它角色按需另行授权）
INSERT IGNORE INTO sys_role_menu (id, role_id, menu_id) VALUES (93, 1, 1204);
