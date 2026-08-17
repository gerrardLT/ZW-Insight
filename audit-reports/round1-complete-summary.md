# ZW-Insight 数据库数据一致性审计报告 - 第 1 轮（finance/contract/project）

**审计日期**: 2026-08-17  
**审计范围**: zw-finance (payment_apply) + zw-contract (construction_contract) + zw-project (project)  
**核心发现**: 🔴 P0 资金回写断裂 / ⚠️ P1 字段设计冗余  

---

## 一、总体结论

| 模块 | 表名 | 字段数 | Tenant 9999 记录 | 关键问题等级 | 紧急修复建议 |
|------|------|--------|-----------------|-------------|-------------|
| **Finance** | biz_payment_apply | 17 | 250 DRAFT / 4 APPROVED | 🔴 **P0** | 审批回调未触发导致 cumulative_paid=NULL |
| **Contract** | biz_construction_contract | 26 | 0 条记录 | 🟢 P2 | 仅测试环境需补充种子数据 |
| **Project** | biz_project | 28 | 3 DRAFT | 🟡 P3 | 正常业务流程中无异常 |

---

## 二、各模块详细诊断

### 2.1 Finance 模块（biz_payment_apply）

#### SQL 探针结果
```sql
-- Step 1.1: 字段列表
id, project_id, contract_id, contract_category, supplier_id, supplier_name, 
payment_amount, payment_date, cumulative_settlement_snapshot, unpaid_amount_snapshot, 
status, workflow_instance_id, tenant_id, created_by, created_at, updated_at, deleted, version

-- Step 1.2: tenant_id=9999 统计
DRAFT   250
APPROVED        4
```

#### 静态比对结论
✅ API 载荷与 DB 列完全匹配（PaymentApplyController L39 @RequestBody BizPaymentApply）  
❌ 发现 **2 个 zombie fields**：`cumulative_settlement_snapshot` / `unpaid_amount_snapshot`（从未读写）

#### 动态测试结论
🔴 **严重缺陷**：4 笔 APPROVED 付款总额 50,000 元 → 对应合同表 cumulative_paid = NULL  
**根因假设**: Flowable 事件监听器未触发 onApproved() 回调路径的 addCumulativePaid() 方法

#### 详细报告
→ 参见 [`audit-reports/step1-payment-apply-final-report.md`](./step1-payment-apply-final-report.md)（含完整诊断逻辑与行动建议）

---

### 2.2 Contract 模块（biz_construction_contract）

#### SQL 探针结果
```sql
-- Step 2.1: 字段列表（26 列）
id, project_id, project_name, contract_code, contract_type, parent_contract_id, 
party_a_name, party_a_id, signing_date, start_date, end_date, contract_amount, 
tax_rate, amount_without_tax, tax_amount, cumulative_change_amount, cumulative_output, 
cumulative_invoice_amount, cumulative_received_amount, status, workflow_instance_id, 
tenant_id, created_by, created_at, updated_at, deleted, version

-- Step 2.2: tenant_id=9999 统计
(no records found)
```

#### 静态比对预期
基于 Controller (`ConstructionContractController`) 分析：
- ✅ 预计字段映射良好（26 列覆盖完整 CRUD）
- ⚠️ 需要验证 cumulative_output/cumulative_settlement 等累计字段的实际回写逻辑

#### 发现
**租户 9999 下无施工合同记录**（对比 finance 模块有 250+ 付款申请，说明：
- 要么是种子数据缺失
- 要么是业务链路中施工合同创建后被删除/归档
- 需进一步调查 L4 lifecycle-sim-v2.sh 是否包含施工合同创建步骤）

---

### 2.3 Project 模块（biz_project）

#### SQL 探针结果
```sql
-- Step 3.1: 字段列表（28 列）
id, project_code, project_name, project_nature, project_type, owner_company_id, 
owner_company_name, signing_company_id, signing_company_name, project_overview, 
project_address, contact_name, contact_phone, need_tender, status, workflow_instance_id, 
budget_amount, contract_amount, cumulative_output, settlement_amount, total_income, 
total_expense, total_other_payment, tenant_id, created_by, created_at, updated_at, deleted, version

-- Step 3.2: tenant_id=9999 统计
DRAFT   3
```

