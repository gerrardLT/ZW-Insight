# ZW-Insight 数据库数据一致性审计报告 - 第 7 轮

**审计日期**: 2026-09-18（UTC 2026-09-18T03-10-11Z）
**审计范围**: 全库（biz_* / sys_* / bd_* / ACT_* / wf_* / flyway）
**审计口径**: 租户 1（演示）+ 租户 9999（测试）+ 全局系统表，仅只读查询
**执行脚本**: keys/audit-data-round7.sh
**基线**: audit-reports/round1-6-complete-summary.md（2026-08-17）

---


## Section 0：Preflight（连通性与元数据）

| 结果 | 检查项 | 实际值 | 期望/说明 |
|------|--------|--------|----------|
| ✅ PASS | 数据库名 | `zw_insight` | 期望 zw_insight |
| ℹ️ INFO | MySQL 版本 | `8.0.37` |  |
| ℹ️ INFO | lower_case_table_names | `` | （0=大小写敏感） |
| ℹ️ INFO | 数据库默认字符集 | `utf8mb4` |  |
| ℹ️ INFO | 总表数 | `219` | （round6 基线 213） |
| ℹ️ INFO | 表分布 | `biz=121 sys=26 bd=6 ACT=39 wf=7 other=20` |  |
| ℹ️ INFO | 审计时刻(UTC) | `2026-09-18T03:10:15Z` |  |


## Section 1：已知问题回归（round1-6 基线对比）

| 结果 | 检查项 | 实际值 | 期望/说明 |
|------|--------|--------|----------|
| ℹ️ INFO | R5-01 租户1 ACT_RU_TASK | `156` | （round5 基线 312） |
| ✅ 改善 | R5-01 改善 | 当前 156 < 基线 312 | 残留已部分清理 |
| ℹ️ INFO | R5-02 ACT_RE_DEPLOYMENT | `1050` | （round5 基线 1023） |
| ℹ️ INFO | R6-01 近30天备份 | `总=30 成功=30 成功率=100%` |  |
| ✅ PASS | R6-01 备份成功率 | 100% | ≥95% |
| ℹ️ INFO | R6-02 日志三表 | `audit=0 login=2779 oper=2591` | （round6 基线全 0，功能未接线） |
| ℹ️ INFO | R6-03 sys_user_project | `总=8 项目孤儿=0 用户孤儿=0` | （round6 基线 1287） |
| ℹ️ INFO | R6-04 sys_login_device | `5692` | （round6 基线 17260） |
| ✅ PASS | Flyway 失败迁移 | `0` | 期望 eq `0` |
| ℹ️ INFO | Flyway 总记录 | `38` |  |


## Section 2：新增表覆盖（cost-control-backbone + p0-gap-closeout）

| 结果 | 检查项 | 实际值 | 期望/说明 |
|------|--------|--------|----------|

### 2.1 cost-control-backbone

| ℹ️ INFO | biz_project_wbs_node 行数 | `0` |  |
| ✅ PASS | WBS parent_id 孤儿 | `0` | 期望 eq `0` |
| ✅ PASS | WBS project_id 孤儿 | `0` | 期望 eq `0` |
| ✅ PASS | WBS node_code 租户内重复 | `0` | 期望 eq `0` |
| ℹ️ INFO | biz_cost_account 行数 | `0` |  |
| ✅ PASS | CBS parent_id 孤儿 | `0` | 期望 eq `0` |
| ✅ PASS | CBS wbs_node_id 孤儿 | `0` | 期望 eq `0` |
| ✅ PASS | CBS account_code 租户内重复 | `0` | 期望 eq `0` |
| ✅ PASS | CBS 金额维度负值 | `0` | 期望 eq `0` |
| ℹ️ INFO | biz_cost_account_txn 行数 | `0` |  |
| ✅ PASS | TXN account_id 孤儿 | `0` | 期望 eq `0` |
| ✅ PASS | TXN 幂等键重复 | `0` | 期望 eq `0` |
| ℹ️ INFO | biz_cost_account_link 行数 | `0` |  |
| ✅ PASS | LINK account_id 孤儿 | `0` | 期望 eq `0` |
| ℹ️ INFO | biz_change_event 行数 | `0` |  |
| ✅ PASS | ChangeEvent event_number 重复 | `0` | 期望 eq `0` |
| ℹ️ INFO | sys_outbox_event 行数 | `0` |  |
| ✅ PASS | Outbox 卡死(attempts≥max 非 DEAD) | `0` | 期望 eq `0` |
| ✅ PASS | Outbox idempotency_key 重复 | `0` | 期望 eq `0` |

