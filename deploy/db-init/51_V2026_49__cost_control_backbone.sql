-- ============================================================
-- 成本控制主线（Cost Control Backbone）Phase 1
-- 迁移版本：51_V2026_49
--
-- 目标：把「预算 → 变更 → 承诺 → 实际 → 预测 → 偏差」拉成一条
--       以 CBS 成本账户为唯一归集口径的主链，并为跨模块最终一致性
--       落地 Transactional Outbox。
--
-- 新增表：
--   1. biz_project_wbs_node  WBS 工作分解结构（项目 → 阶段 → 工作包）
--   2. biz_cost_account      CBS 成本账户（baseline/current/commitment/actual/forecast）
--   3. biz_change_event      变更事件（现场事件 → 影响评估 → 审批 → 驱动预算/合同变更）
--   4. sys_outbox_event      事务性发件箱（领域事件最终一致性）
--
-- 约束与兼容性：
--   - 全部 CREATE TABLE IF NOT EXISTS，可重复执行
--   - 不加物理外键（沿用本仓库既有约定：引用完整性由应用层 + 逻辑删除保障）
--   - 不修改任何既有表结构，存量数据零影响（CBS 为增量归集层，
--     原 biz_budget_detail 口径继续可用）
--   - 菜单/权限目录 ID 段 20201-20299（高于 47 号迁移的 20101-20199，
--     低于演示数据 90001+），全部 INSERT IGNORE
--
-- 回滚：
--   DROP TABLE IF EXISTS sys_outbox_event;
--   DROP TABLE IF EXISTS biz_change_event;
--   DROP TABLE IF EXISTS biz_cost_account_txn;
--   DROP TABLE IF EXISTS biz_cost_account;
--   DROP TABLE IF EXISTS biz_project_wbs_node;
--   DELETE FROM sys_role_menu WHERE id BETWEEN 20201 AND 20299;
--   DELETE FROM sys_menu      WHERE id BETWEEN 20201 AND 20299;
--   DELETE FROM serial_number_rule WHERE business_type = 'CHANGE_EVENT';
-- ============================================================

