# Technical Design Document — P0 核心功能

## 1. 概述

本文档为 ZW-Insight 工程项目管理系统 P0 优先级 7 个核心缺失功能的技术设计方案。设计遵循现有系统架构（Spring Boot 3.2 单体应用 + Vue 3 前端 + uni-app 移动端），复用已有基础设施（Flowable 7.0 审批引擎、MinIO 文件存储、Redis 缓存、RabbitMQ 消息队列）。

### 1.1 功能清单

| # | 功能 | 所属后端模块 | 前端影响 |
|---|------|-------------|---------|
| 1 | 工程量清单上传 | zw-contract | PC |
| 2 | 目标成本变更 | zw-budget | PC |
| 3 | 项目最终结算 | zw-finance | PC |
| 4 | 验证码登录 | zw-security | PC + Mobile |
| 5 | 质保金预警定时任务 | zw-finance | 无（后台任务）|
| 6 | 预算控制配置页面 | zw-budget | PC |
| 7 | 检查方案关联 | zw-site | PC + Mobile |

### 1.2 技术依赖

| 组件 | 用途 | 版本 |
|------|------|------|
| EasyExcel | BOQ Excel 解析 | 3.3.4 |
| Hutool CaptchaUtil | 图形验证码生成 | 5.8.26 |
| 阿里云短信 SDK | 短信验证码发送 | 2.0.24 |
| Flowable | 审批流程 | 7.0.1 |
| Redis | 验证码存储/频率限制/通知去重 | 7 |
| MinIO | BOQ 原始文件存储 | 8.5.9 |

---

## 2. 数据库设计

### 2.1 工程量清单表（新增）

```sql
CREATE TABLE biz_boq_item (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    contract_id BIGINT NOT NULL COMMENT '施工合同ID',
    parent_id BIGINT DEFAULT 0 COMMENT '父级条目ID（0为顶层）',
    item_code VARCHAR(50) NOT NULL COMMENT '项目编码（如1.1.2）',
    item_name VARCHAR(300) NOT NULL COMMENT '项目名称',
    unit VARCHAR(20) COMMENT '计量单位',
    quantity DECIMAL(18,4) DEFAULT 0 COMMENT '工程数量',
    unit_price DECIMAL(18,4) DEFAULT 0 COMMENT '综合单价',
    total_price DECIMAL(18,2) DEFAULT 0 COMMENT '合价',
    completed_quantity DECIMAL(18,4) DEFAULT 0 COMMENT '已完成工程量',
    level TINYINT DEFAULT 1 COMMENT '层级（1-4）',
    sort_order INT DEFAULT 0 COMMENT '排序号',
    created_by BIGINT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    INDEX idx_contract (contract_id),
    INDEX idx_parent (parent_id),
    INDEX idx_tenant (tenant_id)
) COMMENT '工程量清单条目表';
```

### 2.2 目标成本变更表（新增）

```sql
CREATE TABLE biz_budget_change (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL COMMENT '项目ID',
    budget_id BIGINT NOT NULL COMMENT '原预算编制ID',
    change_code VARCHAR(50) COMMENT '变更单编号',
    change_reason TEXT NOT NULL COMMENT '变更原因',
    total_adjust_amount DECIMAL(18,2) DEFAULT 0 COMMENT '调整总额',
    status VARCHAR(20) DEFAULT 'DRAFT' COMMENT '状态(DRAFT/SUBMITTED/APPROVED/REJECTED/WITHDRAWN)',
    workflow_instance_id VARCHAR(64) COMMENT '流程实例ID',
    created_by BIGINT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    INDEX idx_project (project_id),
    INDEX idx_budget (budget_id),
    INDEX idx_tenant (tenant_id)
) COMMENT '目标成本变更主表';

CREATE TABLE biz_budget_change_detail (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    change_id BIGINT NOT NULL COMMENT '变更单ID',
    budget_detail_id BIGINT NOT NULL COMMENT '原预算明细ID',
    cost_category VARCHAR(50) NOT NULL COMMENT '成本大类',
    cost_subcategory VARCHAR(50) COMMENT '二级科目',
    item_name VARCHAR(200) NOT NULL COMMENT '科目名称',
    original_amount DECIMAL(18,2) NOT NULL COMMENT '原金额',
    adjust_amount DECIMAL(18,2) NOT NULL COMMENT '调整金额（正追加/负调减）',
    adjusted_amount DECIMAL(18,2) NOT NULL COMMENT '调整后金额',
    remark VARCHAR(500) COMMENT '备注',
    INDEX idx_change (change_id),
    INDEX idx_budget_detail (budget_detail_id)
) COMMENT '目标成本变更明细表';
```

### 2.3 项目最终结算表（新增）

```sql
CREATE TABLE biz_project_settlement (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL COMMENT '项目ID',
    settlement_code VARCHAR(50) COMMENT '结算单编号',
    -- 收入汇总
    construction_contract_amount DECIMAL(18,2) DEFAULT 0 COMMENT '施工合同总额',
    cumulative_output DECIMAL(18,2) DEFAULT 0 COMMENT '累计产值',
    cumulative_received DECIMAL(18,2) DEFAULT 0 COMMENT '累计收款',
    cumulative_invoiced DECIMAL(18,2) DEFAULT 0 COMMENT '累计开票',
    total_income DECIMAL(18,2) DEFAULT 0 COMMENT '总收入',
    -- 支出汇总
    subcontract_settled DECIMAL(18,2) DEFAULT 0 COMMENT '分包结算总额',
    labor_settled DECIMAL(18,2) DEFAULT 0 COMMENT '劳务结算总额',
    material_settled DECIMAL(18,2) DEFAULT 0 COMMENT '材料结算总额',
    machine_settled DECIMAL(18,2) DEFAULT 0 COMMENT '机械结算总额',
    other_expense DECIMAL(18,2) DEFAULT 0 COMMENT '其他支出',
    cumulative_paid DECIMAL(18,2) DEFAULT 0 COMMENT '累计付款',
    total_expenditure DECIMAL(18,2) DEFAULT 0 COMMENT '总支出',
    -- 利润
    profit DECIMAL(18,2) DEFAULT 0 COMMENT '最终利润',
    profit_rate DECIMAL(5,2) DEFAULT 0 COMMENT '利润率(%)',
    -- 状态
    status VARCHAR(20) DEFAULT 'DRAFT' COMMENT '状态(DRAFT/SUBMITTED/APPROVED/REJECTED)',
    workflow_instance_id VARCHAR(64) COMMENT '流程实例ID',
    created_by BIGINT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    INDEX idx_project (project_id),
    INDEX idx_tenant (tenant_id)
) COMMENT '项目最终结算主表';

CREATE TABLE biz_settlement_contract_detail (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    settlement_id BIGINT NOT NULL COMMENT '结算单ID',
    contract_type VARCHAR(20) NOT NULL COMMENT '合同类型(SUBCONTRACT/LABOR/MATERIAL/MACHINE/OTHER)',
    contract_id BIGINT NOT NULL COMMENT '合同ID',
    contract_code VARCHAR(50) COMMENT '合同编号',
    contract_name VARCHAR(200) COMMENT '合同名称',
    contract_amount DECIMAL(18,2) DEFAULT 0 COMMENT '合同金额',
    settled_amount DECIMAL(18,2) DEFAULT 0 COMMENT '已结算金额',
    paid_amount DECIMAL(18,2) DEFAULT 0 COMMENT '已付款金额',
    unsettled_amount DECIMAL(18,2) DEFAULT 0 COMMENT '未结金额',
    settlement_status VARCHAR(20) COMMENT '合同结算状态',
    INDEX idx_settlement (settlement_id)
) COMMENT '结算关联合同明细表';
```


### 2.4 预算控制配置表（新增）

```sql
CREATE TABLE sys_budget_control_config (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    project_id BIGINT COMMENT '项目ID（NULL表示系统默认规则）',
    control_mode VARCHAR(20) NOT NULL COMMENT '控制模式(WARN_ONLY/BLOCK/EXEMPT)',
    warning_threshold INT DEFAULT 80 COMMENT '预警阈值(50-99，百分比整数)',
    is_default TINYINT DEFAULT 0 COMMENT '是否系统默认(1是/0否)',
    created_by BIGINT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    UNIQUE KEY uk_project (tenant_id, project_id),
    INDEX idx_tenant (tenant_id)
) COMMENT '预算控制配置表';
```

### 2.5 检查方案关联（修改现有表）

```sql
-- 修改 biz_inspection 表，增加方案快照字段
ALTER TABLE biz_inspection
    ADD COLUMN scheme_snapshot JSON COMMENT '检查方案快照（JSON格式）' AFTER scheme_id;

-- 检查明细表（新增，存储检查项及结果）
CREATE TABLE biz_inspection_detail (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    inspection_id BIGINT NOT NULL COMMENT '检查记录ID',
    item_name VARCHAR(200) NOT NULL COMMENT '检查项目名称',
    check_standard VARCHAR(500) COMMENT '检查标准',
    check_method VARCHAR(300) COMMENT '检查方法',
    check_result VARCHAR(20) COMMENT '检查结果(PASS/FAIL/NOT_CHECKED)',
    remark VARCHAR(500) COMMENT '备注',
    sort_order INT DEFAULT 0,
    INDEX idx_inspection (inspection_id)
) COMMENT '检查明细表';
```

