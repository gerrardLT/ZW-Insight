-- ============================================
-- P0 数据权限隔离 - sys_role.data_scope 字段约束增强
-- 版本: V2026_10
-- 功能: 为 sys_role.data_scope 添加 NOT NULL 约束
--       确保数据范围值限定为 ALL/DEPT_AND_CHILDREN/DEPT/PROJECT/SELF
-- Requirements: 1.1
-- ============================================

-- 将已有 data_scope 字段修改为 NOT NULL DEFAULT 'SELF'
-- 注意：V2026_07 已添加该字段（可能为 NULL），此处做约束增强

UPDATE sys_role SET data_scope = 'SELF' WHERE data_scope IS NULL OR data_scope = '';

ALTER TABLE sys_role MODIFY COLUMN data_scope VARCHAR(30) NOT NULL DEFAULT 'SELF'
    COMMENT '数据范围(ALL/DEPT_AND_CHILDREN/DEPT/PROJECT/SELF)';
