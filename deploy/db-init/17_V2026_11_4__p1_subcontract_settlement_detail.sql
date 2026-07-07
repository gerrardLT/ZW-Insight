-- ============================================
-- P1 业务完善 - 分包结算明细表
-- 版本: V2026_11
-- 功能: 分包结算明细（工程项名称、计量单位、本次结算数量、单价、本次结算金额）
-- ============================================

CREATE TABLE IF NOT EXISTS biz_subcontract_settlement_detail (
    id BIGINT NOT NULL COMMENT '主键',
    tenant_id BIGINT NOT NULL COMMENT '租户ID',
    settlement_id BIGINT NOT NULL COMMENT '结算单ID',
    item_name VARCHAR(200) NOT NULL COMMENT '工程项名称',
    unit VARCHAR(20) COMMENT '计量单位',
    quantity DECIMAL(18,4) NOT NULL COMMENT '本次结算数量',
    unit_price DECIMAL(18,4) NOT NULL COMMENT '单价',
    amount DECIMAL(18,2) NOT NULL COMMENT '本次结算金额',
    remark VARCHAR(500) COMMENT '备注',
    sort_order INT DEFAULT 0 COMMENT '排序号',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除（0-未删除 1-已删除）',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    INDEX idx_settlement (settlement_id),
    INDEX idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='分包结算明细表';