### 2.6 质保金预警通知记录（新增）

```sql
CREATE TABLE biz_retention_warning_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    retention_id BIGINT NOT NULL COMMENT '质保金记录ID',
    warning_level VARCHAR(20) NOT NULL COMMENT '预警级别(UPCOMING/URGENT/OVERDUE)',
    notify_status VARCHAR(20) DEFAULT 'PENDING' COMMENT '通知状态(PENDING/SENT/FAILED)',
    retry_count INT DEFAULT 0 COMMENT '重试次数',
    sent_at DATETIME COMMENT '发送时间',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_retention (retention_id),
    INDEX idx_level (warning_level),
    INDEX idx_status (notify_status)
) COMMENT '质保金预警通知日志表';
```

---

## 3. API 设计

### 3.1 工程量清单（BOQ）接口

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/v1/contracts/{contractId}/boq/upload` | 上传 BOQ Excel 文件 |
| GET | `/api/v1/contracts/{contractId}/boq` | 查询合同关联的清单树 |
| GET | `/api/v1/contracts/{contractId}/boq/flat` | 查询清单平铺列表 |
| DELETE | `/api/v1/contracts/{contractId}/boq` | 清除合同清单数据 |

**上传接口详细设计：**

```java
@PostMapping("/api/v1/contracts/{contractId}/boq/upload")
public R<BoqUploadResultVO> uploadBoq(
    @PathVariable Long contractId,
    @RequestParam("file") MultipartFile file
);

// 响应 VO
public class BoqUploadResultVO {
    private Integer totalItems;        // 总条目数
    private Integer levelCount;        // 层级数
    private BigDecimal totalAmount;    // 清单合计金额
    private String fileUrl;            // 原始文件存储地址
}
```

### 3.2 目标成本变更接口

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/v1/budget-changes` | 变更单分页列表 |
| GET | `/api/v1/budget-changes/{id}` | 变更单详情 |
| POST | `/api/v1/budget-changes` | 创建变更单 |
| PUT | `/api/v1/budget-changes/{id}` | 编辑变更单 |
| DELETE | `/api/v1/budget-changes/{id}` | 删除变更单（草稿状态）|
| POST | `/api/v1/budget-changes/{id}/submit` | 提交审批 |
| POST | `/api/v1/budget-changes/{id}/withdraw` | 撤回 |

**创建/编辑 DTO：**

```java
public class BudgetChangeDTO {
    @NotNull
    private Long projectId;
    @NotNull
    private Long budgetId;
    @NotBlank
    private String changeReason;
    @NotEmpty
    private List<BudgetChangeDetailDTO> details;
}

public class BudgetChangeDetailDTO {
    @NotNull
    private Long budgetDetailId;     // 原预算明细ID
    private String costCategory;      // 成本大类
    private String costSubcategory;   // 二级科目
    private String itemName;          // 科目名称
    @NotNull
    private BigDecimal originalAmount;
    @NotNull
    private BigDecimal adjustAmount;  // 正=追加，负=调减
}
```

### 3.3 项目最终结算接口

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/v1/project-settlements` | 创建结算单（自动汇总）|
| GET | `/api/v1/project-settlements/{id}` | 结算单详情 |
| PUT | `/api/v1/project-settlements/{id}` | 编辑结算单 |
| POST | `/api/v1/project-settlements/{id}/submit` | 提交审批 |
| POST | `/api/v1/project-settlements/{id}/export` | 导出 Excel |
| GET | `/api/v1/project-settlements/{id}/unsettled-contracts` | 未结清合同列表 |

### 3.4 验证码接口

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/v1/captcha/image` | 获取图形验证码 |
| POST | `/api/v1/captcha/sms` | 发送短信验证码 |

**图形验证码响应：**

```java
public class CaptchaVO {
    private String uuid;        // 验证码标识
    private String imageBase64; // Base64 编码图片
}
```

**短信验证码请求：**

```java
public class SmsCaptchaDTO {
    @NotBlank
    @Pattern(regexp = "^1[3-9]\\d{9}$")
    private String phone;
}
```

**登录请求扩展：**

```java
public class LoginDTO {
    private String tenantCode;
    private String username;
    private String password;
    private String captchaCode;  // 验证码
    private String captchaUuid;  // 验证码标识
    // 移动端短信登录
    private String phone;
    private String smsCode;
    private String loginType;    // PASSWORD / SMS
}
```

### 3.5 预算控制配置接口

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/v1/budget-control-configs` | 配置列表（分页）|
| GET | `/api/v1/budget-control-configs/{id}` | 配置详情 |
| POST | `/api/v1/budget-control-configs` | 创建配置 |
| PUT | `/api/v1/budget-control-configs/{id}` | 编辑配置 |
| DELETE | `/api/v1/budget-control-configs/{id}` | 删除配置 |
| GET | `/api/v1/budget-control-configs/project/{projectId}` | 获取项目生效配置 |

### 3.6 检查方案关联接口

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/v1/inspection-schemes` | 方案列表（按类型筛选）|
| GET | `/api/v1/inspection-schemes/{id}/items` | 方案检查项列表 |
| POST | `/api/v1/inspections/{id}/apply-scheme` | 关联方案到检查记录 |


---

## 4. High-Level Design

### 4.1 功能 1：工程量清单上传 — 组件交互

```
┌─────────────┐     ┌──────────────────┐     ┌───────────┐
│  PC Frontend │────▶│ BoqController    │────▶│ MinIO     │
│  (Upload)    │     │ /contracts/{id}/ │     │ (原始文件) │
└─────────────┘     │   boq/upload     │     └───────────┘
                    └────────┬─────────┘
                             │
                    ┌────────▼─────────┐
                    │  BoqService       │
                    │  1. 状态校验      │
                    │  2. 引用检查      │
                    │  3. EasyExcel解析 │
                    │  4. 层级构建      │
                    │  5. 批量入库      │
                    │  6. 金额回写      │
                    └────────┬─────────┘
                             │
              ┌──────────────┼──────────────┐
              ▼              ▼              ▼
    ┌─────────────┐  ┌────────────┐  ┌──────────────┐
    │biz_boq_item │  │biz_constr- │  │biz_output_   │
    │  (清单数据)  │  │uction_     │  │report(引用   │
    │             │  │contract    │  │ 检查)        │
    └─────────────┘  │(金额回写)  │  └──────────────┘
                     └────────────┘
```

### 4.2 功能 2：目标成本变更 — 状态机

```
  ┌──────────────────────────────────────────────┐
  │                                              │
  ▼                                              │
DRAFT ──submit──▶ SUBMITTED ──approve──▶ APPROVED │
  │                   │                          │
  │                   │ reject                   │
  │                   ▼                          │
  │              REJECTED ───edit──▶ DRAFT ──────┘
  │                   
  │ withdraw (from SUBMITTED)
  ▼
WITHDRAWN
```

**审批通过回写逻辑：**
```
BudgetChangeApprovedEvent
  ├── 遍历变更明细
  │   └── 原预算明细.budget_total_price += adjust_amount
  ├── 更新原预算.total_amount = SUM(明细.budget_total_price)
  └── 更新 biz_project.budget_amount = 原预算.total_amount
```

### 4.3 功能 3：项目最终结算 — 数据汇总流程

```
┌────────────────────────────────────────────────────────┐
│                  Settlement_Service                      │
│                                                        │
│  1. 校验项目状态 == COMPLETED                           │
│  2. 校验不存在草稿/审批中结算单                          │
│  3. 汇总收入:                                          │
│     ├── biz_construction_contract.contract_amount      │
│     ├── biz_construction_contract.cumulative_output    │
│     ├── biz_payment_received.SUM(receive_amount)       │
│     └── biz_invoice_apply.SUM(invoice_amount) [已审批]  │
│  4. 汇总支出:                                          │
│     ├── biz_expense_contract WHERE category=SUBCONTRACT│
│     ├── biz_expense_contract WHERE category=LABOR      │
│     ├── biz_expense_contract WHERE category=MATERIAL   │
│     ├── biz_expense_contract WHERE category=MACHINE    │
│     └── biz_payment_apply.SUM(payment_amount) [已审批]  │
│  5. 计算利润 = 总收入 - 总支出                          │
│  6. 计算利润率 = 利润 / 总收入 * 100%                   │
│  7. 查询未结清合同列表                                  │
└────────────────────────────────────────────────────────┘
```

### 4.4 功能 4：验证码登录 — 时序图

