-- ============================================================
-- V2026_96__schedule_plan_wbs_node.sql
-- 进度计划挂接 WBS 节点 —— 打通「成本侧结构」与「工程侧结构」
--
-- 背景（2026-10-12 进度把控链路梳理）：
--   本系统存在两棵互不相识的树：
--     · biz_project_wbs_node（WBS 结构）—— 成本侧：CBS 成本账户的 wbs_node_id、
--       变更事件的 affected_wbs_ids 都挂在它上面；
--     · biz_schedule_plan（进度计划）—— 工程侧：任务名/计划起止日期/进度，独立树。
--   两者之间**没有任何外键或引用**，因此无法回答
--   「某个工作包的成本花了多少、进度到哪了」——成本与进度无法对账。
--
-- 设计：给进度计划任务加**可空**的 wbs_node_id，把任务可选地挂到 WBS 节点上。
--   可空 → 不破坏存量数据，也不强制所有任务都必须挂 WBS。
--   引用完整性由应用层保障（与全库约定一致：迁移显式不加物理外键）。
--
-- 幂等：加列/加索引均以 information_schema 判定 + PREPARE/EXECUTE 条件执行。
-- 回滚：
--   ALTER TABLE biz_schedule_plan DROP INDEX idx_biz_schedule_plan_wbs_node;
--   ALTER TABLE biz_schedule_plan DROP COLUMN wbs_node_id;
-- ============================================================

-- 1) 加列（幂等）
SET @col_exists := (SELECT COUNT(*) FROM information_schema.columns
                    WHERE table_schema = DATABASE() AND table_name = 'biz_schedule_plan' AND column_name = 'wbs_node_id');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE biz_schedule_plan ADD COLUMN wbs_node_id BIGINT NULL COMMENT ''关联 WBS 节点ID（可空；用于成本-进度对账）''',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2) 加索引（幂等）
SET @idx_exists := (SELECT COUNT(*) FROM information_schema.statistics
                    WHERE table_schema = DATABASE() AND table_name = 'biz_schedule_plan' AND index_name = 'idx_biz_schedule_plan_wbs_node');
SET @ddl2 := IF(@idx_exists = 0,
    'ALTER TABLE biz_schedule_plan ADD INDEX idx_biz_schedule_plan_wbs_node (wbs_node_id)',
    'SELECT 1');
PREPARE stmt2 FROM @ddl2; EXECUTE stmt2; DEALLOCATE PREPARE stmt2;