### 2.2 p0-gap-closeout

| ℹ️ INFO | biz_payment_received 总数 | `7` |  |
| ℹ️ INFO | claim_status 分布 | `UNCLAIMED=7 CLAIMED=0 WRITTEN_OFF=0` |  |
| ✅ PASS | 认领状态非 UNCLAIMED 但缺 claimed_by/at | `0` | 期望 eq `0` |
| ℹ️ INFO | biz_rectification 总数 | `1` |  |
| ℹ️ INFO | attachment_ids NULL 率 | `100%` | （1 / 1） |
| ℹ️ INFO | bd_material 总数 | `17` |  |
| ℹ️ INFO | material_code NULL 率 | `100%` | （17 / 17） |
| ✅ PASS | material_code 租户内重复 | `0` | 期望 eq `0` |

### 2.3 p2-advanced

| ℹ️ INFO | sys_backup_restore_log 行数 | `0` |  |
| ℹ️ INFO | sys_version 最新版本 | `（空）` |  |


## Section 3：跨模块金额勾稽

| 结果 | 检查项 | 实际值 | 期望/说明 |
|------|--------|--------|----------|

### 3.1 支出侧四类合同 cumulative_paid 勾稽

| ✅ PASS | `biz_labor_contract.cumulative_paid` vs `biz_payment_apply.SUM(payment_amount) WHERE status='APPROVED'` | 0/4 MISMATCH | 全部勾稽 |
| ✅ PASS | `biz_machine_contract.cumulative_paid` vs `biz_payment_apply.SUM(payment_amount) WHERE status='APPROVED'` | 0/3 MISMATCH | 全部勾稽 |
| ✅ PASS | `biz_subcontract.cumulative_paid` vs `biz_payment_apply.SUM(payment_amount) WHERE status='APPROVED'` | 0/4 MISMATCH | 全部勾稽 |
| ✅ PASS | `biz_purchase_contract.cumulative_paid` vs `biz_payment_apply.SUM(payment_amount) WHERE status='APPROVED'` | 0/4 MISMATCH | 全部勾稽 |

### 3.2 支出侧四类合同 cumulative_settlement 勾稽

| ✅ PASS | `biz_labor_contract.cumulative_settlement` vs `biz_labor_settlement.SUM(settlement_amount) WHERE status='APPROVED'` | 0/4 MISMATCH | 全部勾稽 |
| ✅ PASS | `biz_subcontract.cumulative_settlement` vs `biz_subcontract_settlement.SUM(settlement_amount) WHERE status='APPROVED'` | 0/4 MISMATCH | 全部勾稽 |
| ✅ PASS | `biz_purchase_contract.cumulative_settlement` vs `biz_purchase_settlement.SUM(settlement_amount) WHERE status='APPROVED'` | 0/4 MISMATCH | 全部勾稽 |
| ✅ PASS | `biz_machine_contract.cumulative_settlement` vs `biz_machine_settlement.SUM(settlement_amount) WHERE status='APPROVED'` | 0/3 MISMATCH | 全部勾稽 |
| ℹ️ INFO | biz_machine_work_settlement(已审批) | `笔数=1 金额=260000.00` | （无 contract_id，不计入合同勾稽） |

### 3.3 项目侧汇总勾稽

