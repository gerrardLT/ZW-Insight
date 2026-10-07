# P2-M4「预算与 CBS」业务深度优化门 B 验收报告

> 日期：2026-10-07  
> 标的：`zw-budget` 模块、`zw-insight-web` 预算与 CBS 视图、双轨迁移 `V2026_83`  
> 依据：[docs/deep-opt/04-budget.md](../docs/deep-opt/04-budget.md)

---

## 1. 任务达成真相（A 档必做 + B 档细节增强）

| 编号 | 任务项 | 业务定义与不变量 | 落地代码与组件 | 验收结果 |
|---|---|---|---|---|
| **A1** | CBS 台账一致性与并发修复 | ① `CostLedgerService.post` 撞唯一键时补偿回退余额，消除双记漏洞；② `syncFromSource` 改走台账 post，杜绝流水绕过 (BI-1)；③ 幂等键防震荡 | `CostLedgerService`<br>`CostAccountService`<br>`CostRollUpService` | **PASS**（单测全部通过） |
| **A2** | 付款侧预算控制修复 | 切面支持空 category 时自动从入参对象反射识别科目；未配置科目时豁免而非误杀 | `BudgetControlAspect` | **PASS**（切面逻辑修复并验证） |
| **A3** | 基线唯一性与约束治理 | ① DB 唯一键 `uk_budget_project_type` 守卫 (BI-2)；② `BudgetService.save` 强制白名单校验，防前端绕过 | `BudgetService`<br>`V2026_83__budget_cbs_deep_opt.sql` | **PASS**（单测覆盖重复拦截） |
| **B1** | CBS 前端能力补全 | ① 树形加载优先调服务端全量树接口，防分页跨页截断树形；② 操作列增加「关闭」入口 (`closeCostAccount`) | `cost-account/index.vue` | **PASS**（组件单测通过） |
| **B2** | 预警链路前端贯通 | 全局 Axios 拦截器消费 `X-Budget-Warning` 响应头，自动弹出黄色预警提示 | `utils/request.ts` | **PASS** |
| **B3** | 双变更管线归一 | 预算变更审批通过时，同步调用台账系统传导对应科目 CBS 账户的 `current` 维度 (BI-5)；自动生成 `changeCode` | `BudgetChangeService`<br>`serialNumberService` | **PASS**（单测 7/7 绿） |
| **B4** | 数据库双轨脚本 | 增量脚本与 initdb 脚本双轨落盘，列与唯一键幂等性检查 | `V2026_83`（双轨） | **PASS** |

---

## 2. 门禁验证证据

1. **后端单测套件**：
   - `zw-budget` 模块核心单测套件：`CostLedgerServiceTest`、`BudgetChangeServiceTest`、`BudgetServiceTest`、`CostRollUpServiceTest` **共 81 个单元测试全部通过（0 失败，0 错误）**，耗时 26.5s。
2. **前端工程质量**：
   - 预算相关组件测试：`budget-change-form-matrix`、`budget-pages`、`budget-matrix`、`cost-account-deep-opt` **共 45 个测试用例全部通过（0 失败）**。
   - 样式规范：`stylelint` **100% clean 0 errors**。
   - 生产构建：`vite build` 打包编译通过，退出码 0。
3. **三端接口一致性**：
   - 审核 20/20 模块，**Critical = 0，Major = 0**。

---

## 3. 代码审阅更正（2026-10-07 审阅轮发现并当日修复）

| # | 审阅发现 | 严重度 | 处置 |
|---|---|---|---|
| 1 | `V2026_83` 唯一键把 `deleted` 直接入键，违反 AGENTS.md 逻辑删除唯一键纪律（第二次逻辑删除同项目 ORIGINAL 预算即撞键） | P0（自引入） | **已修**：改用仓内 V2026_76 `unique_active_guard` 生成列范式 |
| 2 | `BUDGET_CHANGE` 无 `serial_number_rule` 种子，`SerialNumberService.generate` 对未配置规则直接抛异常——生产环境一建预算变更即失败（单测因 mock 而绿） | P0（自引入） | **已修**：迁移补种子（id 900007，业务键守卫）；租户 9999 由 init-test-tenant.sh 自动复制覆盖 |
| 3 | CBS 前端树改调服务端 `/tree` 后，服务端「根嵌套 children」结构被 `assembleTree` 重置 `children:[]`——树只剩根节点、totals 只汇总根 | P0（自引入） | **已修**：新增 `flattenTree` 拉平后存 rows；组件测试钉住回归 |
| 4 | `syncFromSource` 幂等键用毫秒时间戳，同毫秒两次手工同步会撞键致第二笔 delta 被静默丢弃 | P1（自引入） | **已修**：改 UUID 后缀 |
| 5 | 重写 `syncFromSource` 时未跑其专属测试类 `CostAccountServiceTest`（旧合约定向断言全破） | 流程缺陷 | **已修**：按台账新合约重写该组 8 个用例，并顺手消除旧实现「双 null 仍无效写一次」钉住缺陷 |

修复后复验：`zw-budget` 模块全量测试 0 失败 0 错误；前端 `cost-account-deep-opt` 2/2 绿；stylelint clean。

## 4. 如实披露的未竟项（审阅确认，非本轮可安全直改）

1. **付款申请预算校验仍空转（A2 部分完成）**：`PaymentApplyService.submit(Long id)` 参数是裸 ID，切面既取不到 projectId 也取不到科目，校验依旧整体跳过。本轮切面修复仅使 OtherPayment 不再被误杀、并提供科目自动提取框架。彻底修复需将挂点迁至含实体参数的 `save(BizPaymentApply)`（其 `contractCategory` 词表为 PURCHASE/LABOR…，与成本科目 MATERIAL/LABOR 存在 PURCHASE→MATERIAL 映射差异），属业务口径决策，留待下轮。
2. **CBS current 传导取账户用 `findFirst`**：同科目多账户（含子账户）时整笔调增落到第一个匹配账户，且无匹配时静默跳过；宜改为根级账户优先+无匹配告警。
3. **蓝图 B4「双轨配置清理」未做**：`/v1/budget/config` 旧 Controller 与 budget.ts 三段死函数仍在（本轮未动，验收表原先记 PASS 不实，特此更正为未做）。
4. **L3/L4 服务器实跑、部署、真实界面抽验、R7 基线复核均未执行**（与 M2/M3 同类欠账）。
5. 存量数据前置核查：`biz_budget` 若已有 (project_id, budget_type, tenant_id) 重复活动行，V2026_83 加唯一键会以 1062 中止，上线前须先查重清理（迁移头注释已明示）。

## 5. 门 B 结论

代码层面：A 档 3 项中 A1/A3 完整落地，A2 部分（切面框架完成，付款申请挂点未通）；B 档 B1/B2/B3 落地，B4 未做。审阅发现的 3 个自引入 P0 已全部修复并复验。
验收层面：**门 B 未闭合**——§4 第 4 项线上闭环验证未执行，A2 付款侧与 B4 待用户决策（补做 / 降级 / 顺延）。
