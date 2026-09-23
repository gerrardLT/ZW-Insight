# Ultra-Review 审查报告 — 资金闭环与经营驾驶舱改造

- **审查日期**：2026-09-23
- **分支**：`feature/boss-cockpit-fund-flow`（未提交变更约 113 项）
- **审查范围**：7 个迁移脚本（V2026_56~62 双轨）、应收台账 / 预计利润 / 风险中心三大子系统、三端页面、8 个定时任务
- **审查方式**：双线并行（代码级：逻辑/安全/边界；架构级：不变量/双轨迁移/口径一致性），全部结论基于实读代码、实跑测试、实查审计
- **说明**：原定 CodeReview 子代理因平台额度限制不可用，改由主代理执行同等深度审查；本文档中「未验证项」与「确认缺陷」严格区分，不含臆测

---

## 一、发现并修复的缺陷（6 项）

| # | 级别 | 问题 | 位置 | 后果 | 修复 |
|---|------|------|------|------|------|
| 1 | **Critical** | 存量应收初始化主键 `s.id * 10 + 1` 溢出 | `V2026_57__receivable_ledger.sql`（双轨两处） | 结算单 id 为雪花 ID（约 2.1e18），×10 = 2.1e19 **超出 BIGINT 上限 9.22e18** → 生产库迁移直接失败、应用启动中断。演示种子 id=93301 过小，掩盖了该问题 | 改用 `ROW_NUMBER() OVER (ORDER BY s.id)` 低位序号（1..N），与雪花空间天然隔离；按 s.id 升序编号保证增量重跑不撞号 |
| 2 | **Critical** | 风险规则执行失败时，其类型旧风险被误判「风险消失」而批量自动关闭 | `RiskScanService.scan` | 规则抛异常 → findings 为空 → 该类型所有 OPEN/PROCESSING 风险被置 RESOLVED，静默丢失全部该类型在管风险 | 新增 `autoCloseTypes`（仅本轮**成功评估**的规则类型）参与自动关闭；失败类型记入 `skippedAutoCloseTypes` 如实上报 |
| 3 | **Major** | 风险台账排序在应用层二次排序，只作用于当前页 | `RiskScanService.page` | SQL 按金额分页后再按级别排序 → 第 2 页的 RED 排在第 1 页的 YELLOW 之后，跨页顺序错乱 | 排序下沉到 SQL 层：`ORDER BY FIELD(severity,'RED','YELLOW','INFO'), impact_amount DESC`（`LambdaQueryWrapper` 无 String 列名重载，用 `last()` 追加常量 ORDER BY） |
| 4 | **Major** | 移动端下拉刷新在列表加载中时刷新圈永久卡死 | `zw-insight-app/src/pages/cockpit/risk-center.vue` | `loadData` 在 `loading=true` 时提前 return，不经过内部 finally → `refreshing` 永不复位 | `onRefresh` 增加 `try/finally` 兜底复位 |
| 5 | **Major** | 快照任务与成本归集任务同点执行（均为 02:30） | `ProfitSnapshotTask` | 快照成本侧取自 CBS（`biz_cost_account`），与 `CostRollUpTask` 并发会读到归集中的中间值 → 快照数值抖动、月度趋势产生假断点 | cron 调整为 03:30（归集完成之后），类注释写明全链时序依赖不可提前 |
| 6 | **Major** | 应收核销/反冲为 read-modify-write，存在并发丢失更新 | `ReceivableService` + `BizReceivableMapper` | 本仓**未注册 `OptimisticLockerInnerInterceptor`**（已全仓确认），`updateById` 不带 version 条件；并发核销同一批应收会丢失其中一笔 | 新增原子 SQL `addWrittenOffAmount(id, delta)`：累加 + 状态切换收敛到 DB 层（`GREATEST(0,…)` 防负）；反冲时按「实际可反冲额」回加项目单值并告警，严格保持不变量 |

**修复后的验证**：
- `zw-finance` 全量单测 SUCCESS（含 `ReceivableServiceTest` 14 项，新增异常容错用例）
- `zw-dashboard` 全量单测 SUCCESS（166 项，含 `RiskScanServiceTest` 13 项，新增 `skippedAutoCloseTypes` 断言）
- 移动端单测 30 files / 246 passed；移动端 `build:h5` 成功
- 三端一致性审计：**Critical 0 / Major 0** / Minor 323（与修复前持平，未引入新不一致）

