-- ============================================================
-- 修正超级管理员数据范围为 ALL（配合行级数据权限覆盖扩展 Major #4）
-- 背景：99_data-menu.sql 插入 SUPER_ADMIN 角色时未设 data_scope，取列默认值 'SELF'，
--       且 13_V2026_10 将 NULL 统一置 'SELF'。而 ZwDataPermissionHandler 对超管无豁免，
--       纯按角色 data_scope 过滤 —— 导致超级管理员被 SELF 行级过滤，只能看到本人创建的数据。
--       随着 @DataPermission 覆盖扩展到 labor/machine 等模块，此既存问题会让超管"看不到"他人/演示数据。
-- 修正：超级管理员数据范围应为 ALL（处理器对 ALL 返回 null 不追加过滤，超管可见全部租户数据）。
-- 幂等：按 role_code 定位，可重复执行。
-- ============================================================

UPDATE sys_role SET data_scope = 'ALL' WHERE role_code = 'SUPER_ADMIN';