```
PC端图形验证码流程:
┌──────┐      ┌──────────────┐      ┌───────┐
│Client│      │CaptchaService│      │ Redis │
└──┬───┘      └──────┬───────┘      └───┬───┘
   │ GET /captcha/image│                 │
   │──────────────────▶│                 │
   │                   │ SET captcha:{uuid} = code, EX 300
   │                   │────────────────▶│
   │  {uuid, base64}   │                 │
   │◀──────────────────│                 │
   │                   │                 │
   │ POST /auth/login  │                 │
   │ (+ captchaCode,   │                 │
   │    captchaUuid)   │                 │
   │──────────────────▶│                 │
   │                   │ GET captcha:{uuid}
   │                   │────────────────▶│
   │                   │     code        │
   │                   │◀────────────────│
   │                   │ 比较(忽略大小写) │
   │                   │ DEL captcha:{uuid}
   │                   │────────────────▶│
   │  校验结果          │                 │
   │◀──────────────────│                 │

移动端短信验证码流程:
┌──────┐      ┌──────────────┐   ┌───────┐   ┌─────────┐
│Client│      │CaptchaService│   │ Redis │   │阿里云SMS│
└──┬───┘      └──────┬───────┘   └───┬───┘   └────┬────┘
   │ POST /captcha/sms│              │            │
   │ {phone}          │              │            │
   │─────────────────▶│              │            │
   │                  │ 频率检查      │            │
   │                  │ GET sms:freq:{phone}      │
   │                  │─────────────▶│            │
   │                  │ GET sms:daily:{phone}     │
   │                  │─────────────▶│            │
   │                  │              │            │
   │                  │ 生成6位数字码  │            │
   │                  │ SET sms:{phone}=code,EX300│
   │                  │─────────────▶│            │
   │                  │ SET sms:freq:{phone},EX60 │
   │                  │─────────────▶│            │
   │                  │ INCR sms:daily:{phone}    │
   │                  │─────────────▶│            │
   │                  │              │            │
   │                  │ sendSms(phone, code)      │
   │                  │────────────────────────▶│
   │  {success}       │              │            │
   │◀─────────────────│              │            │
```

### 4.5 功能 5：质保金预警定时任务 — 执行流程

```
┌─────────────────────────────────────────────────────────┐
│  @Scheduled(cron = "0 0 8 * * ?")                       │
│  RetentionWarningTask.execute()                         │
├─────────────────────────────────────────────────────────┤
│  1. 查询 status='UNRETURNED' 的质保金记录               │
│     WHERE expire_date <= NOW() + 30天                   │
│       OR expire_date < NOW()                            │
│                                                        │
│  2. 分级:                                              │
│     ├── 30天≥剩余≥8天 → UPCOMING（即将到期）            │
│     ├── 7天≥剩余≥1天 → URGENT（紧急到期）              │
│     └── 已过期 → OVERDUE（逾期未退还）                  │
│         └── 逾期>180天 → 标记LONG_OVERDUE，跳过         │
│                                                        │
│  3. 去重检查 (Redis Set):                               │
│     key = "retention:warned:{retentionId}:{level}"     │
│     SISMEMBER → 已发送则跳过                            │
│                                                        │
│  4. 逾期催办: 每3天发送一次                              │
│     key = "retention:overdue:last:{retentionId}"       │
│     距上次发送<3天 → 跳过                               │
│                                                        │
│  5. 调用 Message_Service 发送通知                       │
│     ├── 成功 → SADD 去重key，记录日志                   │
│     └── 失败 → 记录失败日志，retry_count++              │
│                                                        │
│  6. 重试失败记录 (retry_count < 3)                      │
└─────────────────────────────────────────────────────────┘
```

### 4.6 功能 6：预算控制配置 — 校验拦截流程

```
业务单据提交
      │
      ▼
┌─────────────────────────────────┐
│ BudgetControlAspect (@Before)    │
│                                 │
│ 1. 查询 sys_budget_control_config│
│    WHERE project_id = ?         │
│    若无结果 → 使用 is_default=1  │
│    若查询异常 → 使用 BLOCK 默认  │
│                                 │
│ 2. 计算预算执行率:               │
│    已发生额 = 该科目下已签合同    │
│    + 已审批付款                  │
│    执行率 = 已发生额/预算额*100% │
│                                 │
│ 3. 预警检查:                     │
│    IF 执行率 >= warning_threshold│
│    THEN 发送站内信预警           │
│                                 │
│ 4. 超支控制:                     │
│    IF 执行率 > 100%:            │
│    ├── BLOCK → throw Exception  │
│    ├── WARN_ONLY → 返回警告标识  │
│    └── EXEMPT → 放行            │
└─────────────────────────────────┘
```

### 4.7 功能 7：检查方案关联 — 快照机制

```
┌──────────────┐                    ┌─────────────────────┐
│ 基础数据模块  │                    │ 现场管理模块         │
│              │                    │                     │
│ biz_inspec-  │   选择方案时读取    │ biz_inspection      │
│ tion_scheme  │◀──────────────────│  + scheme_id        │
│  + items     │                    │  + scheme_snapshot  │
│              │   返回方案内容      │                     │
│              │──────────────────▶│ biz_inspection_     │
│              │                    │   detail            │
└──────────────┘                    │  (检查项+结果)      │
                                    └─────────────────────┘

快照时机: 用户选择方案 → 立即读取方案当前内容 → 写入 scheme_snapshot(JSON)
快照隔离: 方案后续更新不影响已创建记录，仅新创建记录使用最新方案
```


---

## 5. Low-Level Design

### 5.1 工程量清单 — Excel 解析与层级构建

```java
package com.zwinsight.contract.service;

@Service
@RequiredArgsConstructor
public class BoqService {

    private final BoqItemMapper boqItemMapper;
    private final ConstructionContractMapper contractMapper;
    private final OutputReportMapper outputReportMapper;
    private final FileStorageService fileStorageService;

    private static final int MAX_ITEMS = 5000;
    private static final int MAX_LEVEL = 4;
    private static final long MAX_FILE_SIZE = 20 * 1024 * 1024; // 20MB

    /**
     * 上传并解析 BOQ Excel 文件
     */
    @Transactional
    public BoqUploadResultVO uploadBoq(Long contractId, MultipartFile file) {
        // 1. 合同状态校验
        ConstructionContract contract = contractMapper.selectById(contractId);
        if (contract == null) {
            throw new BusinessException("施工合同不存在");
        }
        if (!List.of("EFFECTIVE", "CHANGING").contains(contract.getStatus())) {
            throw new BusinessException("当前合同状态不允许上传清单，合同须处于生效或变更中状态");
        }

        // 2. 文件大小校验
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException("文件大小超过20MB限制");
        }

        // 3. 检查是否被产值上报引用
        Long referencedCount = outputReportMapper.countByContractWithBoqRef(contractId);
        if (referencedCount > 0) {
            throw new BusinessException("该合同清单条目已被产值上报引用，无法覆盖更新");
        }

        // 4. 存储原始文件到 MinIO
        String fileUrl = fileStorageService.upload(file, "boq/" + contractId);

        // 5. EasyExcel 解析
        List<BoqExcelRow> rows = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        
        EasyExcel.read(file.getInputStream(), BoqExcelRow.class, 
            new BoqReadListener(rows, errors, MAX_ITEMS))
            .sheet()
            .doRead();

        if (!errors.isEmpty()) {
            throw new BusinessException("文件解析失败", errors);
        }

        // 6. 删除旧数据
        boqItemMapper.deleteByContractId(contractId);

        // 7. 构建层级树并批量插入
        List<BoqItem> items = buildHierarchy(rows, contractId);
        boqItemMapper.insertBatch(items);

        // 8. 计算合计金额并回写合同
        BigDecimal totalAmount = items.stream()
            .filter(i -> i.getParentId() == 0)
            .map(BoqItem::getTotalPrice)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(2, RoundingMode.HALF_UP);
        
        contractMapper.updateContractAmount(contractId, totalAmount);

        return new BoqUploadResultVO(items.size(), getMaxLevel(items), totalAmount, fileUrl);
    }

    /**
     * 根据项目编码构建父子层级
     * 编码规则: 1 → 1.1 → 1.1.1 → 1.1.1.1（最多4级）
     */
    private List<BoqItem> buildHierarchy(List<BoqExcelRow> rows, Long contractId) {
        List<BoqItem> items = new ArrayList<>();
        Map<String, Long> codeToIdMap = new HashMap<>();
        long tempId = 1;

        for (BoqExcelRow row : rows) {
            BoqItem item = convertToEntity(row, contractId);
            item.setId(tempId++);

            // 计算父级编码
            String parentCode = getParentCode(row.getItemCode());
            if (parentCode != null && codeToIdMap.containsKey(parentCode)) {
                item.setParentId(codeToIdMap.get(parentCode));
            } else {
                item.setParentId(0L);
            }

            // 计算层级
            int level = row.getItemCode().split("\\.").length;
            if (level > MAX_LEVEL) {
                throw new BusinessException("编码 " + row.getItemCode() + " 超过最大层级限制(4级)");
            }
            item.setLevel(level);

            codeToIdMap.put(row.getItemCode(), item.getId());
            items.add(item);
        }

        // 重置ID让数据库自增
        items.forEach(i -> i.setId(null));
        return items;
    }

    /**
     * 获取父级编码: "1.2.3" → "1.2", "1" → null
     */
    private String getParentCode(String code) {
        int lastDot = code.lastIndexOf('.');
        return lastDot > 0 ? code.substring(0, lastDot) : null;
    }
}
```

