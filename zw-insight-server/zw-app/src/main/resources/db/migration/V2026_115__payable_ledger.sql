-- ============================================================
-- V2026_115__payable_ledger.sql
-- 双轨镜像：内容必须与 deploy/db-init/118_V2026_115__payable_ledger.sql 一致
-- 背景：应付侧对称补齐（spec: finance-clarity-terminology-ui R4；对标 08 卷 G2）——
--       应收有台账+FIFO核销+账龄+双写不变量，应付此前散落五表求和无单一事实源。
-- 内容：biz_payable 台账 + biz_payable_write_off 核销明细 + biz_project.payable_amount 双写单值
--       + 存量回填（合同粒度 outstanding 转台账 BACKFILL 行，幂等可重跑）。
-- 口径：OPEN 余额 = payable_amount − paid_amount；不变量「项目应付单值 = 台账 OPEN 余额合计」
--       由 PayableService 同事务维护（对称 ReceivableService）。
-- 幂等：CREATE TABLE IF NOT EXISTS / PREPARE 补列 / 回填 NOT EXISTS + 仅 Project 单值重算。
-- 回滚：DROP TABLE biz_payable_write_off; DROP TABLE biz_payable;
--       ALTER TABLE biz_project DROP COLUMN payable_amount;
-- ============================================================

