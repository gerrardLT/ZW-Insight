-- ============================================
-- P1 合同到期提醒日志表 - 数据库迁移脚本
-- 版本: V2026_11
-- 功能: 合同到期提醒日志记录
-- Requirements: 5.1
-- ============================================

CREATE TABLE IF NOT EXISTS biz_contract_expiry_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    tenant_id BIGINT NOT NULL COMMENT '租户ID',
    contract_id BIGINT NOT NULL COMMENT '合同ID',
    contract_table VARCHAR(50) NOT NULL COMMENT '合同表名',
    contract_code VARCHAR(50) COMMENT '合同编号',
    contract_category VARCHAR(20) COMMENT '合同类型',
    level VARCHAR(20) NOT NULL COMMENT '提醒级别（UPCOMING/URGENT）',
    remaining_days INT COMMENT '剩余天数',
    notify_user_id BIGINT COMMENT '通知人ID',
    notify_status VARCHAR(20) DEFAULT 'SENT' COMMENT '通知状态（SENT/FAILED）',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_contract (contract_id),
    INDEX idx_tenant_date (tenant_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='合同到期提醒日志';
