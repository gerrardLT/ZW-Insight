# Requirements Document

## Introduction

本文档定义 ZW-Insight 平台 8 项 P1 级别业务完善功能的需求。这些功能用于补齐系统完整度，覆盖结算明细、供应商拦截、人事统计、合同提醒、公开报价、退货退款和档案补全。

## Glossary

- **MachineWorkSettlement**: 机械工作量结算单，按项目汇总一个周期内的所有机械台班/工作量记录
- **SubcontractSettlementDetail**: 分包结算明细行，记录本次结算的工程量和金额
- **SupplierBlacklist**: 供应商黑名单，标记不良供应商并在合同创建时自动拦截
- **HrStatistics**: 人事花名册统计，按部门/岗位/工龄等维度分析人员分布
- **ContractExpiryTask**: 合同到期提醒定时任务，扫描即将到期的合同并发送通知
- **PublicInquiry**: 公开询价页面，允许供应商免登录查看询价并提交报价
- **MaterialRefund**: 材料退货退款，退货出库时关联生成退款申请单
- **ArchiveEndpoint**: 档案查询接口，聚合展示业务数据的只读视图

---

## Requirements

### Requirement 1: 机械工作量结算（按项目汇总）

**User Story:** 作为项目经理，我希望按项目和结算周期汇总所有机械工作量记录生成结算单，以便统一审批和付款。

#### Acceptance Criteria

1. THE System SHALL 提供创建机械工作量结算单的接口，包含项目ID、结算周期（开始日期/结束日期）
2. WHEN 创建结算单时，THE System SHALL 自动汇总该项目在指定周期内所有已确认的 MachineWorkLog 记录
3. THE 结算单 SHALL 包含明细列表，每条明细对应一条工作量记录（机械名称、台班数/工作量、单价、小计金额）
4. THE System SHALL 自动计算结算单总金额（所有明细小计之和）
5. WHEN 结算单提交审批时，THE System SHALL 发起 Flowable 审批流程
6. WHEN 结算单审批通过后，THE System SHALL 将关联的工作量记录状态更新为"已结算"
7. IF 某工作量记录已被其他结算单包含，THEN THE System SHALL 排除该记录并在明细中标注

---

### Requirement 2: 分包结算明细表

**User Story:** 作为项目经理，我希望分包结算单包含工程量明细，以便清晰了解本次结算的各项工程量和金额。

#### Acceptance Criteria

1. THE SubcontractSettlement SHALL 关联一张明细表（biz_subcontract_settlement_detail）
2. THE 明细行 SHALL 包含：工程项名称、计量单位、本次结算数量、单价、本次结算金额
3. WHEN 创建分包结算单时，THE System SHALL 允许用户添加多条明细行
4. THE System SHALL 自动计算结算单总金额为所有明细行金额之和
5. WHEN 用户修改明细行数量或单价时，THE System SHALL 自动重新计算该行金额和总金额
6. THE System SHALL 提供结算明细的导出功能（Excel）

---

### Requirement 3: 供应商黑名单自动拦截

**User Story:** 作为采购经理，我希望系统在创建合同时自动校验供应商是否在黑名单中，以便防止与不良供应商签约。

#### Acceptance Criteria

1. WHEN 用户在采购合同（PurchaseContract）中选择供应商时，THE System SHALL 调用 SupplierBlacklistService.isBlacklisted() 校验
2. WHEN 用户在分包合同（Subcontract）中选择供应商时，THE System SHALL 同样执行黑名单校验
3. IF 供应商在黑名单中，THEN THE System SHALL 拒绝保存合同并返回错误提示："该供应商已被列入黑名单（原因：{reason}），禁止签约"
4. THE System SHALL 在前端合同表单中实时提示供应商黑名单状态（选择供应商后立即查询）
5. WHEN 管理员将供应商移出黑名单后，THE System SHALL 允许后续合同正常创建

---

### Requirement 4: 人事花名册统计

**User Story:** 作为人事经理，我希望查看公司人员统计分析，以便了解人员结构和变动趋势。

#### Acceptance Criteria

