-- 独立合同修订，不更新既有合同或签证。回滚：停用入口及流程，保留修订审计记录。
CREATE TABLE IF NOT EXISTS biz_contract_revision (
 id BIGINT NOT NULL PRIMARY KEY,
 tenant_id BIGINT NOT NULL,
 contract_id BIGINT NOT NULL,
 project_id BIGINT NOT NULL,
 base_version INT NOT NULL,
 before_amount DECIMAL(18,2) NOT NULL,
 after_amount DECIMAL(18,2) NOT NULL,
 delta_amount DECIMAL(18,2) NOT NULL,
 before_snapshot LONGTEXT NOT NULL,
 after_snapshot LONGTEXT NOT NULL,
 reason VARCHAR(1000) NOT NULL,
 status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
 workflow_instance_id VARCHAR(64),
 created_by BIGINT,
 created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
 updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 deleted TINYINT NOT NULL DEFAULT 0,
 version INT NOT NULL DEFAULT 0,
 INDEX idx_revision_contract (tenant_id, contract_id, status),
 INDEX idx_revision_project (tenant_id, project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
-- 仅为已有施工合同业务类型的租户补目录，不导入演示数据，不覆盖既有配置。
INSERT INTO wf_business_type (id, type_name, type_code, parent_id, sort_order, tenant_id, created_by, created_at, updated_at, deleted, version)
SELECT 1060000000000000000 + t.tenant_id, '合同修订', 'CONTRACT_REVISION', 0, 106, t.tenant_id, 1, NOW(), NOW(), 0, 0
FROM (SELECT DISTINCT tenant_id FROM wf_business_type WHERE type_code='CONSTRUCTION_CONTRACT' AND deleted=0) t
WHERE NOT EXISTS (SELECT 1 FROM wf_business_type x WHERE x.tenant_id=t.tenant_id AND x.type_code='CONTRACT_REVISION' AND x.deleted=0);
