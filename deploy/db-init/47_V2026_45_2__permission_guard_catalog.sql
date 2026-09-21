-- ============================================================
-- 权限守卫系统性修复：权限目录扩充 + 角色授权（修复走查 Critical #1/#2）
-- 背景：audit-reports/browser-role-walkthrough-2026-08-21.md
--   PermissionInterceptor 为 opt-in（无 @RequiresPermission 即放行），
--   而业务角色 90061-90065 权限码为空 → 本次补模块级视图码 + 写按钮码。
-- 设计：
--   1. 读权限 = 模块级视图码 {module}:view 挂 DIR 级菜单 permission 列，
--      现有 sys_role_menu 授权矩阵自动映射为 API 读权限（菜单收敛=读收敛）。
--   2. 级联依赖例外（project/basedata 下拉被跨模块页面调用）：
--      用隐藏 MENU 行（hidden=1，同 302/303 先例，不进侧边栏）承载视图码，
--      避免直接绑定 DIR 3/18 导致业务角色侧边栏新增目录。
--   3. 写权限沿用按钮级命名 {模块}:{资源}:{动作}，SUPER_ADMIN 拦截器豁免，
--      绑定仅补目录完整性与 UI 授权参考。
-- 幂等：UPDATE 可重复执行，INSERT 全部 IGNORE。ID 段 20101-20199
--      （高于 36 号迁移的 20001-20081，低于演示数据 90001+）。
-- 回滚：
--   UPDATE sys_menu SET permission=NULL WHERE id BETWEEN 1 AND 19;
--   DELETE FROM sys_role_menu WHERE id BETWEEN 20101 AND 20199;
--   DELETE FROM sys_menu WHERE id BETWEEN 20101 AND 20199;
-- ============================================================

-- 1) 模块级视图码：挂到一级目录/首页/看板（id 1-19）
UPDATE sys_menu SET permission = 'dashboard:view'         WHERE id = 1;
UPDATE sys_menu SET permission = 'system:view'            WHERE id = 2;
UPDATE sys_menu SET permission = 'project:view'           WHERE id = 3;
UPDATE sys_menu SET permission = 'contract:view'          WHERE id = 4;
UPDATE sys_menu SET permission = 'finance:view'           WHERE id = 5;
UPDATE sys_menu SET permission = 'budget:view'            WHERE id = 6;
UPDATE sys_menu SET permission = 'purchase:view'          WHERE id = 7;
UPDATE sys_menu SET permission = 'labor:view'             WHERE id = 8;
UPDATE sys_menu SET permission = 'material:view'          WHERE id = 9;
UPDATE sys_menu SET permission = 'machine:view'           WHERE id = 10;
UPDATE sys_menu SET permission = 'subcontract:view'       WHERE id = 11;
UPDATE sys_menu SET permission = 'site:view'              WHERE id = 12;
UPDATE sys_menu SET permission = 'tender:view'            WHERE id = 13;
UPDATE sys_menu SET permission = 'hr:view'                WHERE id = 14;
UPDATE sys_menu SET permission = 'archive:view'           WHERE id = 15;
UPDATE sys_menu SET permission = 'workflow:view'          WHERE id = 16;
UPDATE sys_menu SET permission = 'message:view'           WHERE id = 17;
UPDATE sys_menu SET permission = 'basedata:view'          WHERE id = 18;
UPDATE sys_menu SET permission = 'project-dashboard:view' WHERE id = 19;

-- 2) 写按钮权限目录（BUTTON，挂对应二级菜单）
INSERT IGNORE INTO sys_menu (id, menu_name, menu_type, parent_id, permission, status, hidden, weight) VALUES
(20101, '新增通知',   'BUTTON', 1701, 'message:notice:add',        1, 1, 'NORMAL'),
(20102, '发布通知',   'BUTTON', 1701, 'message:notice:publish',    1, 1, 'NORMAL'),
(20103, '推送配置编辑','BUTTON', 1703, 'message:push-config:edit',  1, 1, 'NORMAL'),
(20104, '新增项目',   'BUTTON', 301,  'project:create',            1, 1, 'NORMAL'),
(20105, '编辑项目',   'BUTTON', 301,  'project:edit',              1, 1, 'NORMAL');

-- 3) 级联依赖视图码：隐藏 MENU 行承载（hidden=1 不进侧边栏）
INSERT IGNORE INTO sys_menu (id, menu_name, menu_type, parent_id, permission, status, hidden, weight) VALUES
(20141, '项目数据视图', 'MENU', 3,  'project:view',  1, 1, 'NORMAL'),
(20142, '基础数据视图', 'MENU', 18, 'basedata:view', 1, 1, 'NORMAL');

-- 4) 角色授权
-- 4a) SUPER_ADMIN 目录登记（拦截器已豁免，仅补目录完整性）
INSERT IGNORE INTO sys_role_menu (id, role_id, menu_id) VALUES
(20101, 1, 20101), (20102, 1, 20102), (20103, 1, 20103),
(20104, 1, 20104), (20105, 1, 20105),
(20106, 1, 20141), (20107, 1, 20142);

-- 4b) 项目立项/编辑：项目经理(90061)、商务人员(90064)
INSERT IGNORE INTO sys_role_menu (id, role_id, menu_id) VALUES
(20111, 90061, 20104), (20112, 90061, 20105),
(20113, 90064, 20104), (20114, 90064, 20105);

-- 4c) 级联视图码放宽：财务(90062)/材料(90063)/商务(90064) 需要项目下拉，
--     四类业务角色均需基础数据下拉（供应商/材料字典等）
INSERT IGNORE INTO sys_role_menu (id, role_id, menu_id) VALUES
(20131, 90062, 20141), (20132, 90063, 20141), (20133, 90064, 20141),
(20121, 90061, 20142), (20122, 90062, 20142),
(20123, 90063, 20142), (20124, 90064, 20142);

-- 说明：其余视图码无需新增绑定 —— 角色已持有对应 DIR（见 31/99 号种子
-- sys_role_menu），permission 列填值后经 selectPermissionsByUserId 自动生效：
--   90061 项目经理 → project/contract/budget/labor/material/machine/subcontract/site/message/dashboard/project-dashboard:view
--   90062 财务     → finance/budget/message/dashboard:view + project/basedata:view(4c)
--   90063 材料     → purchase/material/message/dashboard:view + project/basedata:view(4c)
--   90064 商务     → contract/tender/purchase/message/dashboard:view + project/basedata:view(4c)
--   90065 普通员工 → message/dashboard:view（其余模块读接口一律 403）
