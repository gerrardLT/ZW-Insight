-- ============================================================
-- V2026_85__machine_deep_opt.sql
-- 机械管理模块深度优化（P3-M7，蓝图 docs/deep-opt/07-machine.md 门A已确认版）
--
-- 内容：
--   1) biz_machine_contract 增加 unit_price（计价单价，与 contract_amount 总额分离）、workflow_instance_id
--   2) biz_machine_work_log 增加 contract_id（日志绑定合同，取代按机械名称字符串匹配）
--   3) biz_machine_work_settlement_detail 增加 contract_id、billing_quantity（计价数量，便于审计 小计=单价×数量）
--   4) MACHINE_CONTRACT 编号规则与生产数据修补待有界执行清单，不在本稿自动写入
--   5) 数据修补（门A已确认，线上只读探针核验于 2026-10-08）：
--      D1 原取证 9 条孤儿明细，尚未备份/执行；生产 DML 不放入自动迁移
--      D2 修复结算单 95921 两条明细的列错位（pricing_type 列里存了金额）：
--         小计取原 pricing_type 列的值，明细合计恢复为 260000 = 结算单总额；单价不改，计价方式按能对上的数量推断
--      D3 仅原授权日志 95023；按租户、项目、原状态及种子结算身份限定，不作全表修补
--
-- 幂等：information_schema 守卫 + NOT EXISTS + UPDATE 条件守卫
-- 双轨：deploy/db-init/87_V2026_85__machine_deep_opt.sql 同内容。
-- ============================================================

SET @schema = DATABASE();

-- 1) 合同：单价 + 流程实例
SELECT COUNT(*) INTO @c FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@schema AND TABLE_NAME='biz_machine_contract' AND COLUMN_NAME='unit_price';
SET @s = IF(@c=0, 'ALTER TABLE biz_machine_contract ADD COLUMN unit_price DECIMAL(18,2) NULL COMMENT ''计价单价（台班/月/工作量单位价）'' AFTER contract_amount', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

SELECT COUNT(*) INTO @c FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@schema AND TABLE_NAME='biz_machine_contract' AND COLUMN_NAME='workflow_instance_id';
SET @s = IF(@c=0, 'ALTER TABLE biz_machine_contract ADD COLUMN workflow_instance_id VARCHAR(64) NULL COMMENT ''流程实例ID'' AFTER status', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- 2) 日志：绑定合同
SELECT COUNT(*) INTO @c FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@schema AND TABLE_NAME='biz_machine_work_log' AND COLUMN_NAME='contract_id';
SET @s = IF(@c=0, 'ALTER TABLE biz_machine_work_log ADD COLUMN contract_id BIGINT NULL COMMENT ''机械合同ID'' AFTER project_id', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- 3) 结算明细：合同 + 计价数量
SELECT COUNT(*) INTO @c FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@schema AND TABLE_NAME='biz_machine_work_settlement_detail' AND COLUMN_NAME='contract_id';
SET @s = IF(@c=0, 'ALTER TABLE biz_machine_work_settlement_detail ADD COLUMN contract_id BIGINT NULL COMMENT ''机械合同ID'' AFTER ledger_id', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

SELECT COUNT(*) INTO @c FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@schema AND TABLE_NAME='biz_machine_work_settlement_detail' AND COLUMN_NAME='billing_quantity';
SET @s = IF(@c=0, 'ALTER TABLE biz_machine_work_settlement_detail ADD COLUMN billing_quantity DECIMAL(18,4) NULL COMMENT ''计价数量（台班数/月数/工作量）'' AFTER unit_price', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- 4) 合同编号规则（线上探针：900009 空闲，租户 1 无该业务类型）
INSERT INTO serial_number_rule (id, business_type, rule_prefix, date_format, seq_length, reset_period, description, tenant_id, created_at, updated_at, deleted, version)
SELECT 900009, 'MACHINE_CONTRACT', 'JX', 'yyyyMMdd', 4, 'MONTH', '机械合同编号', 1, NOW(), NOW(), 0, 0
WHERE NOT EXISTS (SELECT 1 FROM serial_number_rule WHERE business_type = 'MACHINE_CONTRACT' AND tenant_id = 1);

-- 5) D1/D2/D3 生产 DML 保持受控，不在无界全表执行；固定 ID 在部署后或由独立受控步骤执行，避免迁移强锁表。
-- D4 不自动绑定存量；D5 45 台测试台账不动；D6 只能经 Flowable 引擎终止。
-- MySQL DDL 隐式提交，禁止在生产用 START TRANSACTION/ROLLBACK 冒充迁移演练。
-- 回滚：新增 nullable 列先保留；应用回退前评估新单据兼容性，禁止删列丢数据。
