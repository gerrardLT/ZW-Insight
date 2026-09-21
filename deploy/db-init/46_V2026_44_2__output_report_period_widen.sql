-- =============================================================================
-- 46_V2026_44__output_report_period_widen.sql
-- biz_output_report.report_period 扩容 VARCHAR(20)→VARCHAR(32)
--
-- 背景（2026-08-21 台账缺口#2 解除配套）：产值 DELETE 守卫为
-- 「DRAFT/REJECTED 或 E2eTestGuard 前缀旁路」，E2E 清理依赖 reportPeriod 携带
-- E2E_TEST_ 前缀（如 E2E_TEST_1755786000000_2026-08，26 字符），超出
-- VARCHAR(20) 导致 INSERT 1406 裸 500。扩至 32 容纳前缀+毫秒时间戳+期间。
-- 幂等：MODIFY COLUMN 重复执行无副作用。
-- =============================================================================
ALTER TABLE biz_output_report
    MODIFY COLUMN report_period VARCHAR(32) COMMENT '报告期间（如2024-01）';