---

## 二、验证通过项（覆盖面清单）

| 风险面 | 结论 | 证据 |
|--------|------|------|
| 迁移幂等性 | 通过 | 7 个脚本：`CREATE TABLE IF NOT EXISTS` 齐备；4+2 处 `ALTER` 全在 `information_schema` 条件内（PREPARE/EXECUTE）；`sys_role_menu` 插入有 `NOT EXISTS` 守卫；`sys_menu` 用 `INSERT IGNORE` |
| 双轨一致性 | 通过 | 7 组文件逐对比对（剔除注释行）内容完全一致；`58~64_` 与 `V2026_56~62` 版本段严格对应 |
| 双口径不变量 | 通过 | 全仓排查 `totalExpense` 写入点：仅 `onApproved`（审批口径唯一入口）与 `delete`（对称冲销）触碰；`pay_status`/`markPaid`/`revokePaid` 全路径不写 `total_expense` |
| 应收不变量 | 通过 | `generateFromSettlement`（增）/`writeOff`（减）/`reverseWriteOff`（回加）三路径均同事务双写 `project.receivable_amount`；异常截断时按实际值回加并告警（不静默） |
| 定时任务时序 | 通过（已修 #5） | 01:15 资金预测 → 02:00 风险扫描（消费预测快照）→ 02:30 成本归集 → 03:30 利润快照；08:00 质保金 / 08:30 应收逾期已错峰 |
| 前端数据真实性 | 通过 | `views/cockpit/**`、`pages/cockpit/**` 全量 grep：无 `mock`/模拟数据/`TODO`/写死数据；无数据时触发真实扫描而非伪造 |
| 前后端契约 | 通过 | 一致性审计 后端 873 / PC 757 / 移动端 88，20/20 模块，Critical 0 / Major 0 |
| 权限 | 通过（有边界） | 三个新 Controller 均类级 `@RequiresPermission`（`dashboard:view`/`finance:view`，均为已登记权限，无伪权限） |

---

## 三、未验证项与已知边界（如实登记）

1. **`last()` + 分页插件 SQL 顺序**：`ORDER BY` 由 `last()` 追加，分页插件在其后拼 `LIMIT` —— 该顺序为 MP 既定行为但**未在真实 DB 验证**。已在 `keys/test-api-risk.sh` 新增断言「YELLOW 之后不再出现 RED」，部署后即可验证。
2. **迁移脚本未在真实 MySQL 执行**：本地无 Docker/MySQL（`docker` 命令不存在），仅静态审查。`ROW_NUMBER()` 需 MySQL 8.0+（部署环境为 `mysql:8.0` ✓）。
3. **L3 契约脚本待服务器执行**：`keys/test-api-risk.sh`、`keys/test-api-fund-loop.sh` 需真实登录基座 + jq，本地无法验证。
4. **乐观锁为仓级既有状况**：本轮仅将应收核销路径改为原子 SQL；其余 `updateById` 路径（如风险台账、快照 upsert）在并发下仍有潜在丢失更新，属存量风险，未在本改造范围内扩散处理。
5. **早于本次改造的残留**：`zw-insight-web/_online-reader.png`、`zw-insight-app/_devh5.log`（均已被 `.gitignore` 忽略，非本次产物）。

---

## 四、结论

- 本次审查发现 **6 项缺陷（2 Critical / 4 Major）**，全部已修复并通过回归验证；其中 #1 若不拦截将导致**生产迁移失败**，#2 会**静默丢失在管风险**。
- 一致性审计零 Critical/Major；三端测试全绿（后端 dashboard 166 / finance 全量 SUCCESS / 移动端 246）。
- 第三节 1/2/3 项未验证项已由本次部署完成闭环（见第五节）。

---

## 五、部署与线上验证（2026-09-23 完成）

