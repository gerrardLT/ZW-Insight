-- ============================================================================
-- V2026_57__receivable_ledger.sql
-- 资金闭环阶段一 1B：应收台账（到期日/账龄/核销）
--
-- 背景（docs/资金流转流程.md §10 支付率与资金缺口；驾驶舱 V1 §10 回款风险）：
--   现状 biz_project.receivable_amount 字段自 V2026_51 引入后生产代码从未回写
--   （全仓仅测试代码 setReceivableAmount），老板资金看板"应收账款/回款率"实际恒读种子值；
--   且无到期日、无账龄、无核销明细，回款风险下钻与收款侧滚动预测无数据基础。
--
-- 本脚本：
--   1) 新表 biz_receivable：结算审批通过生成应收记录（source_type=SETTLEMENT），
--      回款登记生效时 FIFO 核销；预留 RETENTION（质保金到期转应收）等来源类型。
--   2) 存量初始化：从既有 APPROVED 项目结算单生成应收记录（应收 = 最终结算金额
--      （空则累计产值）- 累计收款，仅正差额入库；核销额=0，到期日=结算单创建日+30天默认账期）。
--      ID 采用 settlement.id*10+1 派生（与雪花ID空间无碰撞），NOT EXISTS 守卫幂等。
--
-- 口径不变量：
--   biz_project.receivable_amount = 该项目 OPEN 应收余额合计（应用层同事务双写），
--   本脚本初始化后由应用代码维护一致性；审计脚本后续新增勾稽项校验。
-- ============================================================================

-- ============ 1. 应收台账表 ============

CREATE TABLE IF NOT EXISTS biz_receivable (
    id BIGINT NOT NULL COMMENT '主键ID',
    tenant_id BIGINT COMMENT '租户ID',
    project_id BIGINT NOT NULL COMMENT '项目ID',
    contract_id BIGINT COMMENT '关联施工合同ID（可空）',
    source_type VARCHAR(30) NOT NULL COMMENT '来源类型（SETTLEMENT-项目结算/RETENTION-质保金到期）',
    source_id BIGINT NOT NULL COMMENT '来源单据ID（结算单ID等）',
    receivable_amount DECIMAL(18,2) NOT NULL COMMENT '应收金额',
    due_date DATE NOT NULL COMMENT '约定收款到期日',
    written_off_amount DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '已核销金额（回款登记生效时FIFO冲减）',
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN' COMMENT '状态（OPEN-未结清/CLOSED-已结清）',
    remark VARCHAR(500) COMMENT '备注',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除（0-未删除 1-已删除）',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_project_status (project_id, status),
    KEY idx_due_date (due_date),
    KEY idx_source (source_type, source_id),
    KEY idx_tenant_id (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='应收台账（结算驱动，含到期日与核销）';

-- ============ 1.1 应收核销明细表（回款↔应收多对多勾稽，支持改额/删除精确反冲） ============

CREATE TABLE IF NOT EXISTS biz_receivable_write_off (
    id BIGINT NOT NULL COMMENT '主键ID',
    tenant_id BIGINT COMMENT '租户ID',
    receivable_id BIGINT NOT NULL COMMENT '应收台账ID（biz_receivable.id）',
    payment_received_id BIGINT NOT NULL COMMENT '回款登记ID（biz_payment_received.id）',
    amount DECIMAL(18,2) NOT NULL COMMENT '本次核销金额',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除（0-未删除 1-已删除）',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_receivable_id (receivable_id),
    KEY idx_payment_received_id (payment_received_id),
    KEY idx_tenant_id (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='应收核销明细（回款勾稽台账，可审计可反冲）';

-- ============ 2. 存量初始化：APPROVED 项目结算单 → 应收记录 ============
-- 应收金额 = IFNULL(final_settlement_amount, cumulative_output) - IFNULL(cumulative_received, 0)，仅正差额入库。
-- 到期日 = 结算单创建日 + 30 天（默认账期，与应用配置 zw.finance.receivable.default-credit-days 一致）。
-- 主键：ROW_NUMBER() 生成低位序号（1..N）——
--   ❌ 不可用项目列作 id：settlement.id 即雪花 ID（约 2.1e18），任何线性派生（如 ×10+1 = 2.1e19）
--      都会超出 BIGINT 上限 9.22e18 导致生产迁移直接失败（演示种子 id=93301 太小掩盖了该问题）；
--   ✅ 低位序号与雪花空间（≥1e18）天然隔离，永不冲突。
-- 幂等：NOT EXISTS 按 (source_type, source_id) 守卫；ROW_NUMBER 按 s.id 升序稳定编号，
--   增量重跑时既有行被守卫排除、新行分到更大号，不产生撞号竞态；INSERT IGNORE 为最后兜底。

INSERT IGNORE INTO biz_receivable
    (id, tenant_id, project_id, contract_id, source_type, source_id,
     receivable_amount, due_date, written_off_amount, status, remark, created_at, updated_at, deleted, version)
SELECT
    ROW_NUMBER() OVER (ORDER BY s.id) AS id,
    s.tenant_id,
    s.project_id,
    NULL,
    'SETTLEMENT',
    s.id,
    IFNULL(s.final_settlement_amount, IFNULL(s.cumulative_output, 0)) - IFNULL(s.cumulative_received, 0),
    DATE_ADD(DATE(IFNULL(s.created_at, NOW())), INTERVAL 30 DAY),
    0,
    'OPEN',
    '存量初始化（V2026_57，来自已审批项目结算单）',
    NOW(), NOW(), 0, 0
FROM biz_project_settlement s
WHERE s.status = 'APPROVED'
  AND s.deleted = 0
  AND IFNULL(s.final_settlement_amount, IFNULL(s.cumulative_output, 0)) - IFNULL(s.cumulative_received, 0) > 0
  AND NOT EXISTS (
      SELECT 1 FROM biz_receivable r
      WHERE r.source_type = 'SETTLEMENT' AND r.source_id = s.id AND r.deleted = 0
  );

-- ============ 3. 存量项目应收单值回填（与台账 OPEN 余额对齐） ============
-- biz_project.receivable_amount 生产代码从未回写（恒为种子/默认值），
-- 此处一次性对齐为台账 OPEN 余额合计；此后由应用层同事务双写维护。

UPDATE biz_project p
JOIN (
    SELECT r.project_id, SUM(r.receivable_amount - r.written_off_amount) AS open_balance
    FROM biz_receivable r
    WHERE r.status = 'OPEN' AND r.deleted = 0
    GROUP BY r.project_id
) t ON t.project_id = p.id
SET p.receivable_amount = t.open_balance
WHERE p.deleted = 0;

-- 无 OPEN 应收的项目归零（防止历史种子残留虚高）
UPDATE biz_project p
SET p.receivable_amount = 0
WHERE p.deleted = 0
  AND IFNULL(p.receivable_amount, 0) <> 0
  AND NOT EXISTS (
      SELECT 1 FROM biz_receivable r
      WHERE r.project_id = p.id AND r.status = 'OPEN' AND r.deleted = 0
  );
