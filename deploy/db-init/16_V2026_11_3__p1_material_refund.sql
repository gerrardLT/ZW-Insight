-- ============================================
-- P1 业务完善 - 材料退款申请数据模型
-- 版本: V2026_11
-- 功能: 退货退款关联（功能7）
-- ============================================

-- ============ 1. 材料退款申请表 ============

CREATE TABLE IF NOT EXISTS biz_material_refund (
    id BIGINT NOT NULL COMMENT '主键',
    tenant_id BIGINT NOT NULL COMMENT '租户ID',
    project_id BIGINT NOT NULL COMMENT '项目ID',
    outbound_id BIGINT NOT NULL COMMENT '关联出库单ID',
    contract_id BIGINT NOT NULL COMMENT '关联采购合同ID',
    refund_code VARCHAR(50) COMMENT '退款单号',
    refund_amount DECIMAL(18,2) NOT NULL COMMENT '退款金额',
    refund_reason VARCHAR(500) COMMENT '退款原因',
    status VARCHAR(20) DEFAULT 'DRAFT' COMMENT '状态（DRAFT/PENDING/APPROVED/REJECTED）',
    workflow_instance_id VARCHAR(64) COMMENT '流程实例ID',
    created_by BIGINT COMMENT '创建人',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    INDEX idx_project (project_id),
    INDEX idx_contract (contract_id),
    INDEX idx_outbound (outbound_id),
    INDEX idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='材料退款申请';

-- ============ 2. 材料退款明细表 ============

CREATE TABLE IF NOT EXISTS biz_material_refund_detail (
    id BIGINT NOT NULL COMMENT '主键',
    tenant_id BIGINT NOT NULL COMMENT '租户ID',
    refund_id BIGINT NOT NULL COMMENT '退款申请ID',
    material_name VARCHAR(200) NOT NULL COMMENT '材料名称',
    specification VARCHAR(100) COMMENT '规格',
    unit VARCHAR(20) COMMENT '单位',
    quantity DECIMAL(18,4) NOT NULL COMMENT '退货数量',
    unit_price DECIMAL(18,4) NOT NULL COMMENT '入库单价',
    amount DECIMAL(18,2) NOT NULL COMMENT '退款金额',
    created_by BIGINT COMMENT '创建人',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    INDEX idx_refund (refund_id),
    INDEX idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='材料退款明细';
