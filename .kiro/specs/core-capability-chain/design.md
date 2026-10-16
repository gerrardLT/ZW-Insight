# 设计与变更记录

## 总体设计原则

- 事实链优先于指标：先有过程结算、真实票据、条款等事实，再派生指标；派生比率携带分子/分母/basis，前端不自算。
- 复用优先：审批复用既有 BPMN 与 `wf_business_type`；附件复用 `file_info`；应收复用 `ReceivableService`（sourceType=SETTLEMENT）；质保金复用 `biz_retention_money`；唯一性复用 V2026_103 生成列身份 guard 模式；状态迁移复用 approvePending CAS + 原子累加 + 影响行数守卫模式。
- 累计字段唯一写入方向：仅审批通过（APPROVED）写入，驳回/撤回/终止/删除对称反冲或释放占用；所有 UPDATE 检查影响行数。

## 批次 1：F1–F3（V2026_107）

- 劳务 `submit` 显式调用 `BudgetControlConfigService.checkBudget`，BLOCK 抛 `BusinessException`，WARN 放行（通知在 checkBudget 内触发）。
- `sendWarningNotification` 重写：查项目 `PROJECT_MANAGER` 成员 → 逐人 `UrgeNotifyEvent`（携带 `SecurityContextHolder.getTenantId()`，事件监听为 @Async 必须显式传租户）；无成员 log.error 返回失败；异常不阻断校验主流程（通知失败不改变 PASS/WARN/BLOCK 结果），但必须留 error 日志。
- V2026_107：`biz_change_visa` 加 `change_event_id BIGINT NULL`、`active_visa_event_link` 生成列（`deleted=1` 或无关联时 NULL，否则 CONCAT 值）+ `uk_active_visa_event(tenant_id, active_visa_event_link)`；`ChangeEventApprovedContractHandler` 在幂等位检查之后、回写之前查有效关联签证（`deleted=0` 且状态非 DRAFT 作废族），存在则 log.info 跳过合同回写。
- `taxDifference` → `invoiceAmountDifference`，同步改 JavaDoc；无前端消费（已核）。

## 批次 2：C1 过程结算（V2026_108）

- `biz_final_settlement` + `settlement_type`（默认 FINAL 存量兼容）、`period_start/period_end`；`biz_construction_contract` + `cumulative_settlement`；新表 `biz_settlement_source`（含 `active_source_identity` 生成列唯一 guard：tenant + source_type + source_id，deleted=1 → NULL，防同一来源被两张有效结算重复占用）。
- 来源占用集合 = 本结算 SUBMITTED + 全表 APPROVED（提交与审批生效前双重校验，模式同采购分批）。
- `onApproved(PROCESS)`：CAS → `addCumulativeSettlement(delta=审定-扣款)` 行数守卫 → 来源置 SETTLED → `ReceivableService` 生成应收（金额=本期应付进度款，到期日取期间末+宽限）。
- FINAL：校验并汇总展示 PROCESS；审定额仍走同一累计回写，另保留置 SETTLED 与项目结算额既有行为。
- 驳回/撤回/终止：状态退出占用集合，未入账不反冲；删除：DRAFT/REJECTED 直接删，APPROVED 仅 E2E 旁路可达且须对称反冲（与产值删除同款双条件）。

## 批次 3：C2 条款（V2026_109）

- `biz_contract_pay_term`：`(contract_category, contract_id)` 挂接五类支出合同与施工合同；term_type 四值；ratio/amount 二选一由服务端归一（同时填以 amount 为准）；`deleted` 逻辑删。
- 质保金计提幂等键：`(source_type='PROCESS_SETTLEMENT', source_id=settlementId)` 写入 `biz_retention_money` 前先查有效记录，存在则跳过；计提额=审定额×条款比例。
- 预付款扣回 v1=建议值：结算表单按 PREPAY 条款剩余未扣回比例计算建议额，人工可改，审定额=申报−各扣款后的净额口径在单据内自洽。

## 批次 4：C3 税务事实链（V2026_110）

- 四合同税三件套：仅 `tax_rate` 为输入列，`amount_without_tax/tax_amount` 服务端派生持久化（含税额与税率已知时）；税率未知三列全 NULL；导入模板加可选税率列，缺省不猜。
- `biz_invoice_issued`：`active_issued_invoice_identity` 生成列（数电 20 位号或 8 位号+10/12 位代码组合，规则同进项 V2026_103）；RED 必须挂 `original_id` 且金额不许超原票剩余可红冲额；`apply_id` 关联已批开票申请仅为溯源，不回写申请或合同累计。
- `biz_invoice_received` 补 `verify_status/verify_time/verified_by/use_status/bookkeep_status`，默认 UNVERIFIED/UNCONFIRMED/UNBOOKED，纯状态登记不改金额链路。

## 批次 5：C4 价差（V2026_111）

- `biz_settlement_tolerance`：tenant 级单行（pct_tolerance、abs_tolerance，默认 0）；容差上限 = max(来源金额×pct%, abs)。
- `calculateLines` 扩展：明细可带 `settlementUnitPrice`（缺省=来源单价）；`price_difference=（结算−来源）×数量`；超容差且 `differenceReason` 为空 → 409；三处重算点（save/submit/onApproved 复核）共用同一方法。

## 批次 6：C5 只读指标

- `CockpitService`/`ProjectDashboardService` 新增只读组与比率元数据；`ProjectBusinessSummary.vue` 呈现；全部只读查询，无新写入路径。

## 验证与回滚

- 每批次本地 commit；前端 vitest/组件测试 + check-test-sync + 一致性审计；后端与 MySQL 运行验证登记受阻。
- 回滚：代码回退保留可空新增列；生成列/唯一索引移除须备份评估；业务数据按单据反冲路径处理，不手改累计。
