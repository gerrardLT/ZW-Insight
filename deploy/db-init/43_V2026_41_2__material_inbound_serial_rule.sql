-- 补丁③（2026-08）：材料入库单号自动生成
-- MaterialInboundService.save 接入 SerialNumberService.generate(MATERIAL_INBOUND)，
-- 需要 serial_number_rule 种子（默认租户 id=1），否则抛未配置编号规则。幂等范式对齐 23_V2026_18 / 29_V2026_24。

INSERT INTO serial_number_rule (id, business_type, rule_prefix, date_format, seq_length, reset_period, description, tenant_id, created_at, updated_at, deleted, version)
SELECT 900006, 'MATERIAL_INBOUND', 'RK', 'yyyyMMdd', 4, 'MONTH', '材料入库单编号', 1, NOW(), NOW(), 0, 0
WHERE NOT EXISTS (SELECT 1 FROM serial_number_rule WHERE business_type = 'MATERIAL_INBOUND' AND tenant_id = 1);
