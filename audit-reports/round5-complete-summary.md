# ZW-Insight 数据库数据一致性审计报告 - 第 5 轮（zw-workflow + zw-security）

**审计日期**: 2026-08-17  
**审计范围**: zw-workflow（Flowable act_* / wf_* 表）+ zw-security/system（sys_user/role/menu/tenant 权限配置）  
**审计口径**: 租户 1 + 9999，仅只读查询  
**总体结论**: 🔴 **发现租户 1 测试残留重大积压（312 个运行中审批任务）**；权限配置完整性全部通过

---

## 零、过程事故记录（审计自身）

- 首版脚本用小写 `act_*` 查表全部报 `Table doesn't exist`，一度误判"Flowable 表再次丢失"
- 取证结论：MySQL `lower_case_table_names=0`（大小写敏感），表名为大写 `ACT_*`，**表一直完好**，纯脚本错误
- 同步修正字段假设：本库时间字段为 `created_at`（非 create_time）、wf_process_def 无 business_type 列、sys_tenant 无 deleted 列

## 一、A 部分：zw-workflow 审计

### A1. ACT_RU_TASK 运行中任务分布（全部租户 1，共 312）
| 流程 | 数量 |
|------|------|
| construction_contract_approval | 145 |
| purchase_contract_approval | 97 |
| material_transfer_approval | 58 |
| payment_apply_approval | 8 |
| purchase_settlement_approval | 3 |
| project_close_approval | 1 |

运行时总量：ru_task=312 / ru_var=1562 / ru_exec=624 / hi_procinst=2246。租户 9999 待办 = **0**（测试清理纪律对 9999 有效）。

### A2. 残留定性（逐项实证）
| 组 | 证据 | 定性 |
|----|------|------|
| 调拨 58 | from_project 全部为 `E2E_TEST_*_MAT`；workflow_instance_id 全部有值；与 58 个 material_transfer_approval 任务一一对应 | L4/E2E 测试残留（08-11~08-16 创建） |
| 施工合同 145 | 合同全 SUBMITTED；关联项目 56 个 `E2E_TEST_*` + 89 个乱码中文测试名（`*_1785378425062` 时间戳后缀）；created_by=1(admin) | L5 UI 测试残留（withdraw 未回收） |
| 采购合同 97 | business_key 全 PURCHASE_CONTRACT 前缀；创建日期全为测试日 | 测试残留 |
| 付款 8 | 全部 SUBMITTED / 65000.00 固定额 / 08-14~08-16 | L5 测试残留 |
| 结算 3 + 结案 1 | 08-11 结算单 / 07-30 结案项目 CLOSING | 测试残留 |

任务创建日期分布（07-30 起，08-11 峰值 86，08-13 为 62）与 CI/L5 测试日历完全吻合。

### A3. 保护核查 ✅
- 种子项目 90001/90002/90003 名下运行中流程 = **0**（演示数据零污染）
- wf_approval_record 2263 条孤儿 = **0**（审批记录与流程实例完全一致）
- wf_process_def 682 条与 ACT_RE_PROCDEF 无缺失映射

### A4. ⚠️ R5-02 部署去重疑似退化（P3 观察项）
- ACT_RE_DEPLOYMENT = **1023**（08-13 事故清理时为 992，此后 +31）
- 流程定义版本最高 78（machine_settlement），说明每次部署仍生成新版本
- 08-14 修复声称 enableDuplicateFiltering 生效，但部署计数持续增长 → 疑似过滤仅对 Spring 自动部署生效，deploy-bpmn.sh API 路径未覆盖（待代码排查确认）

## 二、B 部分：zw-security/system 权限配置审计

| 检查项 | 结果 |
|--------|------|
| sys_role_menu → sys_role 孤儿 | ✅ 0 |
| sys_user_role → sys_user 孤儿 | ✅ 0 |
| sys_role_menu → sys_menu 孤儿 | ✅ 0 |
| sys_menu 父节点孤儿 | ✅ 0 |
| sys_tenant 状态 | ✅ DEFAULT/T9999 均启用，到期 2099-12-31 |
| sys_user 分布 | 租户 1：132（123 已删除，测试账号流转正常）；租户 9999：45（0 删除） |

**权限配置完整性全部通过**。

## 三、缺陷清单

| 编号 | 缺陷 | 等级 | 定性 | 处置建议 |
|------|------|------|------|---------|
| **R5-01** | 租户 1 积压 312 个运行中审批任务 + 对应 SUBMITTED 业务单据（施工合同 145/采购合同 97/调拨 58/付款 8/结算 3/结案 1），全部为 L4/L5/E2E 测试残留 | **P1** | 测试 cleaner 未覆盖租户 1 的 UI 测试数据（仅 9999 自清理）；08-13 事故后 withdraw 回收对 UI 测试路径仍有漏网 | 需用户决策：A. 级联清理（terminate 流程+逻辑删除单据）；B. 保留观察 |
| **R5-02** | ACT_RE_DEPLOYMENT 持续增长（992→1023），流程定义版本达 78 | P3 | 部署去重过滤疑似未覆盖 deploy-bpmn.sh API 路径 | 排查 ProcessDefinitionService.deploy 过滤逻辑；暂不影响功能 |
| **R3-03 定性** | 58 条 SUBMITTED 调拨单 = E2E 测试残留（非真实审批卡点），并入 R5-01 | P1 | 归属已确证 | 随 R5-01 处置 |

## 四、验收结论

### ✅ 通过项
1. wf_approval_record ↔ ACT_HI_PROCINST 一致性：0 孤儿
2. wf_process_def ↔ ACT_RE_PROCDEF 对齐：无缺失
3. 权限四表（role_menu/user_role/menu/tenant）孤儿检查：全 0
4. 种子演示数据零污染（种子项目无运行中流程）
5. 租户 9999 运行态零残留

### 🔴 待处置
- R5-01：312 任务 + 关联单据清理（**涉及租户 1，必须用户决策**）

---

**报告生成时间**: 2026-08-17  
**审计人员**: Qoder + Human Collaboration  
**下轮计划**: 第 6 轮 system tables + flyway（sys_config/日志表/迁移历史一致性收官）
