-- V2: 补充字段长度约束和索引优化（配合后端 @Valid 参数校验）
-- 确保数据库层面与后端 DTO 校验规则一致

-- 项目名称 NOT NULL 约束（前端已校验，数据库层兜底）
ALTER TABLE biz_project MODIFY COLUMN project_name VARCHAR(200) NOT NULL COMMENT '项目名称';

-- 合同金额 NOT NULL 约束
ALTER TABLE biz_construction_contract MODIFY COLUMN contract_amount DECIMAL(18,2) NOT NULL COMMENT '合同金额';

-- 合同类型 NOT NULL 约束
ALTER TABLE biz_construction_contract MODIFY COLUMN contract_type VARCHAR(20) NOT NULL COMMENT '合同类型';

-- 开票金额 NOT NULL 约束
ALTER TABLE biz_invoice_apply MODIFY COLUMN invoice_amount DECIMAL(18,2) NOT NULL COMMENT '本次开票金额';
