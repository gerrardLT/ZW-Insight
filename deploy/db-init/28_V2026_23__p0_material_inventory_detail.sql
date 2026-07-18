-- ============================================
-- P0 修复 - 材料盘点明细表（两阶段拆分）
-- 版本: V2026_23
-- 功能: 材料盘点单拆分为"登记(DRAFT)"与"审批调库存(APPROVED)"两阶段。
--       原实现 save() 直接覆盖库存数量，无审批、无留痕；本次新增盘点明细表，
--       在登记阶段记录账面数量/实盘数量/差异（兼作盘盈亏差异流水），
--       审批阶段再据实盘数量调整库存。
-- ============================================

CREATE TABLE IF NOT EXISTS biz_material_inventory_detail (
    id BIGINT NOT NULL COMMENT '主键',
    tenant_id BIGINT COMMENT '租户ID',
    inventory_id BIGINT NOT NULL COMMENT '盘点单ID',
    stock_id BIGINT NOT NULL COMMENT '库存ID',
    material_name VARCHAR(200) COMMENT '材料名称（快照）',
    specification VARCHAR(100) COMMENT '规格（快照）',
    unit VARCHAR(20) COMMENT '单位（快照）',
    book_quantity DECIMAL(18,4) DEFAULT 0 COMMENT '账面数量（登记时库存快照）',
    actual_quantity DECIMAL(18,4) DEFAULT 0 COMMENT '实盘数量',
    diff_quantity DECIMAL(18,4) DEFAULT 0 COMMENT '差异数量（实盘-账面，正数盘盈/负数盘亏）',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除（0-未删除 1-已删除）',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    INDEX idx_inventory (inventory_id),
    INDEX idx_stock (stock_id),
    INDEX idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='材料盘点明细表（盘盈亏差异流水）';
