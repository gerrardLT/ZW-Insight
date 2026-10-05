# ZW-Insight 业务全链路 + 资金专项全量测试报告（核心四层）

- **日期**：2026-10-05 12:33 – 15:40 (+08)
- **测试对象**：主环境 129.204.3.200 + 徽颍 43.142.44.145（R3 双机部署 bbb2b5b 后复测）
- **执行依据**：GOAL.md 定稿合同（用户确认：测试+修复｜线上当前版本｜核心四层）
- **结论速览**：**全部验收达成——L1 / R7 审计 / L4 全链路 / L3 28⁄28 全绿**；发现并修复 1 个线上资金链路 P0 后端缺陷（已双机部署 + 端到端复测 32/32）+ 7 类测试基座缺陷；垃圾数据清理 355 行（备份在案）；终验审计 PASS=67 FAIL=0 WARN=0 基线完好

---

## 一、验收面结果总览

| 验收项 | 结果 | 证据 |
|---|---|---|
| 1. L1 单元测试 | ✅ 全绿 | `mvn test` 22/22 模块 BUILD SUCCESS（16:44）；修复后 `mvn -pl zw-contract -am test` 374 tests 0F/0E |
| 2. L3 API 契约（28 脚本） | ✅ 28/28 全绿 | 部署后复测 change-event 32/32（exit=0）；其余含资金专项 fund-loop 60/60、finance 82/0、risk 81/0、tenant-isolation 24/0 等 |
| 3. L4 生命周期模拟 | ✅ 全绿 | 26/26 阶段（报备→…→竣工结算→结案）；verify-l4-clean：biz_ 残留 0、Flowable 任务 0、租户 1 零污染、测试基建完好 |
| 4. R7 数据审计 | ✅ 基线一致 | PASS=67 FAIL=0 WARN=0 INFO=39（04:38Z 初审 + 05:37Z 终验，与 2026-09-24 基线完全一致） |
| 5. 资金不变量证据 | ✅ 三层齐备 | 见「三、资金专项结论」 |
| 6. 本报告 | ✅ | audit-reports/full-chain-fund-test-report-2026-10-05.md |

## 二、发现的缺陷与修复

### P0-1 后端：变更事件带成本明细提交评估必 500（资金传导主链断裂）⚠️
- **现象**：`POST /change-event/{id}/assessment` → 500「系统内部错误」
- **根因**：MyBatis-Plus `JacksonTypeHandler` 按字段裸类型（`List`）反序列化 JSON 列丢失元素泛型；`BizChangeEvent.affectedAccounts`（`List<AffectedAccount>`）从库读回后元素实为 `LinkedHashMap`，`ChangeEventService.validateCostDetails:495` 遍历强转即 `ClassCastException`
- **影响面**：costDelta≠0 时 affectedAccounts 为**强制要求**，故**任何真实变更评估都无法提交**；变更→CBS current_amount→预算传导主链（ChangeEventApprovalHandler→Outbox）在线上完全不可用。此前无人发现：单测 mock 实体类型正确永远复现不了，线上无真实用户走到该路径
- **修复**（本地已验证）：`BizChangeEvent` 三个 JSON 列 setter 单点归一化（`affectedAccounts`/`supportingDocs`/`affectedWbsIds`，Jackson `convertValue` + `TypeReference`），治愈校验/审批/Outbox payload 全部读路径；新增回归 `BizChangeEventJsonListTest` 4 例
- **连带修复**：`ChangeEventStatus.assertTransition` 的 `IllegalStateException` → `BusinessException`（状态违规属可预期约束，不再落「系统内部错误」兜底；5 处测试断言同步更新）
- **状态**：✅ 已双机部署并复测通过——CI run 37277038819（push main：ca48c3b/b75994b/bbb2b5b）全绿，server1/server2 并行部署成功；change-event L3 部署后 **32/32 exit=0**（评估→批准→APPROVED→负向全通，变更→CBS 成本传导首次在线上真实走通）

