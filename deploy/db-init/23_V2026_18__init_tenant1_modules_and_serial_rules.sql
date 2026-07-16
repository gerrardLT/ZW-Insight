-- V2026_18: 初始化默认租户(id=1)的功能模块与编号规则
--
-- 背景：
--   V2026_17 将 admin 归属到默认租户 id=1 后，admin 的请求开始携带 tenant_id=1，
--   从而触发两个此前因 tenant_id=NULL 而被绕过的校验：
--     1) TenantModuleInterceptor：sys_tenant.modules 为空 → 拦截业务模块路径返回 403
--        「您的租户未开通该功能模块」（budget/purchase/labor/material/machine/subcontract/
--         site/finance/hr/tender/price-compare/dashboard 全部被拦）。
--     2) SerialNumberService.generate：serial_number_rule 无该租户规则 → 抛
--        「未配置编号规则: PROJECT/CONTRACT/PURCHASE_CONTRACT/FUND_TRANSFER」，
--        导致创建项目/合同/采购合同/资金调拨 500。
--
-- 方案：
--   给默认租户 id=1 开通全部 12 个功能模块（与 TenantModuleEnum 完全一致），
--   并补齐 4 类编号规则种子数据。均为幂等写法，可安全重复执行。

-- 1. 为默认租户开通全部功能模块（与 TenantModuleEnum.values() 一致）
UPDATE sys_tenant
SET modules = JSON_ARRAY(
        'TENDER', 'BUDGET', 'PURCHASE', 'LABOR', 'MATERIAL', 'MACHINE',
        'SUBCONTRACT', 'SITE', 'FINANCE', 'HR', 'PRICE_COMPARE', 'DASHBOARD'
    )
WHERE id = 1;

-- 2. 补齐默认租户的编号规则（业务类型来自各 Service 的 serialNumberService.generate(...) 调用）
INSERT INTO serial_number_rule (id, business_type, rule_prefix, date_format, seq_length, reset_period, description, tenant_id, created_at, updated_at, deleted, version)
SELECT 900001, 'PROJECT', 'PRJ', 'yyyyMMdd', 4, 'MONTH', '项目编号', 1, NOW(), NOW(), 0, 0
WHERE NOT EXISTS (SELECT 1 FROM serial_number_rule WHERE business_type = 'PROJECT' AND tenant_id = 1);

INSERT INTO serial_number_rule (id, business_type, rule_prefix, date_format, seq_length, reset_period, description, tenant_id, created_at, updated_at, deleted, version)
SELECT 900002, 'CONTRACT', 'HT', 'yyyyMMdd', 4, 'MONTH', '施工合同编号', 1, NOW(), NOW(), 0, 0
WHERE NOT EXISTS (SELECT 1 FROM serial_number_rule WHERE business_type = 'CONTRACT' AND tenant_id = 1);

INSERT INTO serial_number_rule (id, business_type, rule_prefix, date_format, seq_length, reset_period, description, tenant_id, created_at, updated_at, deleted, version)
SELECT 900003, 'PURCHASE_CONTRACT', 'CG', 'yyyyMMdd', 4, 'MONTH', '采购合同编号', 1, NOW(), NOW(), 0, 0
WHERE NOT EXISTS (SELECT 1 FROM serial_number_rule WHERE business_type = 'PURCHASE_CONTRACT' AND tenant_id = 1);

INSERT INTO serial_number_rule (id, business_type, rule_prefix, date_format, seq_length, reset_period, description, tenant_id, created_at, updated_at, deleted, version)
SELECT 900004, 'FUND_TRANSFER', 'ZJ', 'yyyyMMdd', 4, 'MONTH', '资金调拨编号', 1, NOW(), NOW(), 0, 0
WHERE NOT EXISTS (SELECT 1 FROM serial_number_rule WHERE business_type = 'FUND_TRANSFER' AND tenant_id = 1);