#### 静态比对预期
基于 Controller (`ProjectController`) 分析：
- ✅ 28 列字段完整（含预算金额 budget_amount 与支出统计 total_expense/total_other_payment）
- ⚠️ total_expense 的累积回写依赖 payment_apply.onApproved() → **同步受 P0 缺陷影响**

#### 发现
**租户 9999 下有 3 个 DRAFT 状态项目**（符合测试数据常态）。但需注意：
- total_expense 字段值可能为 0（因为所有付款申请的回写失败）
- 预算管控逻辑依赖 budget_amount - contract_amount，如果合同创建时未正确写入 project_id，将导致预算校验失效

---

## 三、缺陷优先级汇总

| 编号 | 缺陷类型 | 影响模块 | 等级 | 证据来源 | 建议修复时限 |
|------|---------|---------|------|---------|-------------|
| **P0-01** | 资金回写链路断裂 | biz_payment_apply → biz_other_contract | 🔴 P0 | Step 3.1 MISMATCH 检测（4 笔 50K 元累计失败） | **24h 人工验证 + 72h 修复** |
| **P1-01** | Zombie fields 误导文档 | biz_payment_apply | ⚠️ P1 | FieldMap 对比（cumulative_settlement_snapshot/unpaid_amount_snapshot 从未使用） | 1 周内清理或补注释 |
| **P2-01** | 施工合同种子数据缺失 | biz_construction_contract | 🟢 P2 | tenant_id=9999 计数为 0 | 下版本排期（不影响现有业务） |
| **P3-01** | 项目 total_expense 统计失真 | biz_project | 🟡 P3 | 依赖于 P0-01 修复（累计回写失败导致 0 值） | P0 修复后自动恢复 |

---

## 四、后续行动计划

### 短期（1 周内）
1. **P0 紧急调查**（优先级最高）
   - [ ] SSH 登录服务器执行 Flowable 表查询（act_ru_task/act_hi_procinst）
   - [ ] 启用 PaymentApplyService 日志 DEBUG 级别观察 onApproved() 调用堆栈
   - [ ] 构造本地单元测试复现资金回写流程

2. **P1 字段清理**
   - [ ] 在 BizPaymentApply.java 补充 @Deprecated 注解
   - [ ] 评估是否彻底删除这两个字段（需 Flyway 迁移脚本）

3. **受阻台账登记**
   - [ ] 在 `.kiro/specs/test-maturity-upgrade/tasks.md` 追加条目：
     ```
     | 2026-08-17 | L5 | 资金回写链路断裂（P0-01） | DEPENDENCY | onApproved() 回调未被触发导致 cumulative_paid 全为 NULL | 财务月结风险、预算管控失效 | 待服务器端日志排查 + 单元测试定位根因 | AI | 待人工介入 |
     ```

### 中期（第 2 轮规划）
**模块组合**: zw-labor + zw-machine + zw-subcontract（四类支出合同结算回写统一性验证）  
**预计耗时**: 5h  
**重点验证**: labor/machine/subcontract 三种合同类型的 cumulative_paid 回写是否与 OTHER_EXPENSE 存在同样缺陷

### 长期（第 3-6 轮）
按规划顺序推进剩余 4 轮采购/材料/预算/系统表审计

---

## 五、附录 A：SQL 探针原始数据

### Finance 模块
```sql
id      bigint  NO
project_id      bigint  NO
...
cumulative_settlement_snapshot  decimal YES  ← ZOMBIE FIELD
unpaid_amount_snapshot  decimal YES  ← ZOMBIE FIELD
...
```

### Contract 模块
```sql
id      bigint  NO
project_id      bigint  NO
contract_code   varchar NO
...
cumulative_output       decimal YES
...
(status: 0 records in tenant_id=9999)
```

### Project 模块
```sql
id      bigint  NO
project_code    varchar NO
...
total_expense   decimal YES
...
(DRAFT: 3 records)
```

---

**报告生成时间**: 2026-08-17  
**审计人员**: Qoder + Human Collaboration  
**下次迭代计划**: 第 2 轮启动前确认 P0 缺陷修复方案（A/B/C 三套方案决策会）
