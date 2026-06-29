-- ============================================================
-- 功能8：办公用品库存表（如不存在则新建）
-- ============================================================
CREATE TABLE IF NOT EXISTS biz_office_supply (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    tenant_id BIGINT NOT NULL COMMENT '租户ID',
    supply_name VARCHAR(200) NOT NULL COMMENT '用品名称',
    specification VARCHAR(100) COMMENT '规格型号',
    unit VARCHAR(20) COMMENT '单位',
    current_stock DECIMAL(18,4) DEFAULT 0 COMMENT '当前库存数量',
    total_inbound DECIMAL(18,4) DEFAULT 0 COMMENT '累计入库量',
    total_issued DECIMAL(18,4) DEFAULT 0 COMMENT '累计领用量',
    last_inbound_date DATE COMMENT '最近入库日期',
    created_by BIGINT COMMENT '创建人',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除（0-未删除 1-已删除）',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    INDEX idx_tenant (tenant_id),
    INDEX idx_name (supply_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='办公用品库存';
