-- ============================================
-- P0 整改超期催办 - 数据库迁移脚本
-- 版本: V2026_09
-- 功能: 催办配置管理、催办日志追溯
-- Requirements: 8.1, 8.2, 8.3, 8.4, 10.1, 10.2
-- ============================================

-- ============ 1. 整改催办配置表 ============

CREATE TABLE IF NOT EXISTS biz_reminder_config (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL COMMENT '租户ID',
    interval_days INT NOT NULL DEFAULT 3 COMMENT '催办间隔天数(1-30)',
    escalation_days INT NOT NULL DEFAULT 7 COMMENT '升级通知阈值天数',
    long_overdue_days INT NOT NULL DEFAULT 30 COMMENT '长期超期停止催办天数',
    enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用(0-停用/1-启用)',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='整改催办配置表';

-- ============ 2. 整改催办日志表 ============

CREATE TABLE IF NOT EXISTS biz_reminder_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL COMMENT '租户ID',
    inspection_id BIGINT NOT NULL COMMENT '检查记录ID(biz_inspection.id)',
    receiver_id BIGINT NOT NULL COMMENT '接收人ID',
    reminder_level VARCHAR(20) NOT NULL COMMENT '催办级别(NORMAL/ESCALATED)',
    send_status VARCHAR(20) NOT NULL COMMENT '发送状态(SENT/FAILED)',
    overdue_days INT NOT NULL COMMENT '超期天数',
    sent_at DATETIME COMMENT '发送时间',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_inspection (inspection_id),
    INDEX idx_receiver (receiver_id),
    INDEX idx_tenant_created (tenant_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='整改催办日志表';
