-- P1: 机械工作量结算 - 添加结算状态字段
-- 用于追踪工作日志是否已被结算单覆盖

ALTER TABLE biz_machine_work_log
    ADD COLUMN IF NOT EXISTS settlement_status VARCHAR(20) DEFAULT 'UNSETTLED'
    COMMENT '结算状态（UNSETTLED-未结算/SETTLED-已结算）';

-- 为结算状态添加索引，支持创建结算单时快速排除已结算记录
CREATE INDEX IF NOT EXISTS idx_machine_work_log_settlement_status
    ON biz_machine_work_log (settlement_status);