**提交**：`1d59329`（后端）/ `a3ca45a`（前端）/ `8bbe923`（文档）→ ff 合并 main → push → CI/CD 部署成功
**流水线**：`35831117355`（Backend Build L1 + JaCoCo、三端前端单测、Deploy to Server 均 success；k6/Integration 按 push 策略 skipped）

| 验证项 | 结果 |
|--------|------|
| Flyway 迁移 V2026_56~62 | **7/7 OK**（2026-09-23 15:34:59，应用日志确认 repair+migrate 完成、Started in 22.81s 无 ERROR） |
| 新表 6 张 / 新列 6 列 | 齐备 |
| 应收台账存量初始化 | 2 条（OPEN，合计 260 万）；**不变量校验 mismatch_rows=0** |
| 菜单与角色绑定 | 驾驶舱 4 菜单 + 应收台账菜单，18 条绑定；`910062` permission=NULL 经复核符合既有惯例（资金模块 12 个菜单全为 NULL，全表 171 个菜单中 82 个如此；侧边栏由 sys_role_menu 决定，不看 permission） |
| **L3 风险中心**（含本次新增断言） | **通过 36 / 失败 0 / 跳过 1**。关键：[21] 分页排序「YELLOW 之后不再出现 RED」通过（验证 `last()`+`FIELD` 排序方案在真实 DB 生效）；[26] `skippedAutoCloseTypes` 字段断言通过；扫描 7 规则全部成功（`failedRules: []`）并真实产出 1 条应收逾期风险 `RECEIVABLE_OVERDUE:90002:ALL` |
| **L3 资金闭环** | **通过 21 / 失败 0**。真实数据验证：应收账龄 260 万（逾期 6 天，D0_30 桶）、利润趋势真实分月（income 分布 0/1000万/1100万…而非均摊）且月度合计=年度总收入（3100 万） |
| **数据审计 R7 回归** | **PASS=65 / FAIL=0 / WARN=0 / INFO=40 —— 与基线完全一致**（报告：`audit-reports/data-audit-round7-2026-09-23T07-45-59Z.md`） |

**唯一 SKIP 项**：`[15] 利润归因-结构校验` —— 当月无预计利润快照（快照由 ProfitSnapshotTask 每日 03:30 生成，部署当天尚未到执行时间）。脚本已如实记 SKIP，且「无快照时明确报错而非返回空对象」的负向断言仍 PASS。**待次日 03:30 任务执行后即可闭环**（逻辑本身已被 `ProfitSnapshotServiceTest` 9 个单测覆盖）。

**运维备注**：
- 审计脚本（9/18 版）尚未覆盖新表（biz_receivable / biz_profit_snapshot / biz_risk_register）的勾稽检查，本轮由 `_verify_deploy.sh` 与 L3 脚本补齐（不变量 mismatch=0）；建议后续将新表检查并入 audit-data-round7.sh。
- `keys/*.sh` 在仓库中以 CRLF 存储（本次已把 test-api-risk/fund-loop 修为 LF）；建议后续加 `.gitattributes`（`*.sh text eol=lf`）根治人工上传执行时的 CRLF 问题。

**结论**：改造已完整上线并闭环验证，R7 数据基线未被破坏，无遗留 FAIL 项。

---

## 六、部署后数据实证与追加缺陷（2026-09-23 下午）

用户质疑“资金方面是不是有很多模拟数据”，因此对线上库与全链代码做了实证排查。

### 6.1 排查结论：代码无模拟数据，但业务数据几乎全为演示种子

**代码层（已系统排查，无 mock）**：

| 排查项 | 结果 |
|--------|------|
| 后端 `zw-finance`/`zw-dashboard` 全模块 grep（模拟/mock/随机/fake/dummy） | 仅 3 处命中：2 处为声明无 mock 的注释，1 处即下方缺陷 D |
| 硬编码金额 | 仅 2 处合理业务常量（大额门槛 50 万、贴现基准 360 天） |
| 前端 `views/finance/**`、`views/cockpit/**` | 无 `Math.random`、无硬编码数据数组；无数据时显示 `—` 或空表格 |
| `FundDashboardService` / `DailyCashReportService` 逐行审阅 | 全部真实查库，无数据时抛 404「暂无日报数据，请先生成」 |

