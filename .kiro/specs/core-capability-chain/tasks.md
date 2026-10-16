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

- [x] 四类支出合同（采购/劳务/机械/分包）补税三件套列与实体字段；save/update 服务端派生（zw-common 新增 TaxAmounts 差额法工具，税率未知置空不猜填）；四前端表单加税率输入。
- [x] biz_invoice_issued 销项发票台账：数电/传统号码规则与 V2026_103 同源身份 guard（DB 生成列唯一）、红字挂原票（同项目同合同+剩余可红冲额封顶+税额取负）、查验/入账状态流转、删除防红冲悬空；权限复用 finance:invoiceapply:*（避免新码无菜单承载）。
- [x] biz_invoice_received 补 verify/use/bookkeep 状态列与流转接口（纯登记不动金额）；进项页加查验/入账列与操作。
- [x] 驾驶舱 getInvoiceLedger 补真税额指标：salesTaxAmount（台账净额蓝正红负）/inputTaxAmount/taxAmountDifference + taxMetricsBasis 注明估算口径。
- [x] 前端：销项台账新页面（登记/查验/入账/删除，红字选原票）+ 路由 + sys_menu 种子（508）与迁移内幂等菜单；菜单覆盖校验 missing 0。
- [x] 测试：InvoiceIssuedServiceTest 9 例 + DashboardServiceTest 补 mock + 组件测试 3 例（17/17 发票族通过）+ finance shell 守卫段；行尾修复 3 处；vue-tsc 143 基线。

## 批次 5：C4 价差 + V2026_111

- [x] V2026_111 双轨：biz_purchase_settlement_detail 补 source/settlement 单价、价差、原因四列；biz_settlement_tolerance 租户级容差表（默认 0=任何偏离须原因）。
- [x] calculateLines 扩展：结算单价可输入（缺省=入库价）、价差=（结算−入库）×数量、超 max(来源额×pct%,abs) 强制原因 409；create/update/submit（既有）/onApproved（新增）四处服务端重算；容差租户回退登录上下文（create 前置清空实体租户）。
- [x] 前端：LINES 表格加入库单价/结算单价/价差/原因列（价差高亮、无价差禁填原因）、编辑态回填已存单价与原因、金额按结算价预览；payload 携带单价与原因。
- [x] 测试：PurchaseSettlementServiceTest 新增 3 例（零容差拒绝/有原因按结算价+行字段断言/容差内免原因）+ purchase-matrix 组件 1 例（26/26）+ purchase shell 负向守卫段；vue-tsc 143 基线；EOL clean。

## 批次 6：C5 只读指标与口径治理

- [x] 新端点 GET /api/v1/dashboard/project/{id}/receipt-ratios：回款率四口径（/合同额、/累计产值、/累计开票、/累计结算）统一 value/numerator/denominator/basis/available/unavailableReason（零分母明示 DENOMINATOR_ZERO，不用 0% 冒充）。
- [x] 双毛利（产值−CBS实际、结算−CBS实际）：仅 CBS 可用（根账户口径与 ProfitSnapshot 同源防层级重复计数）；无账户明示 CBS_UNAVAILABLE 不回退审批口径。
- [x] 人工对照只读组：工资单+点工已批 vs 劳务累计结算并列 + 差额 + 「未入 CBS 权责成本（会计政策待定）」声明；跨模块只读 SQL（LaborCostReadMapper，沿 countTenderRegisters 只读惯例），不动 CostRollUp。
- [x] 前端：ProjectBusinessSummary 新增「回款与毛利」面板（后端值直显、不可算明示）与人工对照条；比率接口失败不影响既有两组。
- [x] 测试：ProjectDashboardServiceTest 2 例（四口径元数据/零分母、双毛利根账户去重+人工对照）+ 组件 3 例新增（9/9 通过）+ dashboard shell 只读断言；EOL 修复 3 处；TS 错误集与 HEAD 完全一致零新增。

## 收尾

- [ ] AGENTS.md 迁移进度更新（V2026_107–111，双轨缺口说明保留）。
- [ ] `node tests/check-test-sync.mjs --last` 无 UNSYNCED；一致性审计无新增 Critical；前端相关 vitest 通过。
- [ ] 受阻登记（test-maturity-upgrade/tasks.md）：后端编译/单测/MySQL 幂等/真实审批链/双机部署未运行。
- [ ] 完成报告：批次清单、commit 列表、未验证项。

## 验收边界（强制）

完成指代码与测试落地，非运行通过；后端/MySQL/线上验证依用户禁令未执行，不标通过、不虚报。不推送；推送与部署另行授权。

## 生产事故与恢复（2026-10-16 推送部署）

- **事故**：V2026_110 的 biz_invoice_issued 生成列把 PREPARE 字符串内「双写引号」写法（''D:''、''''）误抄进裸 CREATE TABLE，Flyway 语法错 → 双机后端崩溃循环（容器 Up<1s），CI 部署 health check 双失败。
- **恢复**：修复为裸 SQL 单写（'D:'、''、NULLIF(...,'')），57311696 推送；129 侧手工备份 flyway_schema_history（18.7KB）→ 删除 success=0 失败行 → 停旧容器阻断复发；43 侧由部署流水线自带「[3/5] 清理 Flyway 失败记录」步骤自动清理。CI run 38862232837 双机部署成功。
- **核验**：129 重启=0、Flyway 107-111 全 success、failed=0、4 新表+累计结算列+菜单508 就位、生成列表达式与设计一致、5 个新端点 401（已映射需鉴权）；43 经 CI health check 401 + 文档收敛通过（应用完整启动蕴含迁移成功），本机 SSH 因该机到本机网络路径不可达（banner exchange 超时，历史丢包问题）未能做行级核验。
- **教训**：迁移 SQL 语法在本轮无任何本地/CI 验证环节，唯一闸门是服务器临时库事务内预跑——已记入记忆（zwi-flyway-pitfalls 第 3/4 条）。