-- ============================================================
-- 1. WBS 节点表（工作分解结构）
-- ============================================================
CREATE TABLE IF NOT EXISTS biz_project_wbs_node (
    id BIGINT NOT NULL COMMENT '主键ID',
    project_id BIGINT NOT NULL COMMENT '项目ID',
    parent_id BIGINT DEFAULT NULL COMMENT '父节点ID（NULL=根节点）',
    node_level INT DEFAULT 1 COMMENT '层级（1-阶段 2-工作包 3-任务）',
    node_code VARCHAR(50) NOT NULL COMMENT '节点编号（如 PH-001/WP-001）',
    node_name VARCHAR(200) NOT NULL COMMENT '节点名称',
    description VARCHAR(500) COMMENT '描述',
    start_date DATE DEFAULT NULL COMMENT '计划开始日期',
    end_date DATE DEFAULT NULL COMMENT '计划结束日期',
    status VARCHAR(20) DEFAULT 'ACTIVE' COMMENT '状态（ACTIVE-进行中/INACTIVE-停用/CLOSED-已关闭）',
    sort_order INT DEFAULT 0 COMMENT '排序号',
    tenant_id BIGINT COMMENT '租户ID',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除（0-未删除 1-已删除）',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    UNIQUE KEY uk_wbs_code (tenant_id, project_id, node_code),
    KEY idx_wbs_project (project_id),
    KEY idx_wbs_parent (parent_id),
    KEY idx_wbs_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='WBS节点表（工作分解结构）';

-- ============================================================
-- 2. CBS 成本账户表（成本分解结构 / 成本归集唯一口径）
--    六个金额维度对应成本主线：
--      baseline_amount   目标成本（首次批准，不随变更漂移）
--      current_amount    当前预算（baseline + 已批准变更）
--      commitment_amount 已承诺（合同/PO 签订占用，未付款）
--      actual_amount     实际成本（结算/发票/付款已发生）
--      forecast_amount   完工预测 EAC（actual + 剩余估算）
--      variance = current - forecast（正=节约，负=超支）
-- ============================================================
CREATE TABLE IF NOT EXISTS biz_cost_account (
    id BIGINT NOT NULL COMMENT '主键ID',
    project_id BIGINT NOT NULL COMMENT '项目ID',
    parent_id BIGINT DEFAULT NULL COMMENT '父账户ID（NULL=根账户）',
    wbs_node_id BIGINT DEFAULT NULL COMMENT '关联WBS节点ID（可空）',
    account_code VARCHAR(50) NOT NULL COMMENT '账户编码（如 01.02.03）',
    account_name VARCHAR(200) NOT NULL COMMENT '账户名称',
    cost_category VARCHAR(30) NOT NULL COMMENT '费用类别（MATERIAL/LABOR/MACHINE/SUBCONTRACT/INDIRECT/OTHER）',
    cost_subcategory VARCHAR(100) DEFAULT NULL COMMENT '费用子类（对应 biz_cost_subcategory）',
    baseline_amount DECIMAL(18,2) DEFAULT 0 COMMENT '目标成本（原始批准预算）',
    current_amount DECIMAL(18,2) DEFAULT 0 COMMENT '当前预算（含已批准变更）',
    commitment_amount DECIMAL(18,2) DEFAULT 0 COMMENT '已承诺金额（合同/采购占用）',
    actual_amount DECIMAL(18,2) DEFAULT 0 COMMENT '实际成本（结算/发票/付款）',
    forecast_amount DECIMAL(18,2) DEFAULT 0 COMMENT '完工预测EAC',
    status VARCHAR(20) DEFAULT 'ACTIVE' COMMENT '状态（ACTIVE-活跃/LOCKED-锁定/CLOSED-已关闭）',
    remark VARCHAR(500) COMMENT '备注',
    tenant_id BIGINT COMMENT '租户ID',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除（0-未删除 1-已删除）',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    UNIQUE KEY uk_cost_account_code (tenant_id, project_id, account_code),
    KEY idx_ca_project (project_id),
    KEY idx_ca_parent (parent_id),
    KEY idx_ca_wbs (wbs_node_id),
    KEY idx_ca_category (cost_category),
    KEY idx_ca_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='CBS成本账户表（成本主线归集口径）';

-- ============================================================
-- 3. 变更事件表（跨域变更主链的起点）
--    状态机：DRAFT → ASSESSING → APPROVING → APPROVED / REJECTED
--            DRAFT → CANCELLED
--    审批（谁批）走 Flowable/BPMN；状态机（业务生命周期）由本表 status 承载，
--    两者严格分离：workflow_instance_id 仅为关联指针。
-- ============================================================
CREATE TABLE IF NOT EXISTS biz_change_event (
    id BIGINT NOT NULL COMMENT '主键ID',
    project_id BIGINT NOT NULL COMMENT '项目ID',
    event_number VARCHAR(50) NOT NULL COMMENT '变更事件编号（编号规则 CHANGE_EVENT）',
    source_type VARCHAR(30) NOT NULL COMMENT '来源类型（FIELD_EVENT-现场事件/DESIGN_CHANGE-设计变更/OWNER_REQUEST-业主指令/OTHER-其他）',
    source_ref VARCHAR(100) DEFAULT NULL COMMENT '来源引用（如签证ID/巡检ID，保证业务事实不重复录入）',
    title VARCHAR(300) NOT NULL COMMENT '标题',
    description TEXT COMMENT '详细描述',
    category VARCHAR(30) DEFAULT NULL COMMENT '影响类别（COST_IMPACT/SCOPE_CHANGE/SCHEDULE_DELAY/QUALITY_ISSUE）',
    affected_wbs_ids JSON DEFAULT NULL COMMENT '影响的WBS节点ID数组',
    affected_accounts JSON DEFAULT NULL COMMENT '影响的成本账户数组[{accountId,deltaType,deltaAmount}]',
    supporting_docs JSON DEFAULT NULL COMMENT '佐证附件数组[{fileId,url,name,type}]',
    impact_assessment JSON DEFAULT NULL COMMENT '影响评估{costDelta,scheduleDelayDays,rationale}',
    cost_delta DECIMAL(18,2) DEFAULT 0 COMMENT '成本影响金额（冗余自影响评估，便于列表排序/统计）',
    schedule_delay_days INT DEFAULT 0 COMMENT '工期影响天数（冗余自影响评估）',
    status VARCHAR(20) DEFAULT 'DRAFT' COMMENT '状态（DRAFT/ASSESSING/APPROVING/APPROVED/REJECTED/CANCELLED）',
    assessed_by BIGINT DEFAULT NULL COMMENT '影响评估人ID',
    assessed_at DATETIME DEFAULT NULL COMMENT '影响评估时间',
    approved_by BIGINT DEFAULT NULL COMMENT '审批人ID',
    approved_at DATETIME DEFAULT NULL COMMENT '审批时间',
    rejection_reason VARCHAR(500) DEFAULT NULL COMMENT '驳回原因',
    workflow_instance_id VARCHAR(64) DEFAULT NULL COMMENT '流程实例ID（仅关联指针，不承载业务状态）',
    tenant_id BIGINT COMMENT '租户ID',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除（0-未删除 1-已删除）',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    UNIQUE KEY uk_change_event_number (tenant_id, event_number),
    KEY idx_ce_project (project_id),
    KEY idx_ce_status (status),
    KEY idx_ce_source (source_type),
    KEY idx_ce_created (created_at),
    KEY idx_ce_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='变更事件表（跨域变更主链起点）';

-- ============================================================
-- 4. CBS 成本流水表（成本账户的唯一变更台账）
--    双重职责：
--      ① 幂等锁 —— uk_txn_source 保证同一业务事实（如某变更事件批准）
--         只能对账户金额生效一次，Outbox「至少一次」投递不会重复记账
--      ② 可追溯 —— current/commitment/actual 每一次变动都留痕，
--         支持「这个数字怎么来的」下钻与审计回放
--    金额一律记增量（delta）+ 变动后余额（balance_after），
--    余额字段让对账无需从头累加即可校验。
-- ============================================================
CREATE TABLE IF NOT EXISTS biz_cost_account_txn (
    id BIGINT NOT NULL COMMENT '主键ID',
    project_id BIGINT NOT NULL COMMENT '项目ID',
    account_id BIGINT NOT NULL COMMENT '成本账户ID',
    amount_type VARCHAR(20) NOT NULL COMMENT '金额维度（BASELINE/CURRENT/COMMITMENT/ACTUAL/FORECAST）',
    delta_amount DECIMAL(18,2) NOT NULL COMMENT '变动增量（正=增加，负=减少）',
    balance_after DECIMAL(18,2) NOT NULL COMMENT '变动后余额（对账校验用）',
    source_type VARCHAR(40) NOT NULL COMMENT '来源类型（CHANGE_EVENT/CONTRACT/PURCHASE/SETTLEMENT/PAYMENT/MANUAL）',
    source_id VARCHAR(64) NOT NULL COMMENT '来源业务ID（与 source_type 组成幂等键）',
    source_number VARCHAR(64) DEFAULT NULL COMMENT '来源单据编号（展示用）',
    occurred_at DATETIME DEFAULT NULL COMMENT '业务发生时间（区别于记录写入时间）',
    remark VARCHAR(500) DEFAULT NULL COMMENT '备注',
    tenant_id BIGINT COMMENT '租户ID',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除（0-未删除 1-已删除）',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    UNIQUE KEY uk_txn_source (tenant_id, source_type, source_id, account_id, amount_type),
    KEY idx_txn_account (account_id, created_at),
    KEY idx_txn_project (project_id),
    KEY idx_txn_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='CBS成本流水表（金额变动台账+幂等锁）';

-- ============================================================
-- 5. 事务性发件箱（Transactional Outbox）
--    业务事务内写入本表 → 独立投递器轮询发布 → 幂等消费，
--    替代跨模块直接改字段的强耦合写法。
--    不继承 BaseEntity 字段集（无 created_by/version/deleted），
--    属基础设施表，投递成功后按保留期清理。
-- ============================================================
CREATE TABLE IF NOT EXISTS sys_outbox_event (
    id BIGINT NOT NULL COMMENT '主键ID',
    tenant_id BIGINT DEFAULT NULL COMMENT '租户ID',
    event_type VARCHAR(100) NOT NULL COMMENT '事件类型（如 CHANGE_EVENT_APPROVED）',
    aggregate_type VARCHAR(100) NOT NULL COMMENT '聚合根类型（如 ChangeEvent）',
    aggregate_id BIGINT NOT NULL COMMENT '聚合根ID',
    idempotency_key VARCHAR(160) NOT NULL COMMENT '幂等键（aggregateType:aggregateId:eventType:version）',
    payload JSON DEFAULT NULL COMMENT '事件负载',
    status VARCHAR(20) DEFAULT 'PENDING' COMMENT '投递状态（PENDING/DELIVERED/FAILED/DEAD）',
    attempts INT DEFAULT 0 COMMENT '已尝试次数',
    max_attempts INT DEFAULT 5 COMMENT '最大尝试次数（超出转 DEAD）',
    next_retry_at DATETIME DEFAULT NULL COMMENT '下次重试时间（指数退避）',
    last_error VARCHAR(1000) DEFAULT NULL COMMENT '最后一次错误信息',
    published_at DATETIME DEFAULT NULL COMMENT '投递成功时间',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_outbox_idempotency (idempotency_key),
    KEY idx_outbox_pending (status, next_retry_at),
    KEY idx_outbox_aggregate (aggregate_type, aggregate_id),
    KEY idx_outbox_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='事务性发件箱表（领域事件最终一致性）';

-- ============================================================
-- 6. CBS 成本账户绑定表（显式映射 + 自动解析兜底）
--    用途：将源单据（合同/结算/付款）精确绑定到具体成本账户，
--         解决「一个科目下多个 CBS 账户时如何分配」的歧义问题。
--    无绑定时走科目级归集，有歧义则报告为 unmapped（人工解决），
--    避免猜测导致错误归集。
-- ============================================================
CREATE TABLE IF NOT EXISTS biz_cost_account_link (
    id BIGINT NOT NULL COMMENT '主键 ID',
    tenant_id BIGINT DEFAULT NULL COMMENT '租户 ID',
    project_id BIGINT NOT NULL COMMENT '项目 ID',
    account_id BIGINT NOT NULL COMMENT '成本账户 ID',
    source_type VARCHAR(50) NOT NULL COMMENT '来源类型（PURCHASE_CONTRACT/LABOR_CONTRACT/MACHINE_CONTRACT/SUBCONTRACT_CONTRACT/PAYMENT/MATERIAL_OUTBOUND/etc）',
    source_id VARCHAR(100) NOT NULL COMMENT '来源业务 ID（合同 ID/结算单号等）',
    commitment_amount DECIMAL(18,2) DEFAULT 0 COMMENT '承诺额（该绑定关系贡献的 commitment）',
    actual_amount DECIMAL(18,2) DEFAULT 0 COMMENT '实际成本（该绑定关系贡献的 actual）',
    remark VARCHAR(500) DEFAULT NULL COMMENT '备注',
    created_by BIGINT DEFAULT NULL COMMENT '创建人 ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除（0-未删除 1-已删除）',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    UNIQUE KEY uk_account_source (tenant_id, account_id, source_type, source_id),
    KEY idx_account (account_id),
    KEY idx_project (project_id),
    KEY idx_source (source_type, source_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='CBS 成本账户绑定表（显式映射源单据→成本账户）';

-- ============================================================
-- 7. 编号规则：变更事件（租户 1 演示/默认租户；其他租户在 UI 中自行配置）
--    沿用 serial_number_rule 既有列语义，reset_period=YEAR → 年度流水
-- ============================================================
INSERT IGNORE INTO serial_number_rule
    (id, business_type, rule_prefix, date_format, seq_length, reset_period, description, tenant_id)
VALUES
    (900010, 'CHANGE_EVENT', 'CHG', 'yyyy', 4, 'YEAR', '变更事件编号 CHG+年份+4位流水', 1);

-- ============================================================
-- 8. 菜单与权限目录（ID 段 20201-20299）
--    MENU 行进侧边栏；BUTTON 行 hidden=1 仅承载权限码
-- ============================================================

-- 8.1 一级菜单：项目成本主线看板（与 19 号「项目看板」同级，挂根布局）
INSERT IGNORE INTO sys_menu (id, menu_name, menu_type, parent_id, path, component, icon, sort_order, status, hidden, permission) VALUES
(20201, '成本主线看板', 'MENU', 0, '/project-cost-control', 'views/dashboard/project-cost-control', 'TrendCharts', 3, 1, 0, 'dashboard:costcontrol:view');

-- 8.2 项目管理（parent_id=3）：WBS 结构
INSERT IGNORE INTO sys_menu (id, menu_name, menu_type, parent_id, path, component, icon, sort_order, status, hidden, permission) VALUES
(20202, 'WBS结构', 'MENU', 3, 'wbs', 'views/project/wbs/index', 'Share', 4, 1, 0, 'project:wbs:view');

-- 8.3 合同管理（parent_id=4）：变更事件
INSERT IGNORE INTO sys_menu (id, menu_name, menu_type, parent_id, path, component, icon, sort_order, status, hidden, permission) VALUES
(20203, '变更事件', 'MENU', 4, 'change-event', 'views/contract/change-event/index', 'Switch', 4, 1, 0, 'contract:changeevent:view');

-- 8.4 预算管理（parent_id=6）：CBS 成本账户
INSERT IGNORE INTO sys_menu (id, menu_name, menu_type, parent_id, path, component, icon, sort_order, status, hidden, permission) VALUES
(20204, '成本账户CBS', 'MENU', 6, 'cost-account', 'views/budget/cost-account/index', 'Coin', 5, 1, 0, 'budget:costaccount:view');

-- 8.5 按钮级权限目录（hidden=1，仅补权限码；parent 挂对应 MENU 行）
INSERT IGNORE INTO sys_menu (id, menu_name, menu_type, parent_id, permission, status, hidden, weight) VALUES
-- WBS（parent=20202）
(20211, 'WBS新增', 'BUTTON', 20202, 'project:wbs:add',            1, 1, 'NORMAL'),
(20212, 'WBS编辑', 'BUTTON', 20202, 'project:wbs:edit',           1, 1, 'NORMAL'),
(20213, 'WBS删除', 'BUTTON', 20202, 'project:wbs:delete',         1, 1, 'NORMAL'),
-- CBS 成本账户（parent=20204）
(20221, '成本账户新增',   'BUTTON', 20204, 'budget:costaccount:add',    1, 1, 'NORMAL'),
(20222, '成本账户编辑',   'BUTTON', 20204, 'budget:costaccount:edit',   1, 1, 'NORMAL'),
(20223, '成本账户删除',   'BUTTON', 20204, 'budget:costaccount:delete', 1, 1, 'NORMAL'),
(20224, '成本账户锁定',   'BUTTON', 20204, 'budget:costaccount:lock',   1, 1, 'NORMAL'),
(20225, '成本金额同步',   'BUTTON', 20204, 'budget:costaccount:sync',   1, 1, 'NORMAL'),
-- 变更事件（parent=20203）
(20231, '变更事件新增',   'BUTTON', 20203, 'contract:changeevent:add',      1, 1, 'NORMAL'),
(20232, '变更事件编辑',   'BUTTON', 20203, 'contract:changeevent:edit',     1, 1, 'NORMAL'),
(20233, '变更事件删除',   'BUTTON', 20203, 'contract:changeevent:delete',   1, 1, 'NORMAL'),
(20234, '影响评估提交',   'BUTTON', 20203, 'contract:changeevent:assess',   1, 1, 'NORMAL'),
(20235, '变更事件提交审批','BUTTON', 20203, 'contract:changeevent:submit',   1, 1, 'NORMAL'),
(20236, '变更事件批准',   'BUTTON', 20203, 'contract:changeevent:approve',  1, 1, 'NORMAL'),
(20237, '变更事件驳回',   'BUTTON', 20203, 'contract:changeevent:reject',   1, 1, 'NORMAL'),
(20238, '变更事件取消',   'BUTTON', 20203, 'contract:changeevent:cancel',   1, 1, 'NORMAL');

-- 8.6 超管角色（role_id=1）授权绑定，保证上线即可见（其余租户角色在 UI 中按需授权）
INSERT IGNORE INTO sys_role_menu (id, role_id, menu_id) VALUES
(20201, 1, 20201),
(20202, 1, 20202),
(20203, 1, 20203),
(20204, 1, 20204),
(20211, 1, 20211),
(20212, 1, 20212),
(20213, 1, 20213),
(20221, 1, 20221),
(20222, 1, 20222),
(20223, 1, 20223),
(20224, 1, 20224),
(20225, 1, 20225),
(20231, 1, 20231),
(20232, 1, 20232),
(20233, 1, 20233),
(20234, 1, 20234),
(20235, 1, 20235),
(20236, 1, 20236),
(20237, 1, 20237),
(20238, 1, 20238);