**EasyExcel 读取监听器：**

```java
@Slf4j
public class BoqReadListener implements ReadListener<BoqExcelRow> {

    private final List<BoqExcelRow> dataList;
    private final List<String> errors;
    private final int maxItems;
    private int rowIndex = 0;

    @Override
    public void invoke(BoqExcelRow row, AnalysisContext context) {
        rowIndex++;
        if (dataList.size() >= maxItems) {
            throw new BusinessException("清单条目超过" + maxItems + "条限制");
        }
        
        // 校验必填字段
        List<String> rowErrors = validateRow(row, rowIndex);
        if (!rowErrors.isEmpty()) {
            if (errors.size() < 100) {
                errors.addAll(rowErrors);
            }
            return;
        }
        dataList.add(row);
    }

    @Override
    public void doAfterAllAnalysed(AnalysisContext context) {
        log.info("BOQ解析完成, 共 {} 条有效数据", dataList.size());
    }

    private List<String> validateRow(BoqExcelRow row, int index) {
        List<String> errs = new ArrayList<>();
        if (StringUtils.isBlank(row.getItemCode())) {
            errs.add("第" + index + "行: 项目编码不能为空");
        }
        if (StringUtils.isBlank(row.getItemName())) {
            errs.add("第" + index + "行: 项目名称不能为空");
        }
        return errs;
    }
}
```

### 5.2 目标成本变更 — 审批回调

```java
package com.zwinsight.budget.service;

@Service
@RequiredArgsConstructor
public class BudgetChangeService {

    private final BudgetChangeMapper changeMapper;
    private final BudgetChangeDetailMapper detailMapper;
    private final BudgetDetailMapper budgetDetailMapper;
    private final BudgetMapper budgetMapper;
    private final ProjectMapper projectMapper;
    private final ApprovalService approvalService;
    private final ExpenseContractMapper expenseContractMapper;

    /**
     * 提交变更申请前的预算余额校验
     */
    public void validateBeforeSubmit(BudgetChangeDTO dto) {
        for (BudgetChangeDetailDTO detail : dto.getDetails()) {
            if (detail.getAdjustAmount().compareTo(BigDecimal.ZERO) < 0) {
                // 调减时检查已占用预算
                BigDecimal occupied = calculateOccupiedBudget(
                    dto.getProjectId(), detail.getBudgetDetailId());
                BigDecimal adjustedAmount = detail.getOriginalAmount()
                    .add(detail.getAdjustAmount());
                
                if (adjustedAmount.compareTo(occupied) < 0) {
                    throw new BusinessException(
                        "科目[" + detail.getItemName() + "]预算余额不足以支撑调减，" +
                        "已占用预算: " + occupied + "，调整后金额: " + adjustedAmount);
                }
            }
        }
    }

    /**
     * 计算科目已占用预算 = 已签合同金额 + 已付无合同费用
     */
    private BigDecimal calculateOccupiedBudget(Long projectId, Long budgetDetailId) {
        BudgetDetail budgetDetail = budgetDetailMapper.selectById(budgetDetailId);
        // 查询该科目下已签合同金额
        BigDecimal contractAmount = expenseContractMapper
            .sumContractAmountByProjectAndCategory(
                projectId, budgetDetail.getCostCategory());
        return contractAmount != null ? contractAmount : BigDecimal.ZERO;
    }

    /**
     * 提交审批
     */
    @Transactional
    public void submit(Long changeId) {
        BudgetChange change = changeMapper.selectById(changeId);
        validateBeforeSubmit(convertToDTO(change));

        Map<String, Object> variables = new HashMap<>();
        variables.put("businessType", "BUDGET_CHANGE");
        variables.put("projectId", change.getProjectId());
        variables.put("amount", change.getTotalAdjustAmount());

        String processInstanceId = approvalService.startProcess(
            "budget_change", changeId,
            SecurityContext.getCurrentTenantId(), variables);

        change.setStatus("SUBMITTED");
        change.setWorkflowInstanceId(processInstanceId);
        changeMapper.updateById(change);
    }

    /**
     * 审批通过回调 — 执行预算回写
     */
    @Transactional
    public void onApproved(Long changeId) {
        BudgetChange change = changeMapper.selectById(changeId);
        change.setStatus("APPROVED");
        changeMapper.updateById(change);

        List<BudgetChangeDetail> details = detailMapper.selectByChangeId(changeId);
        
        // 逐科目回写原预算明细
        for (BudgetChangeDetail detail : details) {
            budgetDetailMapper.addBudgetTotalPrice(
                detail.getBudgetDetailId(), detail.getAdjustAmount());
        }

        // 更新原预算总额
        Budget budget = budgetMapper.selectById(change.getBudgetId());
        BigDecimal newTotal = budgetDetailMapper.sumBudgetTotalPriceByBudgetId(
            budget.getId());
        budget.setTotalAmount(newTotal);
        budgetMapper.updateById(budget);

        // 回写项目预算金额
        projectMapper.updateBudgetAmount(change.getProjectId(), newTotal);
    }
}
```

### 5.3 验证码服务 — 图形验证码与频率限制

```java
package com.zwinsight.security.service;

@Service
@RequiredArgsConstructor
@Slf4j
public class CaptchaService {

    private final StringRedisTemplate redisTemplate;
    private final SmsClient smsClient; // 阿里云短信客户端

    private static final String CAPTCHA_KEY = "captcha:";
    private static final String SMS_KEY = "sms:";
    private static final String SMS_FREQ_KEY = "sms:freq:";
    private static final String SMS_DAILY_KEY = "sms:daily:";
    private static final String IP_LOCK_KEY = "login:ip:lock:";
    private static final String IP_FAIL_KEY = "login:ip:fail:";

    private static final int CAPTCHA_EXPIRE = 300;      // 5分钟
    private static final int SMS_FREQ_EXPIRE = 60;      // 60秒
    private static final int SMS_DAILY_LIMIT = 10;
    private static final int IP_FAIL_LIMIT = 5;
    private static final int IP_LOCK_DURATION = 900;    // 15分钟

    /**
     * 生成图形验证码
     */
    public CaptchaVO generateImageCaptcha() {
        // 使用 Hutool 生成验证码
        LineCaptcha captcha = CaptchaUtil.createLineCaptcha(130, 48, 4, 80);
        String code = captcha.getCode();
        String uuid = UUID.randomUUID().toString().replace("-", "");
        String imageBase64 = captcha.getImageBase64();

        // 存入 Redis
        redisTemplate.opsForValue().set(
            CAPTCHA_KEY + uuid, code, CAPTCHA_EXPIRE, TimeUnit.SECONDS);

        return new CaptchaVO(uuid, "data:image/png;base64," + imageBase64);
    }

    /**
     * 校验图形验证码
     */
    public boolean verifyImageCaptcha(String uuid, String inputCode) {
        String key = CAPTCHA_KEY + uuid;
        String storedCode = redisTemplate.opsForValue().get(key);
        
        // 无论成功失败都删除（一次性使用）
        redisTemplate.delete(key);

        if (storedCode == null) {
            return false; // 已过期或不存在
        }
        return storedCode.equalsIgnoreCase(inputCode);
    }

    /**
     * 发送短信验证码
     */
    public void sendSmsCode(String phone, String clientIp) {
        // 1. 手机号格式校验
        if (!phone.matches("^1[3-9]\\d{9}$")) {
            throw new BusinessException("手机号格式无效");
        }

        // 2. 频率限制 - 60秒内不可重复发送
        String freqKey = SMS_FREQ_KEY + phone;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(freqKey))) {
            Long ttl = redisTemplate.getExpire(freqKey, TimeUnit.SECONDS);
            throw new BusinessException("发送过于频繁，请" + ttl + "秒后重试");
        }

        // 3. 日限额检查
        String dailyKey = SMS_DAILY_KEY + phone;
        String dailyCount = redisTemplate.opsForValue().get(dailyKey);
        if (dailyCount != null && Integer.parseInt(dailyCount) >= SMS_DAILY_LIMIT) {
            throw new BusinessException("今日短信发送次数已达上限(10次)");
        }

        // 4. 生成6位数字验证码
        String code = String.format("%06d", new Random().nextInt(1000000));

        // 5. 存储验证码到 Redis
        redisTemplate.opsForValue().set(SMS_KEY + phone, code, CAPTCHA_EXPIRE, TimeUnit.SECONDS);

        // 6. 设置频率限制
        redisTemplate.opsForValue().set(freqKey, "1", SMS_FREQ_EXPIRE, TimeUnit.SECONDS);

        // 7. 日计数器递增
        redisTemplate.opsForValue().increment(dailyKey);
        if (dailyCount == null) {
            // 首次设置过期时间到当天结束
            redisTemplate.expire(dailyKey, getSecondsUntilEndOfDay(), TimeUnit.SECONDS);
        }

        // 8. 调用阿里云短信 SDK 发送
        smsClient.sendVerificationCode(phone, code);
    }

    /**
     * IP 登录失败计数与锁定检查
     */
    public void checkIpLock(String clientIp) {
        String lockKey = IP_LOCK_KEY + clientIp;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(lockKey))) {
            Long ttl = redisTemplate.getExpire(lockKey, TimeUnit.SECONDS);
            throw new BusinessException("该IP已被临时锁定，剩余" + (ttl / 60) + "分钟");
        }
    }

    public void recordIpFailure(String clientIp) {
        String failKey = IP_FAIL_KEY + clientIp;
        Long count = redisTemplate.opsForValue().increment(failKey);
        redisTemplate.expire(failKey, 300, TimeUnit.SECONDS); // 5分钟窗口

        if (count != null && count >= IP_FAIL_LIMIT) {
            // 锁定IP
            redisTemplate.opsForValue().set(IP_LOCK_KEY + clientIp, "1",
                IP_LOCK_DURATION, TimeUnit.SECONDS);
            redisTemplate.delete(failKey);
        }
    }

    private long getSecondsUntilEndOfDay() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime endOfDay = now.toLocalDate().atTime(23, 59, 59);
        return Duration.between(now, endOfDay).getSeconds();
    }
}
```

