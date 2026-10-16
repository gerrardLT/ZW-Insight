# 实施任务与验收边界

## 批次 0：Spec 与文档

- [x] requirements/design/tasks 三件套建立。
- [ ] `docs/deep-opt/真正需要补充的核心能力清单-2026-10-16.md` 入库（已核不含第三方名称）；其余竞品 untracked 文档保持不入库。

## 批次 1：F1–F3 + V2026_107

- [x] F1a 劳务 submit 预算复核（BLOCK 拒绝、WARN 入线程上下文、金额非正跳过）+ 单测 4 例。
- [x] F1b 预算 WARN 真实通知（UrgeNotifyEvent → PROJECT_MANAGER，无收件人 log.error 不谎报）+ 单测 2 例。
- [x] F2 签证关联变更事件：V2026_107 双轨迁移（change_event_id + active_visa_event_link 生成列唯一 guard）；save 四重校验（存在/同合同同项目/事件未直写/未被占用）；Handler 有关联跳过且不置幂等位；onApproved 终检封窄并发窗口；单测 9 例新增。
- [x] F3 taxDifference → invoiceAmountDifference（前端零消费已核）+ JavaDoc 正名。
- [x] API 脚本 keys/test-api-contract.sh 增关联守卫负向段（HTTP 200 + body 业务码断言，与 GlobalExceptionHandler 语义一致）。
- [ ] 签证表单前端关联选择器：并入批次 2 合同抽屉改造，不在本批。

## 批次 2：C1 过程结算 + V2026_108

- [x] 迁移：settlement_type/period/扣款三列/申报争议合计；biz_settlement_source 表（占用由应用层 JOIN 状态校验，登记为实现边界）；cumulative_settlement 列（COALESCE 容忍 NULL）；级联 Listener 登记 biz_settlement_source。
- [x] Service：PROCESS 保存/提交/生效三处校验来源（存在/已审批/同合同/占用 409/审定+争议≤申报/核减原因）；净额服务端重算；生效 CAS→addCumulativeSettlement（上限守卫）→发布 ProcessSettlementApprovedEvent（同步事务内）；FINAL 保留原行为并追加累计结算回写；驳回自动释放占用。
- [x] 应收：zw-common 事件契约 + zw-finance ProcessSettlementReceivableListener（同步监听不吞异常）+ generateFromProcessSettlement（幂等/期间末+账期到期/非正不生成）。
- [x] 前端：抽屉「过程结算」页签（列表/新建弹窗含来源选择与净额预览/提交）；竣工结算页签改只取 FINAL；签证表单补关联事件选择器（批次 1 遗留项）；api/types 契约扩展。
- [x] 测试：FinalSettlementServiceTest 重写（20 用例含 PROCESS 全路径）+ ControllerTest 签名适配 + ReceivableServiceTest 过程结算 3 例 + 组件测试 3 例新增（41/41 通过）+ api-contract 2 例 + shell 脚本负向守卫段。
- [x] 行尾体检：9 个 LF 文件误转 CRLF 已还原，全量复检 clean；vue-tsc 143（较存量 144 净减 1）。

## 批次 3：C2 条款 + V2026_109

- [x] V2026_109 双轨：biz_contract_pay_term 表（四型条款/比例金额二选一/质保期月数/扣回规则/宽限）；biz_retention_money 补 source_type/source_id + active_retention_source 生成列唯一 guard（**落差表"retention 缺 contract_id"结论过期：实体与 DDL 已有该列，本轮未加**）。
- [x] ContractPayTermService/Controller：CRUD + 归属不可改绑 + 质保金比例 3% 上限（财建183号）+ 施工合同 projectId 后端反查；calcRetentionAmount（封顶审定额）/suggestPrepayDeduct。
- [x] 修订快照纳入条款（ContractRevisionService.snapshot 扩展 contract+payTerms 结构）。
- [x] ProcessSettlementRetentionListener（zw-finance）：按结算单 retentionDeduct 单据事实计提，幂等双保险，质保期取条款无则留空不猜；事件契约补 retentionDeduct 字段。
- [x] 前端：抽屉「收付款条款」页签（CRUD 弹窗）+ 过程结算弹窗按 PREPAY 条款预填扣回并提示质保金条款；级联清理登记 biz_contract_pay_term。
- [x] 测试：ContractPayTermServiceTest 7 例 + ProcessSettlementRetentionListenerTest 3 例 + 组件 3 例（43/43）+ api-contract 3 例（38/38）+ shell 守卫段；行尾体检 1 处修复；vue-tsc 143 维持基线。

## 批次 4：C3 税务事实链 + V2026_110

- [ ] 四合同税三件套（派生持久化、导入导出、前端表单）。
- [ ] biz_invoice_issued 台账（防重/红冲挂原票/查验与入账状态）+ biz_invoice_received 状态列。
- [ ] 驾驶舱真税额指标（销项税/进项税/估算税差，basis 注明）。
- [ ] 单测 + 组件测试 + api 脚本；本地 commit。

## 批次 5：C4 价差 + V2026_111

- [ ] 明细价差字段 + 容差表 + calculateLines 扩展（三处重算、超容差强制原因）。
- [ ] 审批端偏离标识；单测（阈值内/超阈/重算）；本地 commit。

## 批次 6：C5 只读指标

- [ ] 工资点工 vs 劳务结算并列只读组（含未入 CBS 声明）。
- [ ] 四回款率 + 双毛利 + 元数据（numerator/denominator/basis/asOf）；CBS 不可用明示。
- [ ] ProjectBusinessSummary 组件测试更新；本地 commit。

## 收尾

- [ ] AGENTS.md 迁移进度更新（V2026_107–111，双轨缺口说明保留）。
- [ ] `node tests/check-test-sync.mjs --last` 无 UNSYNCED；一致性审计无新增 Critical；前端相关 vitest 通过。
- [ ] 受阻登记（test-maturity-upgrade/tasks.md）：后端编译/单测/MySQL 幂等/真实审批链/双机部署未运行。
- [ ] 完成报告：批次清单、commit 列表、未验证项。

## 验收边界（强制）

完成指代码与测试落地，非运行通过；后端/MySQL/线上验证依用户禁令未执行，不标通过、不虚报。不推送；推送与部署另行授权。
