-- ============================================================
-- V2026_122__dashboard_menu_consolidation.sql
-- 双轨镜像：内容必须与 zw-insight-server/zw-app/src/main/resources/db/migration/V2026_122__dashboard_menu_consolidation.sql 一致
-- 背景：继 V2026_120（财务 29→8）/V2026_121（三域 42 行收敛）之后：
--   ① 经营总览域再收敛（用户指令）：顶级菜单「项目看板」(id=19) 与「成本主线看板」
--      (id=20201) 并入「经营驾驶舱」Hub 入口（920201）作为 2 个 Tab（前端已带
--      Tab 级权限 project-dashboard:view / dashboard:costcontrol:view）；
--      「首页」(id=1) 为登录落地页保留。
--   ② 权限补强说明：前端 V2026_122 同版本为全部 17 个 Hub 入口路由补显式
--      meta.permission（域级 view 码），并在 HubPage 增加 Tab 级权限过滤
--      （hubTabs[].permission 无码不渲染）——修复 Hub 化后原子路由细粒度
--      守卫在 Tab 内挂载时被绕过的问题。本 SQL 只动菜单与绑定，权限码载体
--      （BUTTON 行与 menu.permission）不变。
-- 探针（推送前双机完成）：id 19/20201 两机均存在、hidden=0、均为顶级(parent=0)。
-- 回滚：UPDATE sys_menu SET hidden=0 WHERE id IN (19, 20201);
--       DELETE rm FROM sys_role_menu rm JOIN sys_menu m ON rm.menu_id=m.id
--         WHERE m.id = 920201 AND rm.id NOT IN (原有绑定)；-- 实操按备份表比对还原
-- ============================================================

-- 1) 项目看板与成本主线看板退出侧边栏（并入经营驾驶舱 Hub，保留行与权限码）
UPDATE sys_menu SET hidden = 1 WHERE id IN (19, 20201) AND menu_type = 'MENU' AND deleted = 0;

-- 2) 经营驾驶舱入口(920201)绑定并集扩充：∪ 项目看板(id=19) ∪ 成本主线看板(id=20201)
--    的绑定角色（原有 cockpit 三件绑定不变；NOT EXISTS 守卫幂等）
INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT (SELECT IFNULL(MAX(id), 0) FROM sys_role_menu) + ROW_NUMBER() OVER (ORDER BY t.role_id), t.role_id, 920201
FROM (
  SELECT DISTINCT rm.role_id FROM sys_role_menu rm
  WHERE rm.menu_id IN (19, 20201)
) t
WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu x WHERE x.role_id = t.role_id AND x.menu_id = 920201);
