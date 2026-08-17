# ZW-Insight 数据库数据一致性审计报告 - 第 4 轮（zw-budget + zw-hr）

**审计日期**: 2026-08-17  
**审计范围**: zw-budget（预算/明细/变更/管控配置）+ zw-hr（工资单/薪资统计）  
**审计口径**: 租户 1 + 9999，仅只读查询  
**总体结论**: 🟡 **勾稽机制验证通过**；发现 1 个种子数据超预算问题（P1）+ 1 个预算变更版本语义待确认（P2）

---

## 一、A 部分：zw-budget 模块审计

### A1. 数据分布
| 表 | 租户 | 状态 | 数量 |
|---|------|------|------|
| biz_budget | 1 | APPROVED | 72 |
| biz_budget | 1 | DRAFT | 63 |
| biz_budget_detail | 1 | - | 10 |
| biz_budget_change | 1 | APPROVED | 1 |
| sys_budget_control_config | 1 | - | 4 |

### A2. 预算明细计算勾稽（total = quantity × unit_price）
✅ **10 条明细全部 MATCH**（含材料 2800×4000=1120万 等精确计算验证）

### A3. 预算总额 vs 项目 budget_amount
- ✅ 92001（ORIGINAL 原始预算 4500万）与项目 90001 budget_amount=4500万 **MATCH**
- ⚠️ 92002（CHANGE 变更预算 4650万）与项目预算额不一致 → **定性为预算版本语义**：
  - 种子 SQL L291-292 实证：92001=ORIGINAL(change_seq=0)，92002=CHANGE(change_seq=1)
  - 变更预算审批通过后未回写项目 budget_amount（与待决策清单 #1 "预算变更 BPMN 缺失"相关）
  - **非数据错误，是功能缺口**（预算变更链路不完整）

### A4. 预算占用勾稽（合同金额 vs 科目额度）
🔴 **1 处 OVER_BUDGET（R4-01）**：
- SUBCONTRACT 科目额度：700万（明细 500万+200万）
- 实际占用：**800万**（合同 91801=500万 EFFECTIVE + 91802=300万 EFFECTIVE）
- **超支 100万**

**定性**：种子数据自身超预算。种子创建于 2026-07-23（早于预算管控切面完善），分包合同 91801/91802 直接硬编码创建，未经过 @BudgetCheck 校验。真实业务的预算管控有效性已由 22-budget-control.spec.ts（BLOCK/WARN_ONLY/EXEMPT 端到端）确证，此为种子叙事问题。

其他科目：LABOR 1200万额度/占用 1000万 OK，MACHINE 600万/500万 OK，MATERIAL 1800万/0 OK，INDIRECT 200万/0 OK。

---

## 二、B 部分：zw-hr 模块审计

### B0. 重要架构发现
⚠️ **biz_hr_statistics 表不存在**——薪资统计为**动态查询设计**（SalaryStatisticsService 实时聚合 biz_labor_payroll），无物化统计表。原计划的"统计表 vs 工资单勾稽"检查项**不适用**（无数据漂移风险面，因为无冗余存储）。

### B1. biz_labor_payroll 实际结构（审计修正）
实际字段为**结算口径**：`total_settlement / total_paid / unpaid / period_start / period_end / order_type`（非个人工资明细口径）。

### B2. 工资单勾稽（unpaid = total_settlement - total_paid）
✅ **全部 MATCH**（含种子 96031：结算 85000 - 已付 70000 = 未付 15000 精确匹配；77 条测试工资单全 0 自洽）

### B3. 负值/状态分布
| 租户 | 状态 | 数量 | 结算合计 |
|------|------|------|---------|
| 1 | SETTLED | 1 | 85,000 |
| 1 | APPROVED | 73 | 0 |
| 1 | DRAFT | 3 | 0 |

✅ **零负值结算**（negative_settlement=0）  
⚠️ 73 条 APPROVED 工资单结算额全为 0（E2E 测试残留，提交后未填明细——观察项，无资金风险）

---

## 三、缺陷清单汇总

| 编号 | 缺陷 | 等级 | 定性 | 处置建议 |
|------|------|------|------|---------|
| **R4-01** | 种子预算 SUBCONTRACT 科目超支 100万（额度 700万/占用 800万） | P1 | 种子数据叙事不自洽（早于预算管控切面创建） | 修正种子 SQL：调整 91802 金额为 200万 或提高 SUBCONTRACT 明细额度 |
| **R4-02** | 预算变更（92002 CHANGE 4650万）未回写项目 budget_amount（4500万） | P2 | 功能缺口（与待决策清单 #1 预算变更 BPMN 缺失相关） | 待产品决策预算变更链路语义后处置 |
| **R4-03** | 73 条 APPROVED 工资单结算额为 0（测试残留） | P3 | E2E 测试残留（提交即审批但未填明细） | 观察；如需清理需用户决策（租户 1） |

---

## 四、验收结论

### ✅ 通过项
1. **预算明细计算勾稽**：10 条全部 MATCH（quantity × unit_price = total）
2. **原始预算与项目预算额一致**：92001 = 项目 90001 budget_amount
3. **工资单 unpaid 勾稽**：全部满足 unpaid = settlement - paid
4. **薪资零负值**：无异常结算
5. **架构合理性**：薪资统计无物化表（动态查询），消除了数据漂移风险面

### ⚠️ 观察项
1. R4-01 种子超预算（P1，修正种子 SQL 建议）
2. R4-02 预算变更版本语义（P2，关联待决策 #1）
3. R4-03 零额工资单残留（P3）

---

**报告生成时间**: 2026-08-17  
**审计人员**: Qoder + Human Collaboration  
**下轮计划**: 第 5 轮 zw-workflow + zw-security（审批流数据一致性 + 权限配置对齐）
