-- ============================================================
-- V2026_117__workflow_cc.sql
-- 双轨镜像：内容必须与 deploy/db-init/120_V2026_117__workflow_cc.sql 一致
-- 背景：审批抄送（对标竞品四视图「抄送我的」；spec 阶段三 3c⑤）。
--       发起人提交单据时可指定抄送人；抄送人在审批中心「抄送我的」页签查看。
-- 幂等：CREATE TABLE IF NOT EXISTS。
-- 回滚：DROP TABLE biz_workflow_cc;
-- ============================================================

CREATE TABLE IF NOT EXISTS biz_workflow_cc (
    id BIGINT NOT NULL COMMENT '主键ID',
    tenant_id BIGINT COMMENT '租户ID',
    process_instance_id VARCHAR(64) NOT NULL COMMENT '流程实例ID',
    business_type VARCHAR(50) NOT NULL COMMENT '业务类型（同 BusinessDetailService spec 注册键）',
    business_id BIGINT NOT NULL COMMENT '业务单据ID',
    cc_user_id BIGINT NOT NULL COMMENT '被抄送人用户ID',
    cc_by BIGINT COMMENT '抄送发起人（流程发起人）',
    read_status INT DEFAULT 0 COMMENT '已读状态（0-未读 1-已读）',
    read_at DATETIME NULL COMMENT '阅读时间',
    remark VARCHAR(500) COMMENT '备注',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除（0-未删除 1-已删除）',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_cc_user (cc_user_id, read_status),
    KEY idx_process (process_instance_id),
    KEY idx_business (business_type, business_id),
    KEY idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='审批抄送记录';
