-- ============================================
-- P2 修复 - 新建文件存储配置表 file_storage
-- 版本: V2026_29
-- 背景:
--   实体 FileStorage(@TableName("file_storage")) 已存在并被 platform/storage 接口查询，
--   但 00_schema.sql 与任何迁移均未创建该表，导致查询报
--   "Table 'zw_insight.file_storage' doesn't exist" 返回 500。
-- 影响:
--   新建 file_storage 表，列与实体字段（FileStorage extends BaseEntity）严格一致。
--   幂等：CREATE TABLE IF NOT EXISTS。
-- ============================================

CREATE TABLE IF NOT EXISTS file_storage (
    id BIGINT NOT NULL COMMENT '主键ID',
    storage_type VARCHAR(20) COMMENT '存储类型（LOCAL/MINIO/ALIYUN/TENCENT/QINIU）',
    endpoint VARCHAR(255) COMMENT '存储端点地址',
    access_key VARCHAR(255) COMMENT '访问密钥',
    secret_key VARCHAR(255) COMMENT '密钥',
    bucket VARCHAR(100) COMMENT '存储桶',
    base_path VARCHAR(255) COMMENT '基础路径',
    status INT DEFAULT 1 COMMENT '状态（1-启用 0-停用）',
    tenant_id BIGINT COMMENT '租户ID',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除（0-未删除 1-已删除）',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='文件存储配置表';
