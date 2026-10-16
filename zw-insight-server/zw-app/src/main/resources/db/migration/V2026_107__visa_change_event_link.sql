-- F2 签证↔变更事件单一生效源（2026-10-16）
-- 模型：变更事件=事实评估（CBS 传导），签证=价款确认（合同累计变更额唯一写入方）。
-- visa.change_event_id 可选关联；一个变更事件至多一张有效签证（deleted=1 释放占用，可换新签证）。
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='biz_change_visa' AND column_name='change_event_id')=0,
 'ALTER TABLE biz_change_visa ADD COLUMN change_event_id BIGINT NULL', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- NULL（无关联或已删除）不参与唯一约束：删除历史可无限保留。
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='biz_change_visa' AND column_name='active_visa_event_link')=0,
 'ALTER TABLE biz_change_visa ADD COLUMN active_visa_event_link VARCHAR(32) GENERATED ALWAYS AS (CASE WHEN deleted=0 AND change_event_id IS NOT NULL THEN CONCAT(''E:'', change_event_id) ELSE NULL END) STORED', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='biz_change_visa' AND index_name='uk_active_visa_event')=0,
 'ALTER TABLE biz_change_visa ADD UNIQUE INDEX uk_active_visa_event (tenant_id, active_visa_event_link)', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