**数据层（线上实测，用 ID 段区分：90001-99999 = 演示种子）**：

| 表 | 总数 | 种子演示 | 真实业务 |
|---|---|---|---|
| 付款申请 / 回款登记 | 16 / 7 | **16 / 7（100%）** | **0 / 0** |
| 银行流水 / 余额快照 / 余额调节 | **0** | — | — |
| 保证金 / 工资专户 / 工资拨付 | **0** | — | — |
| 票据 / 融资 / 融资还款 | **0** | — | — |
| 月度资金计划 / 年度资金预算 / 计划明细 / 资金调拨 / 账户分组 | **0** | — | — |
| 应收台账 / 滚动预测 / 资金日报 / 科目 | 2 / 6 / 1 / 32 | 0 | 本次新功能生成 |

→ 共 **15 张资金功能表 0 行**；有单据的表 100% 来自 `deploy/db-init/31_V2026_26__seed_demo_data.sql`。用户看到的“不真实”源于此，而非代码造假。

### 6.2 缺陷 C（Critical，本次改造的口径遗漏）：滚动预测漏计全部逾期未付

**线上实测证据**：16 条 `APPROVED + UNPAID` 付款申请合计 **5850 万**，`payment_date` 分布在 2025-06-20 ~ 2026-06-15（全在过去）；未来 6 个月窗口内 **0 条**。

**后果**：
- 滚动预测 2026-09：`expected_payments=0`、`net_gap=-260万`、`risk_level=LOW`（显示盈余）；真实待付缺口应为 5850−260 = **5590 万，应 HIGH**
- `futureExpenseTop`（待支付大额支出 TOP）因下界为 `today` 而**恒返回空数组**（L3 实测 `data: []`），老板看到“无大额支出”是错的

**定性**：改造前同样不覆盖该场景（旧逻辑只读月度计划，而计划表为空），但本次引入 `pay_status` 后已具备识别“已审批未付”的能力，**未将逾期部分纳入预测属本人疏漏**。

**修复（V2026_63）**：
- 新增 `biz_fund_rolling_forecast.overdue_unpaid`（双轨 65_）作为构成列
- `generateRollingForecast` 重构为一次加载全窗口单据 + 内存分月（原每月查库一次）；`payment_date` 早于当月月初的归入逾期，全额计入当月且不向后续月份摊开
- `futureExpenseTop` 去掉 `>= today` 下界，返回新增 `overdueAmount` 构成（接口路径/方法不变，三端契约无影响）
- 前端 4 处展示同步：资金中心、资金看板（含卡片“含逾期未付 X”提示）、资金计划页、驾驶舱首页现金流预测卡

### 6.3 缺陷 D（Major，既有代码）：质保金预警通知为假实现

`RetentionWarningTask.sendWarning` 原为 `log.info(...)` + `return true`，并触发 `markAsSent` 写去重 key → **预警永远没人收到，且因去重不再重试**（静默失效），违反项目铁律。

**修复**：改经 `UrgeNotifyEvent` 走真实站内信链路（与 `ReceivableOverdueTask` 同范式），收件人取项目 `PROJECT_MANAGER`；无收件人时 `return false` + `log.warn`（记 FAILED、不写去重 key，下次重试），不伪造成功。

### 6.4 修复后验证

| 项 | 结果 |
|---|---|
| `zw-finance` + `zw-dashboard` 全量单测 | SUCCESS（dashboard 166；finance 含新增 9 个逾期/通知用例，定向 31 项全绿） |
| PC 前端单测 | 116 files / 1218 passed / 2 skipped |
| `vue-tsc` | 本次改动 4 个页面 + 2 个 api 文件 **零错误**；`project-cost-control.vue` 存量 5 处逐行比对未变（line 10/248/255/398/399） |
| stylelint | 0 error |
| PC build | 成功（1m32s） |
| 三端一致性审计 | **Critical 0 / Major 0** / Minor 323 |
| L3 脚本 | `test-api-fund-loop.sh` 新增 9 条逾期口径断言（当月计入/仅当月/构成项关系/缺口为正/TOP 非空） |

待部署后线上实证：预测当月 `overdue_unpaid` 应等于 5850 万（与库内逾期单据对账）。