### 测试基座缺陷（7 类，已全部修复并复测）
| # | 缺陷 | 现象 | 修复 |
|---|---|---|---|
| B-1 | fund-loop 断言 21 跨月时间炸弹 | `!=当月` 应为 `>当月`，10 月起历史月快照误入断言范围 | keys/test-api-fund-loop.sh → 60/60 |
| B-2 | test-api-subcontract.sh 仓库内 CRLF 字节 | Linux bash 解析即炸（scp 必炸，Windows 检出风险） | 本地转 LF → 38/0 |
| B-3 | batch2/3/4 `get_token` 缓存无过期校验 | 46 天前过期 token → L0「登录 PASS」假阳性、后续全 401 | L0 强制清新登录 → 20/0、46/0、41/0 |
| B-4 | cost-control 断言用旧契约字段 | `metrics/categories` 已演进为 `totals/categorySummaries` | 对齐现行 DTO → 9/9 |
| B-5 | change-event 脚本 3 处腐化 + URL 未编码中文 | 废枚举/废字段/废状态名；approve 中文 query 被 Tomcat 400 | 逐项对齐现行契约（并顺藤摸出 P0-1） |
| B-6 | test-api-project.sh 清理假阳性 | TENDERING 不可删但无条件打印"已清理"→ 每跑一次租户 1 沉淀 1 条 `API测试项目` 残留 | 清理后 GET 核验 + 显式残留警告 |
| B-7 | change-event 详情断言裸插值 ID | 后端 Long 序列化为字符串（防 JS 精度丢失），字符串≠数字恒 false | 断言加引号按字符串比较 → 32/32 |

### 观察项（记录，不处理）
- 滚动预测分页含历史月快照（生成时点正确值）；消费方应取「当月」而非「第一条」
- audit-data.ps1 的 scp 下载步骤被 SSH post-quantum stderr 警告 + `$ErrorActionPreference='Stop'` 中断（两次复现），报告需手动拉取
- 单模块 `mvn -pl zw-contract test`（无 `-am`）解析 ~/.m2 旧 zw-common jar 会出 `UrgeNotifyEvent` NoSuchMethod 假失败
- machine 模块 `BizMachineWorkSettlementDetail.workLogIds`（`List<Long>` + JacksonTypeHandler）存在同族隐患，当前 main 代码无消费点，未动

## 三、资金专项结论（用户核心关切）

1. **勾稽全绿**：R7 Section 3 全部 MISMATCH=0——五类支出合同 `cumulative_paid/settlement`、项目 `total_income/total_expense/cumulative_output`、开票/收款累计、CBS 账户余额 vs 流水（3.6），线上数据与单据汇总一致（容差 0.01）
2. **双口径不回写**：单测 `PaymentApplyServiceTest:993` 显式断言 `markPaid` 永不回写 `total_expense`；审计勾稽通过是其数据侧佐证
3. **滚动预测逾期口径（V2026_63）**：当月逾期 5850 万正确计入、未来月份全 0 不摊开、`expectedPayments ≥ overdueUnpaid` 构成关系成立、当月净缺口为正（不再误显盈余）
4. **资金闭环端到端**：L4 付款闭环/质保金/保证金/退保证金/竣工结算/结案阶段全过；L3 fund-loop 60 断言全过
5. **⚠️ 唯一断裂点**：变更事件成本传导（P0-1）——这不影响既有账实一致（从未成功入账过），但意味着「变更驱动预算调整」能力实际不可用

## 四、残留与清理

| 项 | 处置 |
|---|---|
| 3 条 ASSESSING 变更事件（本次排查产生） | ✅ 经真实 API cancel（code=200×3） |
| tenant-isolation P9 墓碑 2 行 | ✅ mysqldump 备份后物理清除（backups/t9999-tombstone-20261005.sql） |
| `API测试项目-自动化`（TENDERING，租户 1）+ 历史堆积 | ✅ 已执行清理：删除 355 行，备份 /root/zwi-deploy/backups/zw_insight_pre_cleanup_20261005T073524Z.sql，存活项目仅剩 4 演示种子，累计值守卫全部正确跳过 |
| 终验审计（部署+复测+清理后） | ✅ PASS=67 FAIL=0 WARN=0 INFO=39 基线完好 |

## 五、变更清单（本地工作区，未提交）

**后端**（P0 修复）：
- `zw-insight-server/zw-contract/src/main/java/com/zwinsight/contract/domain/BizChangeEvent.java`（setter 归一化）
- `zw-insight-server/zw-contract/src/main/java/com/zwinsight/contract/domain/ChangeEventStatus.java`（异常类型）
- `zw-insight-server/zw-contract/src/test/.../ChangeEventStatusTest.java`、`ChangeEventServiceTest.java`（断言更新）
- `zw-insight-server/zw-contract/src/test/.../BizChangeEventJsonListTest.java`（新增回归）

**测试脚本**（keys/）：test-api-fund-loop.sh、test-api-subcontract.sh、test-api-batch2-s4-s9.sh、test-api-batch3.sh、test-api-batch4.sh、test-api-cost-control.sh、test-api-change-event.sh、test-api-project.sh

**证据文件**：audit-reports/audit-round7-2026-10-05T{04-38-00,05-37-13}Z.md；GOAL.md 轮次记录