### 5.4 质保金预警定时任务

```java
package com.zwinsight.finance.task;

@Component
@RequiredArgsConstructor
@Slf4j
public class RetentionWarningTask {

    private final RetentionMapper retentionMapper;
    private final RetentionWarningLogMapper warningLogMapper;
    private final MessageService messageService;
    private final ProjectMemberMapper projectMemberMapper;
    private final StringRedisTemplate redisTemplate;

    private static final String WARNED_KEY_PREFIX = "retention:warned:";
    private static final String OVERDUE_LAST_PREFIX = "retention:overdue:last:";
    private static final int MAX_RETRY = 3;
    private static final int LONG_OVERDUE_DAYS = 180;

    @Scheduled(cron = "0 0 8 * * ?")
    public void execute() {
        log.info("质保金预警定时任务开始执行");
        LocalDate today = LocalDate.now();
        LocalDate thirtyDaysLater = today.plusDays(30);

        // 1. 查询需要预警的质保金记录
        List<RetentionRecord> records = retentionMapper.selectUnreturned(thirtyDaysLater);
        
        int processedCount = 0;
        int sentCount = 0;

        for (RetentionRecord record : records) {
            try {
                processedCount++;
                boolean sent = processRecord(record, today);
                if (sent) sentCount++;
            } catch (Exception e) {
                log.error("处理质保金预警异常, retentionId={}", record.getId(), e);
            }
        }

        // 2. 重试之前失败的通知
        retryFailedNotifications();

        log.info("质保金预警任务完成, 处理{}条, 发送通知{}条", processedCount, sentCount);
    }

    private boolean processRecord(RetentionRecord record, LocalDate today) {
        long daysUntilExpire = ChronoUnit.DAYS.between(today, record.getExpireDate());
        
        // 确定预警级别
        String level;
        if (daysUntilExpire < 0) {
            // 已逾期
            long overdueDays = Math.abs(daysUntilExpire);
            if (overdueDays > LONG_OVERDUE_DAYS) {
                retentionMapper.updateStatus(record.getId(), "LONG_OVERDUE");
                return false; // 停止催办
            }
            level = "OVERDUE";
            
            // 逾期催办: 每3天一次
            String lastKey = OVERDUE_LAST_PREFIX + record.getId();
            String lastSent = redisTemplate.opsForValue().get(lastKey);
            if (lastSent != null) {
                LocalDate lastDate = LocalDate.parse(lastSent);
                if (ChronoUnit.DAYS.between(lastDate, today) < 3) {
                    return false; // 未到催办时间
                }
            }
        } else if (daysUntilExpire <= 7) {
            level = "URGENT";
        } else {
            level = "UPCOMING";
        }

        // 去重检查
        String warnedKey = WARNED_KEY_PREFIX + record.getId() + ":" + level;
        if (!"OVERDUE".equals(level)) {
            // 非逾期类型: 同级别只发一次
            if (Boolean.TRUE.equals(redisTemplate.hasKey(warnedKey))) {
                return false;
            }
        }

        // 发送通知
        boolean success = sendWarningNotification(record, level);
        
        if (success) {
            // 记录已发送
            if (!"OVERDUE".equals(level)) {
                redisTemplate.opsForValue().set(warnedKey, "1");
            } else {
                redisTemplate.opsForValue().set(
                    OVERDUE_LAST_PREFIX + record.getId(), today.toString());
            }
            // 记录日志
            saveWarningLog(record.getId(), level, "SENT");
        } else {
            saveWarningLog(record.getId(), level, "FAILED");
        }

        return success;
    }

    private boolean sendWarningNotification(RetentionRecord record, String level) {
        // 获取项目负责人和财务人员
        List<Long> receiverIds = projectMemberMapper.selectByRoleTypes(
            record.getProjectId(),
            List.of("PROJECT_MANAGER", "FINANCE_OFFICER"));

        String title = buildTitle(level);
        String content = buildContent(record, level);

        try {
            messageService.sendInternalMessage(receiverIds, title, content);
            return true;
        } catch (Exception e) {
            log.error("发送质保金预警通知失败, retentionId={}", record.getId(), e);
            return false;
        }
    }

    /**
     * 质保金状态变更为已退还时清除去重记录
     */
    public void onRetentionReturned(Long retentionId) {
        // 清除所有级别的去重key
        Set<String> keys = redisTemplate.keys(WARNED_KEY_PREFIX + retentionId + ":*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
        redisTemplate.delete(OVERDUE_LAST_PREFIX + retentionId);
    }

    private void retryFailedNotifications() {
        List<RetentionWarningLog> failedLogs = warningLogMapper
            .selectByStatus("FAILED", MAX_RETRY);
        for (RetentionWarningLog logEntry : failedLogs) {
            RetentionRecord record = retentionMapper.selectById(logEntry.getRetentionId());
            if (record == null || "RETURNED".equals(record.getStatus())) continue;

            boolean success = sendWarningNotification(record, logEntry.getWarningLevel());
            if (success) {
                logEntry.setNotifyStatus("SENT");
                logEntry.setSentAt(LocalDateTime.now());
            } else {
                logEntry.setRetryCount(logEntry.getRetryCount() + 1);
                if (logEntry.getRetryCount() >= MAX_RETRY) {
                    logEntry.setNotifyStatus("PERMANENTLY_FAILED");
                }
            }
            warningLogMapper.updateById(logEntry);
        }
    }
}
```


### 5.5 预算控制配置 — 拦截器改造

```java
package com.zwinsight.budget.service;

@Service
@RequiredArgsConstructor
@Slf4j
public class BudgetControlConfigService {

    private final BudgetControlConfigMapper configMapper;
    private final BudgetDetailMapper budgetDetailMapper;
    private final ExpenseContractMapper expenseContractMapper;
    private final PaymentApplyMapper paymentApplyMapper;
    private final MessageService messageService;
    private final ProjectMemberMapper projectMemberMapper;

    /**
     * 获取项目生效的预算控制配置
     * 优先项目级 → 回落系统默认
     */
    public BudgetControlConfig getEffectiveConfig(Long projectId) {
        try {
            BudgetControlConfig config = configMapper.selectByProjectId(projectId);
            if (config != null) {
                return config;
            }
            // 查询系统默认配置
            config = configMapper.selectDefault();
            if (config != null) {
                return config;
            }
        } catch (Exception e) {
            log.error("查询预算控制配置异常, projectId={}", projectId, e);
        }
        // 配置异常时使用硬编码默认值
        return BudgetControlConfig.hardCodedDefault(); // BLOCK, 80%
    }

    /**
     * 预算控制校验（被 BudgetControlAspect 调用）
     * @return 校验结果: PASS/WARN/BLOCK
     */
    public BudgetCheckResult checkBudget(Long projectId, String costCategory, 
                                          BigDecimal newAmount) {
        BudgetControlConfig config = getEffectiveConfig(projectId);
        
        if (config.getControlMode() == ControlMode.EXEMPT) {
            return BudgetCheckResult.pass();
        }

        // 计算该科目预算执行率
        BigDecimal budgetAmount = budgetDetailMapper
            .sumBudgetByProjectAndCategory(projectId, costCategory);
        if (budgetAmount == null || budgetAmount.compareTo(BigDecimal.ZERO) <= 0) {
            // 无预算数据时按 BLOCK 处理
            return config.getControlMode() == ControlMode.BLOCK
                ? BudgetCheckResult.block("该科目未设置预算额度")
                : BudgetCheckResult.warn("该科目未设置预算额度");
        }

        BigDecimal usedAmount = calculateUsedAmount(projectId, costCategory);
        BigDecimal totalAfter = usedAmount.add(newAmount);
        BigDecimal executionRate = totalAfter.divide(budgetAmount, 4, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100));

        // 预警阈值检查
        if (executionRate.intValue() >= config.getWarningThreshold() 
            && executionRate.intValue() < 100) {
            sendWarningNotification(projectId, costCategory, executionRate.intValue());
        }

        // 超支控制
        if (executionRate.compareTo(BigDecimal.valueOf(100)) > 0) {
            String msg = String.format("科目[%s]预算执行率%.2f%%，已超预算",
                costCategory, executionRate);
            
            if (config.getControlMode() == ControlMode.BLOCK) {
                return BudgetCheckResult.block(msg);
            } else {
                return BudgetCheckResult.warn(msg);
            }
        }

        return BudgetCheckResult.pass();
    }

    private BigDecimal calculateUsedAmount(Long projectId, String costCategory) {
        // 已签合同金额
        BigDecimal contractUsed = expenseContractMapper
            .sumApprovedContractAmount(projectId, costCategory);
        // 已审批的无合同付款
        BigDecimal directPayment = paymentApplyMapper
            .sumApprovedNoContractPayment(projectId, costCategory);
        
        return (contractUsed != null ? contractUsed : BigDecimal.ZERO)
            .add(directPayment != null ? directPayment : BigDecimal.ZERO);
    }

    private void sendWarningNotification(Long projectId, String category, int rate) {
        List<Long> receivers = projectMemberMapper.selectByRoleTypes(
            projectId, List.of("PROJECT_MANAGER"));
        String content = String.format("项目预算预警：科目[%s]执行率已达%d%%，请关注预算使用情况",
            category, rate);
        messageService.sendInternalMessage(receivers, "预算预警通知", content);
    }
}
```