-- ============ 1. 应付台账主表（对称 biz_receivable） ============
CREATE TABLE IF NOT EXISTS biz_payable (
    id BIGINT NOT NULL COMMENT '主键ID',
    tenant_id BIGINT COMMENT '租户ID',
    project_id BIGINT NOT NULL COMMENT '项目ID',
    contract_id BIGINT NOT NULL COMMENT '支出合同ID（五类合同之一）',
    contract_category VARCHAR(20) NOT NULL COMMENT '合同类别（SUBCONTRACT/PURCHASE/LABOR/MACHINE/OTHER）',
    supplier_name VARCHAR(200) COMMENT '供应商/分包方名称（冗余展示）',
    source_type VARCHAR(30) NOT NULL COMMENT '来源类型（SUBCONTRACT_SETTLEMENT/PURCHASE_SETTLEMENT/LABOR_SETTLEMENT/MACHINE_SETTLEMENT/BACKFILL-迁移回填）',
    source_id BIGINT NOT NULL COMMENT '来源单据ID（结算单ID；BACKFILL 为合同ID）',
    payable_amount DECIMAL(18,2) NOT NULL COMMENT '应付金额（结算金额）',
    paid_amount DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '已核销金额（付款申请生效时FIFO冲减）',
    due_date DATE NOT NULL COMMENT '约定付款到期日',
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN' COMMENT '状态（OPEN-未结清/CLOSED-已结清）',
    remark VARCHAR(500) COMMENT '备注',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除（0-未删除 1-已删除）',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_project_status (project_id, status),
    KEY idx_contract (contract_id, status),
    KEY idx_source (source_type, source_id),
    KEY idx_tenant_id (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='应付台账（支出结算驱动，含到期日与核销）';

-- ============ 2. 应付核销明细表（付款↔应付多对多勾稽，支持反冲） ============
CREATE TABLE IF NOT EXISTS biz_payable_write_off (
    id BIGINT NOT NULL COMMENT '主键ID',
    tenant_id BIGINT COMMENT '租户ID',
    payable_id BIGINT NOT NULL COMMENT '应付台账ID（biz_payable.id）',
    payment_apply_id BIGINT NOT NULL COMMENT '付款申请ID（biz_payment_apply.id）',
    amount DECIMAL(18,2) NOT NULL COMMENT '本次核销金额',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除（0-未删除 1-已删除）',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_payable_id (payable_id),
    KEY idx_payment_apply_id (payment_apply_id),
    KEY idx_tenant_id (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='应付核销明细（付款↔应付勾稽）';

-- ============ 3. 项目应付单值列（双写不变量：单值 = OPEN 余额合计） ============
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_project' AND COLUMN_NAME = 'payable_amount');
SET @ddl = IF(@col_exists = 0,
    'ALTER TABLE biz_project ADD COLUMN payable_amount DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT ''应付账款（台账OPEN余额合计，PayableService同事务双写）'' AFTER receivable_amount',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ============ 4. 存量回填：五类支出合同「结算−已付」> 0 的合同各生成一行 BACKFILL ============
--    幂等：同 (BACKFILL, 合同, 类别, 租户) 已存在则跳过；id 取 710 段基值+ROW_NUMBER
--    （7.1e18 远离运行时雪花区间 ~2.2e18，且 < BIGINT 上限 9.2e18）。
INSERT INTO biz_payable (id, tenant_id, project_id, contract_id, contract_category, supplier_name,
    source_type, source_id, payable_amount, paid_amount, due_date, status, remark,
    created_by, created_at, updated_at, deleted, version)
SELECT 7100000000000000000 + ROW_NUMBER() OVER (ORDER BY t.contract_category, t.contract_id),
    t.tenant_id, t.project_id, t.contract_id, t.contract_category, t.supplier_name,
    'BACKFILL', t.contract_id, t.settled, t.paid, CURDATE(), 'OPEN',
    '迁移回填：合同累计结算−累计已付（V2026_115）', NULL, NOW(), NOW(), 0, 0
FROM (
    SELECT pc.tenant_id, pc.project_id, pc.id AS contract_id, 'PURCHASE' AS contract_category,
           NULL AS supplier_name, pc.cumulative_settlement AS settled, pc.cumulative_paid AS paid
    FROM biz_purchase_contract pc
    WHERE pc.deleted = 0 AND pc.cumulative_settlement IS NOT NULL
      AND pc.cumulative_settlement - IFNULL(pc.cumulative_paid, 0) > 0
    UNION ALL
    SELECT lc.tenant_id, lc.project_id, lc.id, 'LABOR', NULL,
           lc.cumulative_settlement, lc.cumulative_paid
    FROM biz_labor_contract lc
    WHERE lc.deleted = 0 AND lc.cumulative_settlement IS NOT NULL
      AND lc.cumulative_settlement - IFNULL(lc.cumulative_paid, 0) > 0
    UNION ALL
    SELECT mc.tenant_id, mc.project_id, mc.id, 'MACHINE', NULL,
           mc.cumulative_settlement, mc.cumulative_paid
    FROM biz_machine_contract mc
    WHERE mc.deleted = 0 AND mc.cumulative_settlement IS NOT NULL
      AND mc.cumulative_settlement - IFNULL(mc.cumulative_paid, 0) > 0
    UNION ALL
    SELECT sc.tenant_id, sc.project_id, sc.id, 'SUBCONTRACT', NULL,
           sc.cumulative_settlement, sc.cumulative_paid
    FROM biz_subcontract sc
    WHERE sc.deleted = 0 AND sc.cumulative_settlement IS NOT NULL
      AND sc.cumulative_settlement - IFNULL(sc.cumulative_paid, 0) > 0
    UNION ALL
    SELECT oc.tenant_id, oc.project_id, oc.id, 'OTHER', NULL,
           oc.cumulative_settlement, oc.cumulative_paid
    FROM biz_other_contract oc
    WHERE oc.deleted = 0 AND oc.cumulative_settlement IS NOT NULL
      AND oc.cumulative_settlement - IFNULL(oc.cumulative_paid, 0) > 0
) t
WHERE NOT EXISTS (
    SELECT 1 FROM biz_payable p
    WHERE p.source_type = 'BACKFILL' AND p.source_id = t.contract_id
      AND p.contract_category = t.contract_category AND p.tenant_id = t.tenant_id AND p.deleted = 0);

-- ============ 5. 项目应付单值重算（不变量初始化；可重跑） ============
UPDATE biz_project p
SET p.payable_amount = IFNULL((
    SELECT SUM(b.payable_amount - b.paid_amount)
    FROM biz_payable b
    WHERE b.project_id = p.id AND b.status = 'OPEN' AND b.deleted = 0), 0)
WHERE p.deleted = 0;
