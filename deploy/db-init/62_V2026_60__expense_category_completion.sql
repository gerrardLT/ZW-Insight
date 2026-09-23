-- ============================================================================
-- V2026_60__expense_category_completion.sql
-- 资金闭环阶段三：费用科目补全 + 报销明细激活 + 招待费专项管控
--
-- 背景（docs/资金流转流程.md §3.2 十二类实际费用账、§6.3 招待费；
--       audit-reports/cockpit-fund-gap-analysis-2026-09-22.md G7）：
--   1) V2026_51 科目树二级仅 6 项（材料/分包/劳务/机械/办公/差旅），
--      缺措施费、招待费、车辆费、会议费、专业服务类 → 成本中心"商务费用下钻"无数据源；
--   2) biz_reimbursement_detail 自 00_schema.sql 建表后为孤儿表
--      （全仓 main 代码仅 Mapper/实体定义，无任何 Service 写入或读取），
--      报销单只有总额、无费用类别维度 → 招待费专控与费用分析无从落地；
--   3) 招待费无结构化字段（对象/事由/人数/地点），无法做文档 §6.3 的异常分析。
--
-- 建模决策（与计划文档的差异，基于上述事实调整）：
--   不在报销主表加单值 category_code —— 一张报销单可含多类费用，主表单值分类是错误建模；
--   改为激活既有 biz_reimbursement_detail 并挂科目树（category_code），
--   明细合计必须等于主表 total_amount（应用层强校验，不静默）。
--
-- 幂等：CREATE TABLE IF NOT EXISTS + INSERT IGNORE + information_schema 条件 ALTER。
-- ID 段：科目 900136-900147 为本脚本保留（V2026_51 占用 900100-900135）。
-- ============================================================================

-- ============ 1. 补齐二级费用科目（对齐资金流转文档 §3.2 十二类） ============

INSERT IGNORE INTO biz_fund_category (id, tenant_id, code, name, direction, parent_id, level, sort_order, is_system) VALUES
-- 直接工程成本补：措施费（临建/临水临电/安全文明）
(900136, 1, 'EXP-DIRECT-MEASURE',    '措施费',     'EXPENSE', 900120, 2, 5, 1),
-- 间接费用补：商务经营 + 项目管理 + 专业服务
(900137, 1, 'EXP-INDIRECT-ENTERTAIN','招待费',     'EXPENSE', 900121, 2, 3, 1),
(900138, 1, 'EXP-INDIRECT-VEHICLE',  '车辆费',     'EXPENSE', 900121, 2, 4, 1),
(900139, 1, 'EXP-INDIRECT-MEETING',  '会议费',     'EXPENSE', 900121, 2, 5, 1),
(900140, 1, 'EXP-INDIRECT-SALARY',   '管理人员工资','EXPENSE', 900121, 2, 6, 1),
(900141, 1, 'EXP-INDIRECT-RENT',     '房租水电网络','EXPENSE', 900121, 2, 7, 1),
(900142, 1, 'EXP-INDIRECT-LIVING',   '项目生活费', 'EXPENSE', 900121, 2, 8, 1),
(900143, 1, 'EXP-INDIRECT-INSPECTION','检测试验费','EXPENSE', 900121, 2, 9, 1),
(900144, 1, 'EXP-INDIRECT-CONSULTING','咨询费',    'EXPENSE', 900121, 2, 10, 1),
(900145, 1, 'EXP-INDIRECT-AUDIT',    '审计费',     'EXPENSE', 900121, 2, 11, 1),
(900146, 1, 'EXP-INDIRECT-EXPERT',   '专家费',     'EXPENSE', 900121, 2, 12, 1),
-- 融资费用补：银行手续费
(900147, 1, 'EXP-FINANCE-FEE',       '银行手续费', 'EXPENSE', 900124, 2, 1, 1);

-- ============ 2. 激活报销明细：挂科目树 + 区分报销来源 ============

SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_reimbursement_detail' AND COLUMN_NAME = 'category_code') > 0,
    'SELECT 1',
    'ALTER TABLE `biz_reimbursement_detail` ADD COLUMN `category_code` VARCHAR(50) NULL COMMENT ''费用科目编码（biz_fund_category.code，V2026_60 激活明细维度）'' AFTER `expense_type`'
));
PREPARE __stmt FROM @sql;
EXECUTE __stmt;
DEALLOCATE PREPARE __stmt;

SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_reimbursement_detail' AND COLUMN_NAME = 'source_type') > 0,
    'SELECT 1',
    'ALTER TABLE `biz_reimbursement_detail` ADD COLUMN `source_type` VARCHAR(20) NOT NULL DEFAULT ''PROJECT'' COMMENT ''报销单来源（PROJECT-项目报销/PERSONAL-个人报销；reimbursement_id 需配合本字段定位主表）'' AFTER `reimbursement_id`'
));
PREPARE __stmt FROM @sql;
EXECUTE __stmt;
DEALLOCATE PREPARE __stmt;

SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_reimbursement_detail' AND INDEX_NAME = 'idx_category_code') = 0,
    'ALTER TABLE `biz_reimbursement_detail` ADD INDEX `idx_category_code` (`category_code`)',
    'SELECT 1'
));
PREPARE __stmt FROM @sql;
EXECUTE __stmt;
DEALLOCATE PREPARE __stmt;

SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_reimbursement_detail' AND INDEX_NAME = 'idx_source_reimbursement') = 0,
    'ALTER TABLE `biz_reimbursement_detail` ADD INDEX `idx_source_reimbursement` (`source_type`, `reimbursement_id`)',
    'SELECT 1'
));
PREPARE __stmt FROM @sql;
EXECUTE __stmt;
DEALLOCATE PREPARE __stmt;

-- ============ 3. 招待费专项明细（文档 §6.3 最少字段） ============
-- 挂在报销明细行上（一次招待 = 一条报销明细 + 一条专项明细），
-- 报销明细 category_code = 'EXP-INDIRECT-ENTERTAIN' 时本表记录必填（应用层强校验）。

CREATE TABLE IF NOT EXISTS biz_entertainment_detail (
    id BIGINT NOT NULL COMMENT '主键ID',
    tenant_id BIGINT COMMENT '租户ID',
    reimbursement_detail_id BIGINT NOT NULL COMMENT '报销明细ID（biz_reimbursement_detail.id）',
    project_id BIGINT COMMENT '项目ID（冗余，便于按项目分析）',
    entertain_date DATE NOT NULL COMMENT '招待日期',
    host_company VARCHAR(200) NOT NULL COMMENT '招待对象单位（甲方/监理/供应商等）',
    host_persons VARCHAR(200) COMMENT '招待对象人员',
    entertain_reason VARCHAR(500) NOT NULL COMMENT '招待事由',
    host_count INT COMMENT '我方人数',
    guest_count INT COMMENT '对方人数',
    location VARCHAR(200) COMMENT '消费地点',
    handler_id BIGINT COMMENT '经办人ID',
    handler_name VARCHAR(50) COMMENT '经办人姓名',
    has_invoice TINYINT DEFAULT 0 COMMENT '发票是否完整（0-否 1-是）',
    pay_method VARCHAR(20) COMMENT '支付方式（PUBLIC-公账/REIMBURSE-个人报销）',
    pre_approved TINYINT DEFAULT 0 COMMENT '是否有事前审批（0-无 1-有；文档§11 招待费预警项）',
    remark VARCHAR(500) COMMENT '备注',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除（0-未删除 1-已删除）',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    UNIQUE KEY uk_reimbursement_detail (reimbursement_detail_id),
    KEY idx_project_date (project_id, entertain_date),
    KEY idx_handler (handler_id),
    KEY idx_tenant_id (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='招待费专项明细（对象/事由/人数/地点等结构化字段）';