**改造现有 BudgetControlAspect：**

```java
@Aspect
@Component
@RequiredArgsConstructor
public class BudgetControlAspect {

    private final BudgetControlConfigService configService;

    @Before("@annotation(budgetCheck)")
    public void checkBudget(JoinPoint joinPoint, BudgetCheck budgetCheck) {
        Long projectId = extractProjectId(joinPoint);
        String costCategory = budgetCheck.category();
        BigDecimal amount = extractAmount(joinPoint);

        BudgetCheckResult result = configService.checkBudget(
            projectId, costCategory, amount);

        switch (result.getStatus()) {
            case BLOCK:
                throw new BudgetExceededException(result.getMessage());
            case WARN:
                // 设置线程变量，前端通过响应头判断是否弹窗
                BudgetWarningContext.setWarning(result.getMessage());
                break;
            case PASS:
                break;
        }
    }
}
```

**预算校验结果对象：**

```java
@Data
@AllArgsConstructor
public class BudgetCheckResult {
    public enum Status { PASS, WARN, BLOCK }
    
    private Status status;
    private String message;

    public static BudgetCheckResult pass() {
        return new BudgetCheckResult(Status.PASS, null);
    }
    public static BudgetCheckResult warn(String msg) {
        return new BudgetCheckResult(Status.WARN, msg);
    }
    public static BudgetCheckResult block(String msg) {
        return new BudgetCheckResult(Status.BLOCK, msg);
    }
}
```

### 5.6 检查方案关联 — 方案选择与快照

```java
package com.zwinsight.site.service;

@Service
@RequiredArgsConstructor
public class InspectionSchemeService {

    private final InspectionSchemeMapper schemeMapper;
    private final InspectionSchemeItemMapper schemeItemMapper;
    private final InspectionMapper inspectionMapper;
    private final InspectionDetailMapper inspectionDetailMapper;
    private final ObjectMapper objectMapper;

    /**
     * 查询可用检查方案列表（按类型筛选）
     */
    public IPage<InspectionScheme> listSchemes(String inspectionType, int page, int size) {
        Page<InspectionScheme> pageParam = new Page<>(page, Math.min(size, 50));
        LambdaQueryWrapper<InspectionScheme> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(InspectionScheme::getInspectionType, inspectionType)
               .eq(InspectionScheme::getStatus, "ENABLED")
               .orderByDesc(InspectionScheme::getCreatedAt);
        return schemeMapper.selectPage(pageParam, wrapper);
    }

    /**
     * 将方案关联到检查记录
     */
    @Transactional
    public void applyScheme(Long inspectionId, Long schemeId) {
        Inspection inspection = inspectionMapper.selectById(inspectionId);
        if (inspection == null) {
            throw new BusinessException("检查记录不存在");
        }

        // 读取方案及检查项
        InspectionScheme scheme = schemeMapper.selectById(schemeId);
        if (scheme == null || !"ENABLED".equals(scheme.getStatus())) {
            throw new BusinessException("检查方案不存在或已停用");
        }
        List<InspectionSchemeItem> schemeItems = schemeItemMapper.selectBySchemeId(schemeId);

        // 构建方案快照 JSON
        SchemeSnapshotDTO snapshot = new SchemeSnapshotDTO();
        snapshot.setSchemeId(schemeId);
        snapshot.setSchemeName(scheme.getSchemeName());
        snapshot.setItems(schemeItems.stream().map(item -> {
            SchemeSnapshotDTO.ItemDTO dto = new SchemeSnapshotDTO.ItemDTO();
            dto.setItemName(item.getItemName());
            dto.setCheckStandard(item.getCheckStandard());
            dto.setCheckMethod(item.getCheckMethod());
            return dto;
        }).collect(Collectors.toList()));

        String snapshotJson;
        try {
            snapshotJson = objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException e) {
            throw new BusinessException("方案快照序列化失败");
        }

        // 清除现有检查明细
        inspectionDetailMapper.deleteByInspectionId(inspectionId);

        // 填充新的检查明细
        List<InspectionDetail> details = new ArrayList<>();
        int sortOrder = 1;
        for (InspectionSchemeItem item : schemeItems) {
            InspectionDetail detail = new InspectionDetail();
            detail.setTenantId(inspection.getTenantId());
            detail.setInspectionId(inspectionId);
            detail.setItemName(item.getItemName());
            detail.setCheckStandard(item.getCheckStandard());
            detail.setCheckMethod(item.getCheckMethod());
            detail.setCheckResult("NOT_CHECKED");
            detail.setSortOrder(sortOrder++);
            details.add(detail);
        }
        inspectionDetailMapper.insertBatch(details);

        // 更新检查记录的方案关联和快照
        inspection.setSchemeId(schemeId);
        inspection.setSchemeSnapshot(snapshotJson);
        inspectionMapper.updateById(inspection);
    }
}
```

**方案快照 DTO：**

```java
@Data
public class SchemeSnapshotDTO {
    private Long schemeId;
    private String schemeName;
    private List<ItemDTO> items;

    @Data
    public static class ItemDTO {
        private String itemName;
        private String checkStandard;
        private String checkMethod;
    }
}
```

### 5.7 项目最终结算 — 数据汇总与导出

