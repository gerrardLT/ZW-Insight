-- ============================================================
-- V2026_113__menu_rename_terminology.sql
-- 双轨镜像：内容必须与 zw-insight-server/zw-app/src/main/resources/db/migration/V2026_113__menu_rename_terminology.sql 一致
-- 背景：术语收敛改造（docs/术语表.md / .kiro/specs/finance-clarity-terminology-ui）。
--       菜单名一律为单据/台账名词（对象名），去掉动作词与“管理”后缀。
-- 原则：只 UPDATE menu_name，不动 id/path/permission/parent —— 授权按叶子路径解析，
--       不动 path 即无需重绑既有角色；改名幂等（旧名守卫，重复执行无命中）。
-- 回滚：按需反向 UPDATE；新增的 910100 参见文末注释。
-- ============================================================

-- 1) 菜单改名（幂等：仅当旧名存在且未删除时更新）
UPDATE sys_menu SET menu_name = '项目台账'          WHERE menu_name = '项目报备'     AND deleted = 0;
UPDATE sys_menu SET menu_name = '工作分解结构（WBS）' WHERE menu_name = 'WBS结构'     AND deleted = 0;
UPDATE sys_menu SET menu_name = '项目预算'          WHERE menu_name = '预算编制'     AND deleted = 0;
UPDATE sys_menu SET menu_name = '成本变更单'        WHERE menu_name = '目标成本变更' AND deleted = 0;
UPDATE sys_menu SET menu_name = '成本科目（CBS）'   WHERE menu_name = '成本账户CBS'  AND deleted = 0;
UPDATE sys_menu SET menu_name = '施工总承包合同'    WHERE menu_name = '施工合同'     AND deleted = 0;
UPDATE sys_menu SET menu_name = '竣工结算'          WHERE menu_name = '项目最终结算' AND deleted = 0;
UPDATE sys_menu SET menu_name = '应收账款台账'      WHERE menu_name = '应收台账'     AND deleted = 0;
UPDATE sys_menu SET menu_name = '进项发票'          WHERE menu_name = '收票登记'     AND deleted = 0;
UPDATE sys_menu SET menu_name = '销项发票'          WHERE menu_name = '销项发票台账' AND deleted = 0;
UPDATE sys_menu SET menu_name = '其他支出'          WHERE menu_name = '其他费用付款' AND deleted = 0;
UPDATE sys_menu SET menu_name = '备用金'            WHERE menu_name = '备用金管理'   AND deleted = 0;
UPDATE sys_menu SET menu_name = '质保金台账'        WHERE menu_name = '质保金管理'   AND deleted = 0;
UPDATE sys_menu SET menu_name = '资金科目'          WHERE menu_name = '资金分类科目' AND deleted = 0;
UPDATE sys_menu SET menu_name = '融资借款'          WHERE menu_name = '融资借贷'     AND deleted = 0;
UPDATE sys_menu SET menu_name = '入库单'            WHERE menu_name = '到货入库'     AND deleted = 0;
UPDATE sys_menu SET menu_name = '领料单'            WHERE menu_name = '领料出库'     AND deleted = 0;
UPDATE sys_menu SET menu_name = '库存台账'          WHERE menu_name = '库存查询'     AND deleted = 0;
UPDATE sys_menu SET menu_name = '机械租赁合同'      WHERE menu_name = '机械合同'     AND deleted = 0;
UPDATE sys_menu SET menu_name = '点工单'            WHERE menu_name = '用工单'       AND deleted = 0;
UPDATE sys_menu SET menu_name = '投标记录'          WHERE menu_name = '投标报名'     AND deleted = 0;
UPDATE sys_menu SET menu_name = '质量检查'          WHERE menu_name = '质量安全检查' AND deleted = 0;

-- 2) 新增「安全检查」菜单（原质量安全检查页内本就有质量/安全两个页签，拆为双入口；
--    path=safety-inspection 复用同一组件，initial 页签由前端按路由判定）
INSERT INTO sys_menu (id, menu_name, menu_type, parent_id, path, component, icon, sort_order, status, hidden, permission, created_at, updated_at, deleted, version)
SELECT 910100, '安全检查', 'MENU', 12, 'safety-inspection', 'views/site/inspection', 'Warning', 5, 1, 0, NULL, NOW(), NOW(), 0, 0
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu
    WHERE id = 910100 OR (parent_id = 12 AND path = 'safety-inspection' AND deleted = 0));

-- 3) 角色绑定（V2026_54/112 教训：漏绑 = 侧边栏不可见；id = role_id*100000000 + menu_id 唯一稳定）
-- 3a) SUPER_ADMIN 按 role_code 查 id（双机角色 id 可能分叉，禁止硬编码）
INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT r.id * 100000000 + 910100, r.id, 910100
FROM sys_role r
WHERE r.role_code = 'SUPER_ADMIN'
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu x WHERE x.role_id = r.id AND x.menu_id = 910100);

-- 3b) 复制「质量检查」(1203) 的既有绑定到新菜单，保持两入口可见性一致
INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT rm.role_id * 100000000 + 910100, rm.role_id, 910100
FROM sys_role_menu rm
WHERE rm.menu_id = 1203
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu x WHERE x.role_id = rm.role_id AND x.menu_id = 910100);

-- 回滚参考：
--   DELETE FROM sys_role_menu WHERE menu_id = 910100;
--   DELETE FROM sys_menu WHERE id = 910100;
--   UPDATE sys_menu SET menu_name='项目报备' WHERE menu_name='项目台账' AND deleted=0; （其余反向同理）
