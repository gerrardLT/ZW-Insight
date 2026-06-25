-- 供应商门户账户表
-- 用于供应商独立登录认证（手机号+短信验证码）
CREATE TABLE IF NOT EXISTS `sys_supplier_account` (
    `id` BIGINT NOT NULL COMMENT '主键ID',
    `supplier_id` BIGINT NOT NULL COMMENT '供应商ID（关联 biz_inquiry_supplier.supplier_id）',
    `phone` VARCHAR(20) NOT NULL COMMENT '手机号',
    `supplier_name` VARCHAR(100) DEFAULT NULL COMMENT '供应商名称',
    `status` TINYINT DEFAULT 1 COMMENT '状态（1-正常 0-停用）',
    `last_login_at` DATETIME DEFAULT NULL COMMENT '最后登录时间',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_phone` (`phone`),
    KEY `idx_supplier_id` (`supplier_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='供应商门户账户';