```java
package com.zwinsight.finance.service;

@Service
@RequiredArgsConstructor
public class ProjectSettlementService {

    private final ProjectSettlementMapper settlementMapper;
    private final SettlementContractDetailMapper detailMapper;
    private final ProjectMapper projectMapper;
    private final ConstructionContractMapper constructionContractMapper;
    private final ExpenseContractMapper expenseContractMapper;
    private final PaymentReceivedMapper paymentReceivedMapper;
    private final InvoiceApplyMapper invoiceApplyMapper;
    private final PaymentApplyMapper paymentApplyMapper;
    private final ApprovalService approvalService;

    /**
     * 创建结算单（自动汇总数据）
     */
    @Transactional
    public Long createSettlement(Long projectId) {
        // 1. 校验项目状态
        Project project = projectMapper.selectById(projectId);
        if (!"COMPLETED".equals(project.getStatus())) {
            throw new BusinessException("项目未竣工，无法进行最终结算");
        }

        // 2. 校验不存在草稿/审批中结算单
        Long existingCount = settlementMapper.countActiveByProject(projectId);
        if (existingCount > 0) {
            throw new BusinessException("该项目已存在进行中的结算单");
        }

        // 3. 汇总收入数据
        ConstructionContract mainContract = constructionContractMapper
            .selectMainByProject(projectId);
        BigDecimal contractAmount = mainContract != null ? 
            mainContract.getContractAmount() : BigDecimal.ZERO;
        BigDecimal cumulativeOutput = mainContract != null ? 
            mainContract.getCumulativeOutput() : BigDecimal.ZERO;
        BigDecimal cumulativeReceived = paymentReceivedMapper
            .sumByProject(projectId);
        BigDecimal cumulativeInvoiced = invoiceApplyMapper
            .sumApprovedByProject(projectId);
        BigDecimal totalIncome = cumulativeReceived != null ? 
            cumulativeReceived : BigDecimal.ZERO;

        // 4. 汇总支出数据
        BigDecimal subcontractSettled = expenseContractMapper
            .sumSettlementByCategory(projectId, "SUBCONTRACT");
        BigDecimal laborSettled = expenseContractMapper
            .sumSettlementByCategory(projectId, "LABOR");
        BigDecimal materialSettled = expenseContractMapper
            .sumSettlementByCategory(projectId, "MATERIAL");
        BigDecimal machineSettled = expenseContractMapper
            .sumSettlementByCategory(projectId, "MACHINE");
        BigDecimal cumulativePaid = paymentApplyMapper
            .sumApprovedByProject(projectId);
        BigDecimal totalExpenditure = Stream.of(
            subcontractSettled, laborSettled, materialSettled, 
            machineSettled, cumulativePaid)
            .map(v -> v != null ? v : BigDecimal.ZERO)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 5. 计算利润
        BigDecimal profit = totalIncome.subtract(totalExpenditure)
            .setScale(2, RoundingMode.HALF_UP);
        BigDecimal profitRate = totalIncome.compareTo(BigDecimal.ZERO) > 0
            ? profit.divide(totalIncome, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;

        // 6. 创建结算单
        ProjectSettlement settlement = new ProjectSettlement();
        settlement.setProjectId(projectId);
        settlement.setTenantId(SecurityContext.getCurrentTenantId());
        settlement.setConstructionContractAmount(contractAmount);
        settlement.setCumulativeOutput(cumulativeOutput);
        settlement.setCumulativeReceived(cumulativeReceived != null ? cumulativeReceived : BigDecimal.ZERO);
        settlement.setCumulativeInvoiced(cumulativeInvoiced != null ? cumulativeInvoiced : BigDecimal.ZERO);
        settlement.setTotalIncome(totalIncome);
        settlement.setSubcontractSettled(subcontractSettled != null ? subcontractSettled : BigDecimal.ZERO);
        settlement.setLaborSettled(laborSettled != null ? laborSettled : BigDecimal.ZERO);
        settlement.setMaterialSettled(materialSettled != null ? materialSettled : BigDecimal.ZERO);
        settlement.setMachineSettled(machineSettled != null ? machineSettled : BigDecimal.ZERO);
        settlement.setCumulativePaid(cumulativePaid != null ? cumulativePaid : BigDecimal.ZERO);
        settlement.setTotalExpenditure(totalExpenditure);
        settlement.setProfit(profit);
        settlement.setProfitRate(profitRate);
        settlement.setStatus("DRAFT");
        settlement.setCreatedBy(SecurityContext.getCurrentUserId());
        settlementMapper.insert(settlement);

        // 7. 生成合同明细
        generateContractDetails(settlement.getId(), projectId);

        return settlement.getId();
    }

    /**
     * 审批通过回调 — 关闭项目
     */
    @Transactional
    public void onApproved(Long settlementId) {
        ProjectSettlement settlement = settlementMapper.selectById(settlementId);
        settlement.setStatus("APPROVED");
        settlementMapper.updateById(settlement);

        // 更新项目状态为 CLOSED
        projectMapper.updateStatus(settlement.getProjectId(), "CLOSED");
        // 回写结算金额
        projectMapper.updateSettlementAmount(
            settlement.getProjectId(), settlement.getTotalIncome());
    }

    /**
     * 查询未结清合同列表
     */
    public List<UnsettledContractVO> getUnsettledContracts(Long projectId) {
        return expenseContractMapper.selectUnsettledByProject(projectId);
    }

    private void generateContractDetails(Long settlementId, Long projectId) {
        List<ExpenseContract> contracts = expenseContractMapper
            .selectByProject(projectId);
        
        List<SettlementContractDetail> details = contracts.stream().map(c -> {
            SettlementContractDetail d = new SettlementContractDetail();
            d.setSettlementId(settlementId);
            d.setTenantId(SecurityContext.getCurrentTenantId());
            d.setContractType(c.getContractCategory());
            d.setContractId(c.getId());
            d.setContractCode(c.getContractCode());
            d.setContractAmount(c.getContractAmount());
            d.setSettledAmount(c.getCumulativeSettlement());
            d.setPaidAmount(c.getCumulativePaid());
            d.setUnsettledAmount(c.getContractAmount()
                .subtract(c.getCumulativeSettlement()));
            return d;
        }).collect(Collectors.toList());
        
        detailMapper.insertBatch(details);
    }
}
```


---

## 6. 前端设计

### 6.1 新增页面清单

| 功能 | 页面路径 | 页面说明 |
|------|---------|---------|
| BOQ上传 | `views/contract/boq-upload.vue` | 施工合同详情内嵌的清单上传Tab |
| 目标成本变更 | `views/budget/change/index.vue` | 变更单列表 |
| 目标成本变更 | `views/budget/change/form.vue` | 变更单新建/编辑 |
| 项目最终结算 | `views/finance/settlement/index.vue` | 结算单列表 |
| 项目最终结算 | `views/finance/settlement/detail.vue` | 结算单详情 |
| 验证码登录 | 修改 `views/login/index.vue` | 登录页增加验证码输入 |
| 预算控制配置 | `views/budget/control-config/index.vue` | 配置列表CRUD |
| 检查方案关联 | 修改 `views/site/inspection/form.vue` | 检查表单增加方案选择 |

### 6.2 新增 API 文件

```typescript
// src/api/boq.ts
import request from '@/utils/request'

export function uploadBoq(contractId: number, file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post(`/api/v1/contracts/${contractId}/boq/upload`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 120000 // BOQ解析可能耗时较长
  })
}

export function getBoqTree(contractId: number) {
  return request.get(`/api/v1/contracts/${contractId}/boq`)
}

export function deleteBoq(contractId: number) {
  return request.delete(`/api/v1/contracts/${contractId}/boq`)
}
```

```typescript
// src/api/budget-change.ts
import request from '@/utils/request'

export function listBudgetChanges(params: any) {
  return request.get('/api/v1/budget-changes', { params })
}

export function getBudgetChange(id: number) {
  return request.get(`/api/v1/budget-changes/${id}`)
}

export function createBudgetChange(data: any) {
  return request.post('/api/v1/budget-changes', data)
}

export function updateBudgetChange(id: number, data: any) {
  return request.put(`/api/v1/budget-changes/${id}`, data)
}

export function deleteBudgetChange(id: number) {
  return request.delete(`/api/v1/budget-changes/${id}`)
}

export function submitBudgetChange(id: number) {
  return request.post(`/api/v1/budget-changes/${id}/submit`)
}

export function withdrawBudgetChange(id: number) {
  return request.post(`/api/v1/budget-changes/${id}/withdraw`)
}
```

```typescript
// src/api/settlement.ts
import request from '@/utils/request'

export function createSettlement(projectId: number) {
  return request.post('/api/v1/project-settlements', { projectId })
}

export function getSettlement(id: number) {
  return request.get(`/api/v1/project-settlements/${id}`)
}

export function submitSettlement(id: number) {
  return request.post(`/api/v1/project-settlements/${id}/submit`)
}

export function exportSettlement(id: number) {
  return request.post(`/api/v1/project-settlements/${id}/export`, null, {
    responseType: 'blob'
  })
}

export function getUnsettledContracts(id: number) {
  return request.get(`/api/v1/project-settlements/${id}/unsettled-contracts`)
}
```

```typescript
// src/api/captcha.ts
import request from '@/utils/request'

export function getImageCaptcha() {
  return request.get('/api/v1/captcha/image')
}

export function sendSmsCaptcha(phone: string) {
  return request.post('/api/v1/captcha/sms', { phone })
}
```

```typescript
// src/api/budget-control-config.ts
import request from '@/utils/request'

export function listBudgetControlConfigs(params: any) {
  return request.get('/api/v1/budget-control-configs', { params })
}

export function createBudgetControlConfig(data: any) {
  return request.post('/api/v1/budget-control-configs', data)
}

export function updateBudgetControlConfig(id: number, data: any) {
  return request.put(`/api/v1/budget-control-configs/${id}`, data)
}

export function deleteBudgetControlConfig(id: number) {
  return request.delete(`/api/v1/budget-control-configs/${id}`)
}
```

### 6.3 登录页验证码改造

```vue
<!-- login/index.vue 关键改动 -->
<template>
  <el-form-item label="验证码" prop="captchaCode">
    <div class="captcha-row">
      <el-input v-model="loginForm.captchaCode" placeholder="请输入验证码" />
      <img 
        :src="captchaImage" 
        class="captcha-img" 
        @click="refreshCaptcha"
        title="点击刷新"
      />
    </div>
  </el-form-item>
</template>

<script setup lang="ts">
import { getImageCaptcha } from '@/api/captcha'

const captchaImage = ref('')
const captchaUuid = ref('')

async function refreshCaptcha() {
  const { data } = await getImageCaptcha()
  captchaImage.value = data.imageBase64
  captchaUuid.value = data.uuid
}

async function handleLogin() {
  const params = {
    ...loginForm,
    captchaUuid: captchaUuid.value
  }
  try {
    await userStore.login(params)
    router.push('/')
  } catch (e) {
    // 登录失败时自动刷新验证码
    refreshCaptcha()
  }
}

onMounted(() => {
  refreshCaptcha()
})
</script>
```

