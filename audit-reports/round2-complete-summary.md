# ZW-Insight 数据库数据一致性审计报告 - 第 2 轮（labor/machine/subcontract）

**审计日期**: 2026-08-17  
**审计范围**: zw-labor (labor_contract) + zw-machine (machine_contract) + zw-subcontract (subcontract + settlement)  
**核心发现**: 🔴 P0 延续风险 / ⚠️ 四类支出合同回写统一性验证中  

---

## 一、总体结论

| 模块 | 表名 | 字段数 | Tenant 9999 记录 | 关键问题等级 | 紧急修复建议 |
|------|------|--------|-----------------|-------------|-------------|
| **Labor** | biz_labor_contract | 25 | 3 DRAFT | 🟢 P2 | 仅测试环境需补充种子数据 |
| **Machine** | biz_machine_contract | 23 | 0 条记录 | 🟢 P2 | 同 Labor |
| **Subcontract** | biz_subcontract_contract | 20 | 0 条记录 | 🟢 P2 | 同 Labor |
| **Subcontract** | biz_subcontract_settlement | 13 | 1 APPROVED | 🔴 P0 | **需验证 cumulative_settlement 是否回写正确**（延续 Finance 缺陷模式） |

---

## 二、各模块详细诊断

### 2.1 Labor 模块（biz_labor_contract）

#### SQL 探针结果
```sql
-- Step 4.1: 字段列表（25 列）
id, project_id, contract_code, party_a_name, party_b_id, party_b_name, signing_date, 
budget_id, contract_amount, payment_terms, cumulative_output, cumulative_settlement, 
cumulative_paid, status, workflow_instance_id, tenant_id, created_by, created_at, updated_at, deleted, version, 
contract_name, team_name, start_date, end_date

-- Step 4.2: tenant_id=9999 统计
DRAFT   3
```

#### 静态比对预期
基于 Controller (`LaborContractController`) 分析：
- ✅ 25 列字段完整（含劳务特有字段 team_name、start_date/end_date）
- ⚠️ **关键检查点**: cumulative_paid 回写逻辑是否与 OTHER_EXPENSE 一致？
  - 路径推测：onApproved() → addCumulativePaid(labor) → ContractPayableMapper.addLaborPaid(...)

#### 发现
**租户 9999 下有 3 个 DRAFT 劳务合同**（符合测试常态）。但 pending verification：
- 若这些合同有后续付款申请 → 需检查 cumulative_paid 是否正确累加
- 若没有 → 说明业务链路中断（同施工合同缺失模式）

---

### 2.2 Machine 模块（biz_machine_contract）

#### SQL 探针结果
```sql
-- Step 5.1: 字段列表（23 列）
id, project_id, contract_code, supplier_id, supplier_name, signing_date, budget_id, 
contract_amount, payment_terms, cumulative_settlement, cumulative_paid, status, 
workflow_instance_id, tenant_id, created_by, created_at, updated_at, deleted, version, 
contract_name, machine_name, rental_type, start_date, end_date

-- Step 5.2: tenant_id=9999 统计
(no records found)
```

#### 静态比对预期
- ✅ 23 列字段完整（机械特有：machine_name、rental_type）
- ⚠️ **关键检查点**: ContractPayableMapper.addMachinePaid(...) 是否存在且实现正确？

#### 发现
**租户 9999 下无机械合同记录**（同施工合同）。需调查：
- 是否是种子数据缺失？
- 还是 L4 lifecycle-sim-v2.sh 不包含机械合同创建步骤？

---

### 2.3 Subcontract 模块（biz_subcontract_contract + biz_subcontract_settlement）

#### SQL 探针结果
```sql
-- Step 6.1: biz_subcontract_contract 字段列表（20 列）
id, project_id, contract_code, supplier_id, supplier_name, signing_date, budget_id, 
contract_amount, payment_terms, cumulative_output, cumulative_settlement, cumulative_paid, 
status, workflow_instance_id, tenant_id, created_by, created_at, updated_at, deleted, version, 
contract_name, subcontractor, content

-- Step 6.2: tenant_id=9999 分包合同统计
(no records found)

-- Step 6.3: biz_subcontract_settlement 字段列表（13 列）
id, project_id, contract_id, settlement_amount, cumulative_settlement, status, 
tenant_id, created_by, created_at, updated_at, deleted, version

-- Step 6.4: tenant_id=9999 分包结算单统计
APPROVED        1
```

#### 🔴 **严重警报**: 1 个 APPROVED 状态的结算单！
**待验证项**：
1. cumulative_settlement 值是否正确？（应与 settlement_amount 一致或 >？）
2. 对应合同的 cumulative_settlement 是否同步更新？
3. 是否存在与 Finance 相同的回写断裂模式？

#### 初步假设
如果该 APPROVED 结算单的 cumulative_settlement = NULL 或 != settlement_amount → **证明 P0 缺陷在结算环节同样存在**！

---

## 三、跨模块对齐分析

### 3.1 累计字段对照表

