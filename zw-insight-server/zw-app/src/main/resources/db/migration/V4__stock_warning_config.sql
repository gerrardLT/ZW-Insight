-- V4: 库存安全阈值配置表

CREATE TABLE IF NOT EXISTS biz_stock_warning_config (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    project_id BIGINT COMMENT '项目ID（NULL=全局默认）',
    material_id BIGINT COMMENT '材料ID',
    material_name VARCHAR(200) COMMENT '材料名称',
    safety_stock DECIMAL(18,4) NOT NULL DEFAULT 10 COMMENT '安全库存数量',
    enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用（1启用/0停用）',
    created_by BIGINT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    version INT DEFAULT 0,
    UNIQUE KEY uk_project_material (project_id, material_id),
    INDEX idx_tenant (tenant_id)
) COMMENT='库存安全阈值配置';

-- 插入全局默认配置（安全库存=10）
INSERT INTO biz_stock_warning_config (tenant_id, project_id, material_id, material_name, safety_stock, enabled)
VALUES (0, NULL, NULL, '全局默认', 10.0000, 1);