### 6.4 预算校验弹窗机制（WARN_ONLY 模式）

```typescript
// src/composables/useBudgetCheck.ts
import { ElMessageBox } from 'element-plus'

export function useBudgetCheck() {
  
  /**
   * 提交业务单据，处理预算校验响应
   */
  async function submitWithBudgetCheck(submitFn: () => Promise<any>) {
    try {
      const response = await submitFn()
      
      // 检查响应头中的预算警告标识
      const budgetWarning = response.headers?.['x-budget-warning']
      if (budgetWarning) {
        // WARN_ONLY 模式: 弹窗让用户确认
        await ElMessageBox.confirm(
          decodeURIComponent(budgetWarning),
          '预算超支警告',
          {
            type: 'warning',
            confirmButtonText: '确认提交',
            cancelButtonText: '取消'
          }
        )
        // 用户确认后，带 force=true 重新提交
        return await submitFn({ force: true })
      }
      
      return response
    } catch (error: any) {
      if (error?.response?.status === 422 && error.response.data?.code === 'BUDGET_EXCEEDED') {
        // BLOCK 模式: 直接提示不可提交
        ElMessage.error(error.response.data.message)
        throw error
      }
      throw error
    }
  }

  return { submitWithBudgetCheck }
}
```

### 6.5 移动端检查方案关联

```vue
<!-- pages/inspection/form.vue（uni-app）-->
<template>
  <view class="inspection-form">
    <!-- 方案选择 -->
    <uni-section title="检查方案">
      <view class="scheme-selector" @click="showSchemePicker = true">
        <text v-if="selectedScheme">{{ selectedScheme.schemeName }}</text>
        <text v-else class="placeholder">选择检查方案（可选）</text>
      </view>
    </uni-section>

    <!-- 检查项列表 -->
    <uni-section title="检查项目" v-if="checkItems.length">
      <view 
        v-for="(item, index) in checkItems" 
        :key="index"
        class="check-item"
      >
        <view class="item-name">{{ item.itemName }}</view>
        <view class="item-standard">{{ item.checkStandard }}</view>
        <view class="result-buttons">
          <button 
            :class="{ active: item.checkResult === 'PASS' }"
            @click="item.checkResult = 'PASS'"
          >合格</button>
          <button 
            :class="{ active: item.checkResult === 'FAIL' }"
            @click="item.checkResult = 'FAIL'"
          >不合格</button>
          <button 
            :class="{ active: item.checkResult === 'NOT_CHECKED' }"
            @click="item.checkResult = 'NOT_CHECKED'"
          >未检查</button>
        </view>
      </view>
    </uni-section>
  </view>
</template>
```

---

## 7. 阿里云短信 SDK 集成

### 7.1 依赖配置

```xml
<!-- pom.xml (zw-security 模块) -->
<dependency>
    <groupId>com.aliyun</groupId>
    <artifactId>dysmsapi20170525</artifactId>
    <version>2.0.24</version>
</dependency>
```

### 7.2 短信客户端封装

```java
package com.zwinsight.security.sms;

@Component
@Slf4j
public class AliyunSmsClient implements SmsClient {

    @Value("${aliyun.sms.access-key-id}")
    private String accessKeyId;

    @Value("${aliyun.sms.access-key-secret}")
    private String accessKeySecret;

    @Value("${aliyun.sms.sign-name}")
    private String signName;

    @Value("${aliyun.sms.template-code}")
    private String templateCode;

    private com.aliyun.dysmsapi20170525.Client client;

    @PostConstruct
    public void init() throws Exception {
        Config config = new Config()
            .setAccessKeyId(accessKeyId)
            .setAccessKeySecret(accessKeySecret)
            .setEndpoint("dysmsapi.aliyuncs.com");
        this.client = new com.aliyun.dysmsapi20170525.Client(config);
    }

    @Override
    public void sendVerificationCode(String phone, String code) {
        SendSmsRequest request = new SendSmsRequest()
            .setPhoneNumbers(phone)
            .setSignName(signName)
            .setTemplateCode(templateCode)
            .setTemplateParam("{\"code\":\"" + code + "\"}");
        
        try {
            SendSmsResponse response = client.sendSms(request);
            if (!"OK".equals(response.getBody().getCode())) {
                log.error("短信发送失败: phone={}, code={}, message={}",
                    phone, response.getBody().getCode(), response.getBody().getMessage());
                throw new BusinessException("短信发送失败，请稍后重试");
            }
        } catch (Exception e) {
            log.error("短信发送异常: phone={}", phone, e);
            throw new BusinessException("短信发送失败，请稍后重试");
        }
    }
}
```

### 7.3 配置项

```yaml
# application.yml
aliyun:
  sms:
    access-key-id: ${SMS_ACCESS_KEY_ID}
    access-key-secret: ${SMS_ACCESS_KEY_SECRET}
    sign-name: "中维智营"
    template-code: "SMS_XXXXXXXXX"
```

---

## 8. 审批流程集成

### 8.1 需要新增的审批流程定义

| 流程Key | 业务类型 | 说明 |
|---------|---------|------|
| `budget_change` | 目标成本变更 | 变更审批 → 通过后回写预算 |
| `project_settlement` | 项目最终结算 | 结算审批 → 通过后关闭项目 |

### 8.2 审批回调注册

```java
// 目标成本变更审批回调
@Component("budget_change_callback")
public class BudgetChangeApprovalCallback implements ApprovalCallback {
    
    @Autowired
    private BudgetChangeService budgetChangeService;

    @Override
    @Transactional
    public void onApproved(Long businessId, String processInstanceId) {
        budgetChangeService.onApproved(businessId);
    }

    @Override
    @Transactional
    public void onRejected(Long businessId, String processInstanceId) {
        budgetChangeService.onRejected(businessId);
    }
}

// 项目最终结算审批回调
@Component("project_settlement_callback")
public class SettlementApprovalCallback implements ApprovalCallback {
    
    @Autowired
    private ProjectSettlementService settlementService;

    @Override
    @Transactional
    public void onApproved(Long businessId, String processInstanceId) {
        settlementService.onApproved(businessId);
    }

    @Override
    @Transactional
    public void onRejected(Long businessId, String processInstanceId) {
        settlementService.onRejected(businessId);
    }
}
```

---

## 9. Redis Key 规划

| Key 格式 | 数据类型 | TTL | 用途 |
|----------|---------|-----|------|
| `captcha:{uuid}` | String | 5min | 图形验证码存储 |
| `sms:{phone}` | String | 5min | 短信验证码存储 |
| `sms:freq:{phone}` | String | 60s | 短信发送频率限制 |
| `sms:daily:{phone}` | String | 当天剩余 | 每日短信计数 |
| `login:ip:fail:{ip}` | String | 5min | IP登录失败计数 |
| `login:ip:lock:{ip}` | String | 15min | IP锁定标识 |
| `retention:warned:{id}:{level}` | String | 永久 | 质保金预警去重 |
| `retention:overdue:last:{id}` | String | 永久 | 逾期催办最后发送日期 |

---

## 10. 正确性属性（Correctness Properties）

### P1: BOQ 层级一致性
- **属性**: 对于任意清单条目，其 parent_id 指向的父级条目必须存在且 level = 当前条目 level - 1
- **验证**: 任何 level > 1 的条目必须有合法父级引用

### P2: 预算变更金额守恒
- **属性**: 变更通过后，原预算明细 budget_total_price 的变化量之和 = 变更单 total_adjust_amount
- **验证**: SUM(变更明细.adjust_amount) = 变更单.total_adjust_amount

### P3: 结算利润计算正确性
- **属性**: profit = total_income - total_expenditure，且 profit_rate = profit / total_income * 100（total_income > 0 时）
- **验证**: 数值关系恒等

### P4: 验证码一次性使用
- **属性**: 验证码校验成功后立即从 Redis 删除，同一 uuid 不可二次校验成功
- **验证**: 连续两次使用相同 uuid+code 调用校验，第二次必定返回失败

### P5: 预算控制配置单调性
- **属性**: 删除项目级配置后，该项目的生效配置回落为系统默认规则（BLOCK, 80%）
- **验证**: 删除操作前后对比 getEffectiveConfig 返回值

### P6: 方案快照不可变性
- **属性**: 检查记录创建后，即使原方案在 basedata 中更新，检查记录的 scheme_snapshot 内容不变
- **验证**: 修改方案源数据后重新读取检查记录，快照 JSON 与创建时一致

### P7: 质保金通知去重
- **属性**: 同一质保金在同一预警级别阶段内仅产生一次通知发送
- **验证**: 对同一记录连续执行两次预警任务，第二次不应产生新的通知日志
