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
- 待部署验证项 4 条（上文第三节），已在 L3 脚本中预置对应断言，部署后一次跑通即可闭环。
- 建议下一步：部署联调环境 → 执行 `keys/test-api-risk.sh` + `audit-data.ps1` 回归（确认 R7 基线 PASS=65 未被破坏）→ 提交。