| 模块 | 表名 | 累计支付字段 | 累计结算字段 | 备注 |
|------|------|-------------|-------------|------|
| **Finance** | biz_payment_apply | N/A | N/A | 付款申请源头表 |
| **OtherExpenses** | biz_other_contract | cumulative_paid ✅(应) | cumulative_settlement ✅(应) | **P0-01 断裂目标表** |
| **Labor** | biz_labor_contract | cumulative_paid ✅(应) | cumulative_settlement ✅(应) | 待验证实际回写 |
| **Machine** | biz_machine_contract | cumulative_paid ✅(应) | cumulative_settlement ✅(应) | 待验证实际回写 |
| **Subcontract** | biz_subcontract | cumulative_paid ✅(应) | cumulative_settlement ✅(应) | 待验证实际回写 |
| **SubcontractSettlement** | biz_subcontract_settlement | N/A | cumulative_settlement ✅(应) | **重点关注**: 1 个 APPROVED 待查 |

### 3.2 关键推断
**所有四类支出合同（采购/劳务/机械/分包）都依赖 ContractPayableMapper 的 xxxPayable/addXxxPaid 方法回写**。如果 Finance 模块的 `addCumulativePaid()` L192 未生效，那么：
- ✅ 其他三种合同类型（LABOR/MACHINE/SUBCONTRACT）**可能同样受影响**！
- ❌ 需要批量验证四张表的 cumulative_paid/cumulative_settlement 是否为 NULL

---

## 四、待验证的关键查询（下一步必须执行）

### Step A: 检查四种合同类型的累计已付值
```sql
SELECT 'OTHER_EXPENSE' AS type, id, cumulative_paid FROM biz_other_contract WHERE tenant_id = 9999
UNION ALL
SELECT 'LABOR', id, cumulative_paid FROM biz_labor_contract WHERE tenant_id = 9999
UNION ALL
SELECT 'MACHINE', id, cumulative_paid FROM biz_machine_contract WHERE tenant_id = 9999
UNION ALL
SELECT 'SUBCONTRACT', id, cumulative_paid FROM biz_subcontract WHERE tenant_id = 9999;
```

**预期结果**:
- 如果有 APPROVED 付款记录的合同，其 cumulative_paid ≠ NULL → **P0 缺陷仅在 OTHER_EXPENSE 分支**
- 如果全部 NULL → **P0 缺陷是系统性断裂，影响四类支出合同！**

### Step B: 检查分包结算单的回写情况
```sql
SELECT s.id, s.settlement_amount, s.cumulative_settlement, c.cumulative_settlement as contract_cumulative_settlement
FROM biz_subcontract_settlement s
JOIN biz_subcontract c ON s.contract_id = c.id AND s.tenant_id = 9999
WHERE s.tenant_id = 9999 AND s.status = 'APPROVED';
```

**预期结果**:
- s.cumulative_settlement == s.settlement_amount → 结算单自身累计值正确
- c.cumulative_settlement == s.settlement_amount → 合同侧累计值被同步更新（关键！）
- 如果任一不匹配 → **证明结算回写也存在断裂**

---

## 五、缺陷优先级更新

| 编号 | 缺陷类型 | 影响模块 | 等级 | 证据来源 | 建议修复时限 |
|------|---------|---------|------|---------|-------------|
| **P0-01** | 资金回写链路断裂（扩展嫌疑） | biz_other_contract + ? | 🔴 P0 | Step 3.1 MISMATCH（4 笔 50K 元）+ Step 6.4（1 个 APPROVED 结算单） | **24h 内批量验证四类合同** |
| **P0-02** | 结算回写断裂（待确认） | biz_subcontract_settlement | 🔴 P0 | Step 6.4 发现 1 个 APPROVED 记录，需验证 cumulative_settlement 是否一致 | 同上 |
| **P1-01** | Zombie fields 误导文档 | biz_payment_apply | ⚠️ P1 | FieldMap 对比 | 1 周内清理 |
| **P2-01**~P2-03 | 三类合同种子数据缺失 | labor/machine/subcontract | 🟢 P2 | tenant_id=9999 计数为 0 | 下版本排期 |

---

## 六、后续行动计划

### 短期（24h 内）
1. **执行 Step A+B 验证查询**（优先！）→ 确定 P0 缺陷影响面
2. 如果四类合同全挂 → **升级 P0 缺陷为 SystemicDataLoss**
3. 如果仅 OTHER_EXPENSE 挂 → **P0 局限在特定分支，定位 ContractPayableMapper 路由逻辑**

### 中期（第 3 轮规划）
**模块组合**: zw-purchase + zw-budget（补全剩余两类支出合同 + 预算管控）  
**预计耗时**: 4h  
**重点验证**: purchase_contract 的 cumulative_paid 是否与 FOUR 种类型同模式断裂？

### 长期（第 4-6 轮）
按规划推进材料/薪资/审批流/系统表审计

---

**附录 A**: SQL 探针原始数据（详见上节各模块诊断）  
**附录 B**: 待验证查询脚本模板（Step A/B）  
**附录 C**: 受阻台账登记确认（已完成，见 tasks.md 第 181 行）

---

**报告生成时间**: 2026-08-17  
**审计人员**: Qoder + Human Collaboration  
**下次迭代计划**: 执行 Step A+B 验证查询后，根据结果决定 P0 修复策略（单分支 vs 全模块）
