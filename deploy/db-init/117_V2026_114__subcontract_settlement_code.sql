-- ============================================================
-- V2026_114__subcontract_settlement_code.sql
-- 双轨镜像：内容必须与 zw-insight-server/zw-app/src/main/resources/db/migration/V2026_114__subcontract_settlement_code.sql 一致
-- 背景：分包结算此前无单号，列表以数据库 ID 充当"结算单ID"展示（术语表 §六 禁用）。
--       补 settlement_code 列 + 编号规则种子（SUBCONTRACT_SETTLEMENT / FBJS）+ 存量回填。
-- 事故修订（2026-10-18）：首推时固定 id 900010 撞真库 CHANGE_EVENT（V2026_107 仓外占用）
--   致双机 Flyway 失败崩溃循环；本修订改段内 MAX+1 + id 双守卫，失败迁移未被记录 checksum 可安全改写。
-- 幂等：列存在性检查 + PREPARE；种子 NOT EXISTS（business_type+tenant 唯一键）；
--       回填仅命中 settlement_code IS NULL 的行，重复执行无新命中。
-- 回滚：ALTER TABLE biz_subcontract_settlement DROP COLUMN settlement_code;
--       DELETE FROM serial_number_rule WHERE business_type='SUBCONTRACT_SETTLEMENT' AND tenant_id=1;
-- ============================================================

-- 1) 补列（幂等）
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_subcontract_settlement' AND COLUMN_NAME = 'settlement_code');
SET @ddl = IF(@col_exists = 0,
    'ALTER TABLE biz_subcontract_settlement ADD COLUMN settlement_code VARCHAR(50) NULL COMMENT ''结算单编号（serial_number_rule: SUBCONTRACT_SETTLEMENT/FBJS）'' AFTER contract_id',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2) 编号规则种子（默认租户 1；L4 租户 9999 由 keys/init-test-tenant.sh 全量复制租户 1 规则覆盖）
--    id 取 900001-900999 段内 MAX+1 + id 占用双守卫：真库该段可能被仓外迁移/手工数据占用
--    （2026-10-18 事故：固定 900010 撞 CHANGE_EVENT 致双机 Flyway 失败崩溃循环，V2026_107 仓外占用）
INSERT INTO serial_number_rule (id, business_type, rule_prefix, date_format, seq_length, reset_period, description, tenant_id, created_at, updated_at, deleted, version)
SELECT t.id_seed, 'SUBCONTRACT_SETTLEMENT', 'FBJS', 'yyyyMMdd', 4, 'MONTH', '分包结算编号', 1, NOW(), NOW(), 0, 0
FROM (SELECT IFNULL(MAX(id) + 1, 900001) AS id_seed FROM serial_number_rule WHERE id BETWEEN 900001 AND 900999) t
WHERE NOT EXISTS (SELECT 1 FROM serial_number_rule WHERE business_type = 'SUBCONTRACT_SETTLEMENT' AND tenant_id = 1)
  AND NOT EXISTS (SELECT 1 FROM serial_number_rule r WHERE r.id = t.id_seed);

-- 3) 存量回填：按 id 升序 FBJS+日期+4位序号（仅填 NULL 行；窗口函数需 MySQL 8）
UPDATE biz_subcontract_settlement s
JOIN (
    SELECT id, ROW_NUMBER() OVER (ORDER BY id) AS rn FROM biz_subcontract_settlement WHERE settlement_code IS NULL
) t ON t.id = s.id
SET s.settlement_code = CONCAT('FBJS', DATE_FORMAT(IFNULL(s.created_at, NOW()), '%Y%m%d'), LPAD(t.rn, 4, '0'))
WHERE s.settlement_code IS NULL;
