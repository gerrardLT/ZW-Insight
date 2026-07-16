-- V2026_17: 为默认管理员 admin 分配真实租户
--
-- 背景：
--   admin(id=1) 初始 tenant_id=NULL（全局管理员）。而多租户插件（MybatisPlusConfig）
--   查询时把 NULL 当作 0（WHERE tenant_id = 0），插入时却直接写入 NULL，二者不一致，
--   导致 admin 创建的所有多租户业务数据都查询不回来（分页 total 恒为 0），卡住联调测试。
--
-- 方案：
--   给 admin 分配一个真实、启用状态的默认租户（id=1），使插入与查询的 tenant_id 一致。
--   登录逻辑 AuthService.checkTenantExpiry 仅在租户 status=2/3 或已过期时报错，
--   此处租户 status=1 且有效期到 2099，登录不受影响。

-- 1. 确保存在启用状态的默认租户（id=1）
INSERT INTO sys_tenant (id, tenant_code, tenant_name, contact_name, status, expire_date, created_at, updated_at)
SELECT 1, 'DEFAULT', '默认租户', '系统管理员', 1, '2099-12-31', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_tenant WHERE id = 1);

-- 2. 将 admin(id=1) 归属到默认租户
UPDATE sys_user SET tenant_id = 1 WHERE id = 1 AND (tenant_id IS NULL OR tenant_id = 0);

-- 3. 超级管理员角色归属默认租户（sys_ 表不参与租户过滤，此处仅保持数据完整性）
UPDATE sys_role SET tenant_id = 1 WHERE id = 1 AND tenant_id IS NULL;
