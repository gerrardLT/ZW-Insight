-- ============================================
-- P2 体验增强 - 数据库迁移脚本
-- 版本: V2026_08
-- 功能: 供应商门户账户、模板管理、签到记录、初始化模板数据
-- ============================================

-- ============ 1. 供应商门户账户表 ============

CREATE TABLE IF NOT EXISTS sys_supplier_account (
    id BIGINT NOT NULL COMMENT '主键',
    supplier_id BIGINT NOT NULL COMMENT '关联供应商ID',
    phone VARCHAR(20) NOT NULL COMMENT '手机号（登录用）',
    status TINYINT DEFAULT 1 COMMENT '状态：1-正常 0-停用',
    last_login_at DATETIME COMMENT '最后登录时间',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_phone (phone),
    INDEX idx_supplier_id (supplier_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='供应商门户账户';

-- ============ 2. 模板管理表 ============

CREATE TABLE IF NOT EXISTS sys_template (
    id BIGINT NOT NULL COMMENT '主键',
    template_name VARCHAR(100) NOT NULL COMMENT '模板名称',
    template_type VARCHAR(20) NOT NULL COMMENT '模板类型：IMPORT/EXPORT/PRINT',
    module_code VARCHAR(50) NOT NULL COMMENT '业务模块编码',
    file_id BIGINT COMMENT 'MinIO文件ID(IMPORT/EXPORT模板文件)',
    template_content TEXT COMMENT 'HTML内容(PRINT模板)',
    is_default TINYINT DEFAULT 0 COMMENT '是否系统默认模板',
    tenant_id BIGINT COMMENT '租户ID(null为全局)',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_module_type (module_code, template_type),
    INDEX idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='模板管理';

-- ============ 3. 签到记录表 ============

CREATE TABLE IF NOT EXISTS biz_sign_record (
    id BIGINT NOT NULL COMMENT '主键',
    user_id BIGINT NOT NULL COMMENT '签到用户ID',
    project_id BIGINT NOT NULL COMMENT '签到项目ID',
    sign_time DATETIME NOT NULL COMMENT '签到时间',
    latitude DECIMAL(10,7) COMMENT '纬度',
    longitude DECIMAL(10,7) COMMENT '经度',
    address VARCHAR(200) COMMENT '逆地理编码地址',
    is_in_range TINYINT DEFAULT 1 COMMENT '是否在允许范围内：1-是 0-否',
    tenant_id BIGINT COMMENT '租户ID',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_user_project (user_id, project_id),
    INDEX idx_sign_time (sign_time),
    INDEX idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='签到记录';

-- ============ 4. 初始化系统导入模板数据 ============

INSERT INTO sys_template (id, template_name, template_type, module_code, is_default, created_at, updated_at) VALUES
(2001, '机械台账导入模板', 'IMPORT', 'MACHINE_LEDGER', 1, NOW(), NOW()),
(2002, '劳务花名册导入模板', 'IMPORT', 'LABOR_ROSTER', 1, NOW(), NOW()),
(2003, '人员信息导入模板', 'IMPORT', 'SYS_USER', 1, NOW(), NOW()),
(2004, '供应商导入模板', 'IMPORT', 'SUPPLIER', 1, NOW(), NOW()),
(2005, '材料字典导入模板', 'IMPORT', 'MATERIAL', 1, NOW(), NOW());