| ✅ PASS | `biz_project.total_expense` vs APPROVED 付款汇总 | 0/4 MISMATCH | 全部勾稽 |
| ✅ PASS | `biz_project.total_income` vs 收款汇总 | 0/4 MISMATCH | 全部勾稽 |
| ✅ PASS | `biz_project.cumulative_output` vs APPROVED 产值汇总 | 0/4 MISMATCH | 全部勾稽 |

### 3.4 收入侧勾稽（施工合同）

| ✅ PASS | `biz_construction_contract.cumulative_invoice_amount` vs `biz_invoice_apply.SUM(invoice_amount) WHERE status='APPROVED'` | 0/4 MISMATCH | 全部勾稽 |
| ✅ PASS | `biz_construction_contract.cumulative_received_amount` vs `biz_payment_received.SUM(receive_amount) WHERE status='APPROVED'` | 0/4 MISMATCH | 全部勾稽 |

### 3.5 材料库存不变量

| ℹ️ INFO | biz_project_material_stock 总数 | `4` |  |
| ✅ PASS | 库存不变量违例 | `0` | 期望 eq `0` |
| ✅ PASS | 库存负值 | `0` | 期望 eq `0` |

### 3.6 CBS 成本账户勾稽

| ℹ️ SKIP | CBS actual/commitment 勾稽 | 账户数=0 | 无数据可校 |


## Section 4：数据卫生

| 结果 | 检查项 | 实际值 | 期望/说明 |
|------|--------|--------|----------|
| ℹ️ INFO | E2E_TEST_ 项目残留(租户1) | `0` |  |
| ℹ️ INFO | E2E_TEST_ 询价残留(租户1) | `0` |  |
| ℹ️ INFO | 时间戳后缀项目残留(租户1) | `0` | （round5 观察 89 条） |
| ℹ️ INFO | B4/B3/B2/API测试 项目残留 | `0` |  |

### 4.4 租户 9999 业务表残留（期望 0）

| ℹ️ INFO | 租户9999 biz_ 关键表残留合计 | `0` | （期望 0，参照 verify-l4-clean.sh） |

### 4.5 演示种子完整性（ID 90001-99999）

| ✅ PASS | 种子项目数 | `4` | 期望 eq `4` |
| ℹ️ INFO | 种子施工合同数 | `4` | （期望 ≥3） |
| ℹ️ INFO | 种子付款申请数 | `16` | （期望 ≥3） |

### 4.6 业务编号租户内重复

| ✅ PASS | biz_project.project_code 重复 | `0` | 期望 eq `0` |
| ✅ PASS | biz_labor_contract.contract_code 重复 | `0` | 期望 eq `0` |
| ✅ PASS | biz_purchase_contract.contract_code 重复 | `0` | 期望 eq `0` |
| ✅ PASS | biz_subcontract.contract_code 重复 | `0` | 期望 eq `0` |
| ✅ PASS | biz_machine_contract.contract_code 重复 | `0` | 期望 eq `0` |

### 4.7 NULL 必填字段

| ✅ PASS | biz_project.project_code NULL/空 | `0` | 期望 eq `0` |
| ✅ PASS | biz_project.tenant_id NULL | `0` | 期望 eq `0` |
| ✅ PASS | biz_payment_apply.tenant_id NULL | `0` | 期望 eq `0` |

### 4.8 逻辑删除孤儿

| ✅ PASS | 施工合同引用已删除项目 | `0` | 期望 eq `0` |
| ✅ PASS | 付款申请引用已删除项目 | `0` | 期望 eq `0` |


## Section 5：业务规则违例

| 结果 | 检查项 | 实际值 | 期望/说明 |
|------|--------|--------|----------|
| ✅ PASS | CLOSED 项目有新付款申请 | `0` | 期望 eq `0` |

### 5.2 超付检查（5% 容差）

| ✅ PASS | 劳务合同超付 | `0` | 期望 eq `0` |
| ✅ PASS | 机械合同超付 | `0` | 期望 eq `0` |
| ✅ PASS | 分包合同超付 | `0` | 期望 eq `0` |
| ✅ PASS | 采购合同超付 | `0` | 期望 eq `0` |