1. THE System SHALL 提供人事统计接口，返回以下维度数据：
   - 按部门统计人数
   - 按岗位统计人数
   - 按工龄段统计人数（0-1年/1-3年/3-5年/5年以上）
   - 按月统计入离职趋势（近12个月）
2. THE System SHALL 提供当前在职总人数、本月入职人数、本月离职人数的汇总数据
3. THE System SHALL 在 PC 端提供人事统计页面，以图表形式展示上述数据
4. THE 统计数据 SHALL 实时计算（基于 sys_user 表和 hr_entry_apply/hr_resign_apply 表）

---

### Requirement 5: 合同到期提醒

**User Story:** 作为项目经理，我希望系统自动提醒即将到期的合同，以便及时安排续签或结算。

#### Acceptance Criteria

1. THE ContractExpiryTask SHALL 每日 09:00 执行一次合同到期扫描
2. WHEN 合同距到期日不足 30 天时，THE System SHALL 向合同负责人发送"即将到期"站内消息
3. WHEN 合同距到期日不足 7 天时，THE System SHALL 向合同负责人发送"紧急到期"站内消息
4. THE 到期提醒消息 SHALL 包含：合同编号、合同名称、供应商/分包商名称、到期日期、剩余天数
5. THE System SHALL 使用 Redis 去重，同一合同同一级别只提醒一次
6. THE 扫描范围 SHALL 覆盖：采购合同、分包合同、机械合同、劳务合同
7. IF 合同已终止或已结算完毕，THEN THE System SHALL 跳过该合同不发送提醒

---

### Requirement 6: 公开报价查询（免登录）

**User Story:** 作为供应商，我希望无需注册账号即可查看询价公告并提交报价，以便降低参与门槛。

#### Acceptance Criteria

1. THE 供应商门户 SHALL 提供公开询价列表页面（/public/inquiries），无需登录即可访问
2. THE 公开列表 SHALL 仅展示 inviteMode=PUBLIC 且状态为 OPEN 的询价公告
3. WHEN 供应商点击某询价查看详情时，THE System SHALL 展示询价信息（项目名称、材料清单、截止日期）
4. WHEN 供应商提交报价时，THE System SHALL 要求输入手机号并验证短信验证码
5. THE System SHALL 根据手机号关联或自动创建临时供应商账号
6. WHEN 验证码验证通过后，THE System SHALL 允许供应商填写并提交报价单
7. IF 询价已截止（超过 deadline），THEN THE System SHALL 拒绝提交报价

---

### Requirement 7: 退货退款关联

**User Story:** 作为材料管理员，我希望退货出库时自动生成退款申请，以便财务及时处理退款。

#### Acceptance Criteria

1. WHEN 创建退货出库单（outboundType=RETURN）并关联了采购合同时，THE System SHALL 自动生成一条退款申请记录（BizMaterialRefund）
2. THE 退款申请 SHALL 包含：关联出库单ID、关联采购合同ID、退货材料明细、退款金额（按入库单价计算）、退款原因
3. THE System SHALL 将退款申请提交 Flowable 审批流程
4. WHEN 退款审批通过后，THE System SHALL 更新采购合同的已付款金额（扣减退款金额）
5. THE System SHALL 提供退款记录查询接口，支持按合同ID/时间范围筛选
6. IF 退货出库单未关联采购合同，THEN THE System SHALL 不生成退款申请（仅做库存扣减）

---

### Requirement 8: 档案补全（其它合同 + 办公用品）

**User Story:** 作为行政人员，我希望在档案模块查看其它收入合同、其它支出合同和办公用品的汇总信息。

#### Acceptance Criteria

1. THE ArchiveController SHALL 提供 `GET /v1/archive/other-income-contract` 接口，返回其它收入合同档案列表
2. THE ArchiveController SHALL 提供 `GET /v1/archive/other-expense-contract` 接口，返回其它支出合同档案列表
3. THE ArchiveController SHALL 提供 `GET /v1/archive/office-supply` 接口，返回办公用品档案列表
4. THE 其它合同档案 SHALL 包含：合同编号、合同名称、金额、签约日期、状态、关联项目
5. THE 办公用品档案 SHALL 包含：用品名称、当前库存、累计入库量、累计领用量、最近入库日期
6. THE 各接口 SHALL 支持分页查询和关键字搜索
