-- ============================================================
-- P0 接口级功能权限：权限目录种子（修复 Critical #1）
-- 为本期加了 @RequiresPermission 注解的高危接口补齐按钮级权限标识，
-- 命名规范 {模块}:{资源}:{动作}，与后端注解 / 前端 v-permission 共用同一目录。
-- 固定 ID 段 20001-20099（高于现有菜单 id、低于演示数据 90001+），避免冲突。
-- 全部 INSERT IGNORE，可重复执行；SUPER_ADMIN 角色本身在拦截器中豁免，
-- 此处绑定仅用于补全权限目录，便于租户管理员在 UI 中参考授权。
-- ============================================================

-- 按钮级权限（menu_type=BUTTON，挂到对应二级菜单；tenant_id 为空表示全局目录）
INSERT IGNORE INTO sys_menu (id, menu_name, menu_type, parent_id, permission, status, hidden, weight) VALUES
-- 人员管理（parent=202）
(20001, '新增用户',   'BUTTON', 202, 'system:user:add',         1, 1, 'NORMAL'),
(20002, '编辑用户',   'BUTTON', 202, 'system:user:edit',        1, 1, 'NORMAL'),
(20003, '删除用户',   'BUTTON', 202, 'system:user:delete',      1, 1, 'NORMAL'),
(20004, '用户启停',   'BUTTON', 202, 'system:user:status',      1, 1, 'NORMAL'),
(20005, '分配角色',   'BUTTON', 202, 'system:user:assign-role', 1, 1, 'NORMAL'),
(20006, '重置密码',   'BUTTON', 202, 'system:user:reset-pwd',   1, 1, 'NORMAL'),
(20007, '导入用户',   'BUTTON', 202, 'system:user:import',      1, 1, 'NORMAL'),
-- 角色管理（parent=203）
(20011, '新增角色',   'BUTTON', 203, 'system:role:add',         1, 1, 'NORMAL'),
(20012, '编辑角色',   'BUTTON', 203, 'system:role:edit',        1, 1, 'NORMAL'),
(20013, '删除角色',   'BUTTON', 203, 'system:role:delete',      1, 1, 'NORMAL'),
(20014, '分配菜单',   'BUTTON', 203, 'system:role:assign-menu', 1, 1, 'NORMAL'),
(20015, '数据范围',   'BUTTON', 203, 'system:role:data-scope',  1, 1, 'NORMAL'),
-- 菜单管理（parent=204）
(20021, '新增菜单',   'BUTTON', 204, 'system:menu:add',         1, 1, 'NORMAL'),
(20022, '编辑菜单',   'BUTTON', 204, 'system:menu:edit',        1, 1, 'NORMAL'),
(20023, '删除菜单',   'BUTTON', 204, 'system:menu:delete',      1, 1, 'NORMAL'),
-- 机构管理（parent=201）
(20031, '新增机构',   'BUTTON', 201, 'system:org:add',          1, 1, 'NORMAL'),
(20032, '编辑机构',   'BUTTON', 201, 'system:org:edit',         1, 1, 'NORMAL'),
(20033, '删除机构',   'BUTTON', 201, 'system:org:delete',       1, 1, 'NORMAL'),
-- 岗位管理（parent=206）
(20041, '新增岗位',   'BUTTON', 206, 'system:post:add',         1, 1, 'NORMAL'),
(20042, '编辑岗位',   'BUTTON', 206, 'system:post:edit',        1, 1, 'NORMAL'),
(20043, '删除岗位',   'BUTTON', 206, 'system:post:delete',      1, 1, 'NORMAL'),
-- 数据字典（parent=205）
(20051, '新增字典',   'BUTTON', 205, 'system:dict:add',         1, 1, 'NORMAL'),
(20052, '编辑字典',   'BUTTON', 205, 'system:dict:edit',        1, 1, 'NORMAL'),
(20053, '删除字典',   'BUTTON', 205, 'system:dict:delete',      1, 1, 'NORMAL'),
-- 租户管理（平台级，parent=2 系统管理）
(20061, '新增租户',   'BUTTON', 2,   'system:tenant:add',       1, 1, 'PLATFORM'),
(20062, '编辑租户',   'BUTTON', 2,   'system:tenant:edit',      1, 1, 'PLATFORM'),
(20063, '删除租户',   'BUTTON', 2,   'system:tenant:delete',    1, 1, 'PLATFORM'),
(20064, '租户启停',   'BUTTON', 2,   'system:tenant:status',    1, 1, 'PLATFORM'),
(20065, '租户续期',   'BUTTON', 2,   'system:tenant:renew',     1, 1, 'PLATFORM'),
(20066, '租户模块',   'BUTTON', 2,   'system:tenant:modules',   1, 1, 'PLATFORM'),
-- 项目管理（parent=301 项目报备）
(20071, '删除项目',   'BUTTON', 301, 'project:delete',          1, 1, 'NORMAL'),
-- 财务付款（parent=5 财务管理）
(20081, '付款提交审批', 'BUTTON', 5, 'finance:payment:submit',  1, 1, 'NORMAL');

-- 绑定到超级管理员角色（role_id=1），补全权限目录（超管在拦截器中已豁免）
INSERT IGNORE INTO sys_role_menu (id, role_id, menu_id) VALUES
(20001, 1, 20001), (20002, 1, 20002), (20003, 1, 20003), (20004, 1, 20004),
(20005, 1, 20005), (20006, 1, 20006), (20007, 1, 20007),
(20011, 1, 20011), (20012, 1, 20012), (20013, 1, 20013), (20014, 1, 20014), (20015, 1, 20015),
(20021, 1, 20021), (20022, 1, 20022), (20023, 1, 20023),
(20031, 1, 20031), (20032, 1, 20032), (20033, 1, 20033),
(20041, 1, 20041), (20042, 1, 20042), (20043, 1, 20043),
(20051, 1, 20051), (20052, 1, 20052), (20053, 1, 20053),
(20061, 1, 20061), (20062, 1, 20062), (20063, 1, 20063), (20064, 1, 20064), (20065, 1, 20065), (20066, 1, 20066),
(20071, 1, 20071),
(20081, 1, 20081);