### 5.3 超结算检查（5% 容差）

| ✅ PASS | 劳务合同超结算 | `0` | 期望 eq `0` |
| ✅ PASS | 分包合同超结算 | `0` | 期望 eq `0` |
| ✅ PASS | 采购合同超结算 | `0` | 期望 eq `0` |

### 5.4 付款申请引用不存在的合同

| ✅ PASS | 付款申请引用不存在合同 | `0` | 期望 eq `0` |
| ✅ PASS | 付款申请引用不存在项目 | `0` | 期望 eq `0` |

### 5.6 状态倒挂

| ✅ PASS | DRAFT 劳务合同已有付款 | `0` | 期望 eq `0` |
| ✅ PASS | DRAFT 采购合同已有付款 | `0` | 期望 eq `0` |


## Section 6：租户隔离与权限完整性

| 结果 | 检查项 | 实际值 | 期望/说明 |
|------|--------|--------|----------|
| ✅ PASS | 付款申请跨租户污染 | `0` | 期望 eq `0` |
| ℹ️ INFO | sys_user 租户分布 | `t1=8 t9999=14 other=0` |  |
| ✅ PASS | sys_role_menu → sys_role 孤儿 | `0` | 期望 eq `0` |
| ✅ PASS | sys_user_role → sys_user 孤儿 | `0` | 期望 eq `0` |
| ✅ PASS | sys_dict_item → sys_dict 孤儿 | `0` | 期望 eq `0` |
| ✅ PASS | sys_org 父节点孤儿 | `0` | 期望 eq `0` |
| ✅ PASS | serial_number_rule 租户内重复 | `0` | 期望 eq `0` |
| ✅ PASS | sys_menu 父节点孤儿 | `0` | 期望 eq `0` |


## Section 7：容量与索引健康

| 结果 | 检查项 | 实际值 | 期望/说明 |
|------|--------|--------|----------|

### 7.1 Top 10 大表（data_length + index_length）

```
ACT_GE_BYTEARRAY	135.06	131520
ACT_HI_VARINST	12.61	7930
ACT_HI_ACTINST	7.98	8020
sys_login_device	6.34	5601
ACT_HI_IDENTITYLINK	4.00	4965
ACT_HI_TASKINST	1.95	1902
sys_oper_log	1.72	2476
ACT_HI_PROCINST	0.89	1228
ACT_RU_VARIABLE	0.78	836
ACT_RU_ACTINST	0.64	487
```

### 7.2 行数 Top 10

```
ACT_GE_BYTEARRAY	131520
ACT_HI_ACTINST	8020
ACT_HI_VARINST	7930
sys_login_device	5601
ACT_HI_IDENTITYLINK	4965
sys_login_log	2777
sys_oper_log	2476
wf_approval_record	2185
ACT_HI_TASKINST	1902
ACT_HI_COMMENT	1581
```
| ℹ️ INFO | 碎片率>30% 的表数 | `8` | （仅观察，不触发 OPTIMIZE） |

### 7.4 缺失索引的 FK 列（抽查）

| ℹ️ INFO | biz_ 表 FK 列缺索引数 | `55` | （project_id/tenant_id/contract_id） |
| ✅ PASS | 自增 ID 逼近 BIGINT 上限 | `0` | 期望 eq `0` |


---

## 执行摘要

| 指标 | 计数 |
|------|------|
| ✅ PASS | 65 |
| ❌ FAIL | 0 |
| ⚠️ WARN | 0 |
| ℹ️ INFO | 40 |

**总体结论**：

🟢 **数据健康**：全部检查项通过，无 FAIL / WARN。

---

**报告生成时间**: 2026-09-18T03:11:03Z
**审计人员**: Qoder + Human Collaboration
**脚本版本**: keys/audit-data-round7.sh (Round 7)
**下轮计划**: 第 8 轮（视本轮发现决定）
