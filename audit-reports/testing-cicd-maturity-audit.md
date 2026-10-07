# ZW-Insight 测试与 CI/CD 成熟度审计（2026-09-24）

范围：`zw-insight-server/`（22 模块，1091 生产 .java / 384 测试 .java）、`tests/`、`.github/workflows/`（4 个）、`keys/`（83 个文件）、`tools/`。
方法：直接读文件 + 实测计数（`Get-ChildItem`/正则）+ 实跑两套 Node 测试 + git bash 语义验证。未验证的事项在文中显式标注。

## 1. AGENTS.md 测试声明核验

| 声明 | 判定 | 证据 |
|---|---|---|
| CI 基线比对门禁已移除；保留 pom 分档 jacoco check（默认 0.80），`mvn clean verify` 真实触发 | **TRUE** | `deploy.yml:87` 跑 `mvn -B -T 1C clean verify`；`pom.xml:92` `jacoco.min.coverage=0.80`；`pom.xml:442-462` `check` execution（默认 phase=verify）；22 个模块 pom 阈值与 `coverage-baseline.json` **逐一相符（22/22，实测比对）** |
| push 默认只跑 Backend Build → Deploy → 部署冒烟 | **PARTLY** | push 还额外跑 `frontend-test` 三端 matrix（`deploy.yml:140-190`）；且"健康检查"被 `continue-on-error: true`（`deploy.yml:323`）降级为**永不让流水线变红**，真正的硬断言只有 API 文档收敛（`deploy.yml:344-362`） |
| L2 Testcontainers、L3/L4/L5、k6 默认不跑，仅 `workflow_dispatch` + `run_tests=true` | **TRUE** | `deploy.yml:124`、`:376-378`、`:674-676`；唯一触发入口确为手动 dispatch |
| 集成测试使用 tenant_id=9999；`@AfterAll` 调 `TestDataCleaner.cleanByTenantId(9999L)` | **PARTLY** | `TestDataCleaner` 确实存在且被 test-jar 化（`zw-common/pom.xml:72-92`），被 7 个模块的 IT 引用（如 `zw-finance/.../FinanceIntegrationTest.java:43,60`）；但**这些 IT 在 Maven 里从不执行**（见 §3），且部分用 `@AfterEach`/`@BeforeEach` 而非 `@AfterAll` |
| L3 契约验证"9 个独立脚本 `test-api-{...,quote,...}.sh`，断言不满足一律 FAIL，不静默跳过" | **PARTLY / 部分 FALSE** | 实际 **29** 个 `test-api-*.sh`，且 `keys/test-api-quote.sh` **不存在**；29 个中 28 个有真实退出闸门（`report_summary`/`[ "$FAIL" -eq 0 ]`），但 `keys/test-api-tenant-isolation.sh:359` 是无条件 `exit 0` → 永远通过 |
| L4 `lifecycle-sim-v2.sh` 跑通 **19 个阶段**，退出码严格反映结果 | **PARTLY** | 脚本内 `phase()` 被调用 **26** 次（`lifecycle-sim-v2.sh:108` 定义），"19"是过期数字；**"退出码严格反映结果"为 TRUE**：`:1645-1648` `if [ "$TOTAL_FAILED" -gt 0 ]; then exit 1` |
| `bash keys/verify-l4-clean.sh` 作为"清理验收（残留应为 0）" | **FALSE（实质）** | 全脚本只有 4 条 `echo`（`:10,:13,:16,:19`），无任何 `[ "$TOTAL" -eq 0 ]`、无 `exit`；末句 `:21 echo "=== done ==="` → 恒 exit 0；且 `:8,:9` 的 `2>/dev/null` 让 SQL 报错变成"空 → 打印干净" |
| `cleanup-garbage-data.sh` 内置 **3 道不变量断言** | **PARTLY** | ①`:75-79` `exit 3` 真实（`:-1` 使 SQL 错误也触发，写法优良）；③`:258-261` `exit 4` 存在；②`:91-92` 只是 `printf` 循环，**不是断言**（AGENTS.md 自己把"打印"算作断言） |
| 改审计脚本必须验证 FAIL 能触发；已知"报错当零违规" | **PARTLY** | 该具体缺陷已修（`audit-data-round7.sh:510-513` 换表名，注释记录原因）；但**同类守卫仍未加**：`Q()` 用 `2>/dev/null` 且从不检查状态（`:81`），`:119` `actual="${3:-0}"` 把报错强制为 0，`:474-481` `cnt="${cnt:-0}"` → `=0` → `PASS_COUNT++`。另 `:251-260` 的 R5-01 基线断言只有 PASS/WARN/INFO 三支，**没有 FAIL 支**，SQL 报错（0<312）反而记 PASS |
| 两个 Node 工具"有测试" | **TRUE** | consistency-audit：12 测试文件 / **202 例**（实跑 **1 failed / 201 passed, exit 1**）；feature-ledger：4 文件 / **30 例**（实跑 30/30 绿）。`tools/consistency-audit/tests/property/real-data-invariant.property.test.ts:399` 断言登录页含 `captchaCode`，而前端已改为 `SliderCaptcha`（`zw-insight-web/src/views/login/SliderCaptcha.vue:29`）→ 套件在 main 上是红的，因为没人跑它 |
| `tests/README.md` 与 `TESTING-MATURITY.md` 是权威现状 | **FALSE（过期）** | `tests/README.md:15,220` 仍称"实际生效门槛 = baseline 只升不降（CI 逐模块比对）；pom jacoco check 绑定 verify 但 **CI 当前跑 package 未启用**"——与 2026-08-24/09-16 后的现状**正好相反**；`coverage-matrix.md:4` 的 "126 Controller / 150 Service / 225 测试类 / L3 8 脚本 / L4 19 阶段 / L5 56 spec" 与实测（160 Controller、196 Service 类、372 测试类、29 脚本、26 阶段、62 spec 文件）全面脱节；`tests/affected-modules.sh` 全文仅 `main "$@"`（11 字节），是必然报错的残桩 |

## 2. 测试清单（模块 | 生产 java | 测试文件 | 测试类 | @Test | 说明）

| 模块 | 生产 | 测试文件 | 测试类 | @Test | 备注 |
|---|---|---|---|---|---|
| zw-app | 3 | 64 | 59 | 195 | **跨模块**：58 个是其他模块 Controller 的 `@WebMvcTest` 切片 + 10 个 L2 Testcontainers；本模块仅 3 个生产类 |
| zw-archive | 6 | 3 | 3 | 15 | 884‰ 覆盖，Service 层 1:1 |
| zw-basedata | 37 | 10 | 10 | 53 | |
| zw-budget | 52 | 22 | 22 | 269 | Service 层 1:1（9/9） |
| zw-common | 46 | 23 | 16 | 150 | 含 TestDataCleaner/TestConstants/IntegrationTestBase；**5 个横切组件零测试** |
| zw-contract | 68 | 26 | 26 | 189 | |
| zw-dashboard | 34 | 9 | 9 | 116 | 断言质量最好的一批（含 `never()` 负向、`assertThatThrownBy`） |
| zw-file | 67 | 20 | 20 | 123 | **7 个 ImportListener 零测试** |
| zw-finance | 168 | 42 | 42 | 411 | 工作量最大；**6 个审批 Listener/定时任务零测试** |
| zw-hr | 43 | 13 | 13 | 87 | |
| zw-labor | 53 | 14 | 14 | 127 | |
| zw-machine | 47 | 11 | 11 | 82 | |
| zw-material | 47 | 13 | 13 | 106 | |
| zw-message | 35 | 8 | 8 | 38 | **4/4 事件 Listener 零测试** |
| zw-project | 24 | 6 | 6 | 124 | |
| zw-purchase | 56 | 12 | 12 | 103 | 文件比 0.21 |
| zw-security | 35 | 13 | 13 | 136 | `AuthInterceptor`/`SecondaryConfirmAspect` 零测试 |
| zw-site | 60 | 24 | 24 | 125 | |
| zw-subcontract | 25 | 6 | 6 | 82 | |
| zw-system | 90 | 27 | 27 | 178 | 阈值最低 417‰ |
| zw-tender | 33 | 10 | 10 | 81 | |
| zw-workflow | 62 | 8 | 8 | 78 | **文件比 0.13（最低）**；**15/15 非 Service 组件零测试**（3 Listener + 7 回滚策略 + Registry + 2 定时任务） |

合计：372 个 `*Test/*Tests/*IT` 类、**2868 个 `@Test` + 4 个 `@ParameterizedTest`**（另有 jqwik `@Property` 未计入）、约 5808 次断言/verify 调用（≈2.0/test）。
**没有任何模块是零测试**；按"Service 类是否有对应 `*ServiceTest`"口径，196 个 Service 类仅 3 个缺失（`ContractExpiryService`、`FileService`、`SubcontractRewardPunishService`）——"Service 层 1:1"这一条**基本成立**。
**真正的系统性缺口是非 Service 承载逻辑的组件：95 个 Listener/Task/Handler/Strategy/Registry/Interceptor/Aspect 中 55 个（58%）无任何对应测试**，其中含全部 7 个资金/结算回写 Listener、全部 3 个审批监听器、全部 4 个消息事件 Listener——恰是 AGENTS.md §10 与资金口径章节反复强调"不得吞异常/不得谎报成功"的那些类。

代表性抽样（5 个文件）：
- `zw-app/.../budget/controller/BudgetControllerTest.java:29-116`：`@WebMvcTest` + `@MockBean`，断言 `status/code/jsonPath` 并对 mock 打桩值做断言，属**路由/绑定层真实但极浅**；`@Import(TestSecurityConfig.class)` 而 `TestSecurityConfig.java:48-55` **注册空拦截器链、彻底关闭认证** → 58 个 Controller 测试**不可能发现鉴权/越权回归**。
- `zw-app/.../integration/BaseIntegrationTest.java:42-51`：真 Testcontainers MySQL8+Redis（单例容器）。
- `zw-finance/.../integration/FinanceIntegrationTest.java:37-40,68`：真 HTTP 打服务器，**但在 Maven 中永不执行**。
- `zw-dashboard/.../service/CockpitServiceTest.java`：断言真实计算值（`forecastProfitRate 0.2286`）、`verify(..., never())` 负向、非法筛选抛异常——**高质量**。
- `zw-archive/.../ArchiveServiceFallbackTest.java:55-70`：Mockito 模拟 15+ Mapper 驱动兜底分支，真实断言。

## 3. 覆盖率现实 vs 声称

- 门槛**真实存在且是真闸门**：`pom.xml:442-462` `jacoco:check`（默认绑定 verify）＋ `deploy.yml:87` 跑 `verify`。22 个模块 pom 阈值 = `tests/coverage-baseline.json` 千分比/1000，**实测 22/22 完全一致**（如 `zw-system/pom.xml:19 = 0.417` ↔ JSON 417）。
- **声称的 80% 目标只对 3 个模块成立**：19/22 模块阈值 < 0.80；实际范围 **417‰（zw-system，41.7%）～ 884‰（zw-archive）**，中位数约 634‰。AGENTS.md 与 `TESTING-MATURITY.md:47,99` 写的"342‰~884‰""最低 zw-common 342‰"**与 JSON 不符**（zw-common 实为 530，最低者是 zw-system 417）。
- **`TESTING-MATURITY.md:106-124` 附录 A 自称"与 tests/coverage-baseline.json 一致（2026-08-24 全量刷新）"，实测 5/22 行不符**：budget 816 vs 830、finance 779 vs **710**、project 572 vs **744**、dashboard 457 vs **570**、common 342 vs **530**。文档一律**低报**。`frontend-coverage-baseline.json` 为 web 665/app **773**/portal 857，而 `TESTING-MATURITY.md:126` 写 app **875**——同样不符。（`≥80%` 的 ✅/❌ 结论未受影响。）
- 棘轮性质：阈值≈当前覆盖，无余量 → 新增未覆盖代码即红灯，这一点是**真棘轮**。但**可被静默绕过**：`mvn verify -DskipTests` 时 `jacoco.exec` 缺失，JaCoCo report/check 走"Skipping"路径，**不失败**（`pom.xml:406` 的注释表明团队已观察到该 Skip 行为）。`deploy.yml:87` 的 `fast_deploy` 分支即 `mvn -B -DskipTests clean package`——一条命令即可把无测试产物部署到生产。
- L2 步骤显式 `-Djacoco.skip=true`（`deploy.yml:130`），注释仍写"当前覆盖率未达 60%"（`:128`），与分档门槛现状脱节。

## 4. CI/CD：什么真正在把关

| 工作流 | 触发 | 实际门禁 |
|---|---|---|
| `deploy.yml` | `push: main`（仅 `**.md`/`.kiro/**`/`audit-reports/**`/`docs/**` 跳过）＋ `workflow_dispatch(run_tests, fast_deploy)` | 单测 + jacoco 分档 check（真）→ 三端前端单测 + stylelint + app `build:h5`（真）→ 部署 → 健康检查（**`continue-on-error: true`，假的**）→ API 文档收敛（真，且显式拒绝 000 假通过 `:347-352`） |
| `codeql.yml` | push main / **pull_request main** / 周一 cron | 分析真实，但无门槛语义（默认无 branch protection 的告警） |
| `security-scan.yml` | **仅**周三 cron + dispatch（**不含 push/PR**） | **双重装饰**：`:40 -DfailBuildOnCVSS=11`（CVSS 上限 10，阈值永不可达）**且** `:48 continue-on-error: true` |
| `performance-k6.yml` | 每晚 23:00 cron + dispatch | 真实：`run-k6.sh:126-129 exit 5`，k6 阈值硬编码在 js（`login.js:25` p95<3000 等）。但**无历史基线比对**（`:131` 只提示"回填 tasks.md"）→ 是监控，不是回归门禁；且**不随代码提交运行** |

**结论：任何测试都不在合并前运行。** `deploy.yml` **没有 `pull_request` 触发**——测试全部发生在 push 到 main **之后**。一个破坏性提交可以直接进 main（并已部署），红灯只是事后告知。仓库内不存在可验证的 required-check 配置（`gh`/branch protection 无法从工作区读取，此处为限制而非结论）。
其他漏洞：`test-api-tenant-isolation.sh` 永远绿灯（见 §5）；`deploy-bpmn.sh` 两处 `|| true`（`deploy.yml:438,442`）让 L4 前置静默失败；`tests/run-all-tests.sh`、`flake-check.sh`、`affected-modules.sh`、两个 Node 工具**均无任何 workflow 调用**；`.github/workflows/*.yml:37` 明文硬编码 NVD API Key `b1cc005e-...`（注释声称"已在 GitHub Secrets 配置"，密钥本体却在仓库里）。

## 5. Shell / E2E 验证质量

- **L4 是真实资产**：`strict_assert`(`:135-151`)、`assert_status`(`:481-492`)、`assert_amount`(`:494-508`，0.01 容差)、`require_id`(`:470-478`)、`neg_assert`(`:512-524`) 均 `record_stage_result FAILED; exit 1`；终局 `:1645-1648` 硬退出；含 5 个负向用例（预算 BLOCK、驳回重提、超额质保金、超额备用金退还）。业务值断言真实（`totalExpense 245000` `:1197`、库存双向 700→500/200 `:1556-1586`）。弱点：无 `set -e`；`:696-703` 的 BLOCK 负向守卫被自身探针数据（`contractName` 含"预算"）削弱；`:146` `strict_assert` 在响应无 `code` 字段时跳过业务码校验。
- **L3 基本真实**：28/29 有退出闸门与 `jq` 内容断言，缺 `jq` 记 FAIL 而非跳过。**唯一致命例外**：`keys/test-api-tenant-isolation.sh:359` 无条件 `exit 0`，唯一失败信号在 `:184` 的 `trap cleanup EXIT` 内——**已用 git bash 实测确认**：`trap` 的返回状态被丢弃，脚本恒 exit 0。该脚本正是跨租户水平越权探针，且被 `deploy.yml:459` 的 `for script in test-api-*.sh` 收集、按退出码记 ✅。**越权泄漏会被 CI 报成通过。**
- **`keys/verify-l4-clean.sh` 完全无断言**（见 §1），却被文档当作"零残留验收"。
- **`keys/audit-data-round7.sh`** 仍属"报错当零违规"家族（`:81,:119,:474-481`），并有**永不可能 FAIL 的断言**（`:251-260`）。
- **E2E（L5）**：`playwright.config.ts:18 retries: 1` **全局重试一次**——与 `TESTING-MATURITY.md:104` "自动重试已决策不配置（用户 2026-08-10）…无实证场景"**直接矛盾**；重试使 flaky 用例仍报绿。且 `deploy.yml:524-548` 的三步用 `grep "UI_REAL_EXIT=0"` 判定，重试成功后即为 0。文档记的实际用例数与现实不符（api-tests **25 spec/429 例** vs 注释 356；e2e-real **17 spec/167 例** vs 注释 29；consistency **20 spec/54 例** vs 53）。
- 脚本规模：29 个 L3 脚本 + 26 阶段 L4 + 62 个 L5 spec，**覆盖面本身可观**，问题集中在少数"永远绿"的验收点。

## 6. 发现清单

| # | 严重度 | 发现 | 证据 file:line | 修复 |
|---|---|---|---|---|
| 1 | **Critical** | 跨租户越权探针恒 exit 0 → 越权泄漏在 CI 报绿 | `keys/test-api-tenant-isolation.sh:359`（+`:184` trap 状态被丢弃，git bash 实测） | 删除 `:359` 的 `exit 0`，改在 `:184` 处 `exit "$FAIL_COUNT"` 语义（trap 内显式 `exit`），并把该脚本移出 `for` 循环单列一个 required step |
| 2 | **Critical** | 11 个模块级集成测试类 / **107 个 @Test** 在 Maven 中永不执行 | `pom.xml:414-417` 排除 `**/*IntegrationTest.java`；`pom.xml:491-494` failsafe `includes` 仅 `**/com/zwinsight/integration/*`；`deploy.yml:130` 又限定 `-pl zw-app` | failsafe includes 增加 `**/com/zwinsight/{module}/integration/*IntegrationTest.java`（或改 `**/*IntegrationTest.java`），并把 L2 步骤去掉 `-pl zw-app` |
| 3 | **Critical** | 无任何 pre-merge 门禁：测试全部在 push 到 main 之后 | `deploy.yml:3-13`（无 `pull_request`） | 增加 `pull_request` 触发同一 backend+frontend job；对 main 启用 required checks |
| 4 | **High** | 部署"健康检查"被 `continue-on-error` 永久豁免 | `deploy.yml:323` | 去掉 `continue-on-error`；仅在 fast_deploy 时豁免并显式标注 |
| 5 | **High** | `fast_deploy` 一条命令即可免测试、免覆盖率部署生产 | `deploy.yml:87`（`-DskipTests clean package`）＋`:143` | 限制为仅允许对已有绿色 SHA 触发（校验 `git rev-parse` 与最近绿 run 一致），或加 `environment` 人工审批 |
| 6 | **High** | 依赖漏洞扫描：触发不含 push/PR，阈值 CVSS=11 永不可达，且 `continue-on-error` | `security-scan.yml:9-11,40,48` | 阈值改 `-DfailBuildOnCVSS=7`、去掉 `continue-on-error`、加 push/PR 触发（可只跑新增依赖） |
| 7 | **High** | 仓库内明文 NVD API Key | `security-scan.yml:37` | 立刻吊销并轮换该 key，从文件删除 |
| 8 | **High** | 覆盖率门槛文档与 JSON 不符（5/22 行），"342‰~884‰"过期 | `TESTING-MATURITY.md:112-124`、`:47`、`:99`；`coverage-baseline.json` | 由 `coverage-baseline.json` 生成附录 A（脚本产出，禁止手写） |
| 9 | **High** | `tests/README.md` 描述的门槛与现状相反（称 CI 跑 package、baseline 门禁生效） | `tests/README.md:15,220` | 重写为现状（verify + 分档 check；基线门禁已移除） |
| 10 | **High** | 58 个 Controller 切片测试整体关闭鉴权，无法发现越权/权限回归 | `zw-app/src/test/java/com/zwinsight/test/TestSecurityConfig.java:48-55` | 增加保留真实拦截器链的 `@SpringBootTest` 权限切片（少量），覆盖 `@RequiresPermission` 矩阵 |
| 11 | **High** | E2E 全局 `retries: 1` 掩盖 flaky，且与文档决策矛盾 | `zw-insight-web/playwright.config.ts:18` vs `TESTING-MATURITY.md:104` | 设 `retries: 0`；如需重试，把"重试后通过"上报为 flake 指标而非绿 |
| 12 | **High** | consistency-audit 自带测试套件在 main 上失败（陈旧断言）且无人运行 | `tools/consistency-audit/tests/property/real-data-invariant.property.test.ts:399`（实跑 exit 1） | 更新为 slider-captcha 协议；把两工具的 `npm test` 加入 CI |
| 13 | **High** | 两个审计工具产出的 23 份报告无门槛：`Critical` 在全部 23 份中恒为 0（Critical 判定为资源前缀匹配，结构上几乎不可能触发） | `tools/consistency-audit/src/auditors/coverage-auditor.ts:298-316`；`src/cli.ts:178-179` | 把 Critical 判定细到 endpoint 级；报告接入 CI 并设 Major 预算棘轮 |
| 14 | Medium | `verify-l4-clean.sh` 无断言无退出码，却被当作验收 | `keys/verify-l4-clean.sh:10,21` | 加 `[ "${TOTAL:-x}" = "0" ] \|\| exit 1` 与 SQL 状态检查；否则从文档删除 |
| 15 | Medium | 审计脚本仍"报错=零违规"，并有永不 FAIL 的断言 | `keys/audit-data-round7.sh:81,119,474-481,251-260` | `Q()` 检查 mysql 退出码并在非 0 时记 FAIL；`:251-260` 增加 FAIL 支 |
| 16 | Medium | `cleanup-garbage-data.sh` 的"第 2 道不变量断言"只是打印 | `keys/cleanup-garbage-data.sh:91-92` | 任一分支命中数为 ERR 时 `exit 5` |
| 17 | Medium | L4 前置 `deploy-bpmn.sh` 用 `\|\| true` 吞错（两处），L4 仍会继续 | `deploy.yml:438,442` | 去掉 `\|\| true` 或失败即 `exit 1` |
| 18 | Medium | 55/95 非 Service 承载逻辑组件零测试，含全部资金回写与消息 Listener | 实测：`zw-finance/.../PaymentApplyApprovalListener.java` 等；`zw-message/.../UrgeNotifyEventListener.java`；`zw-workflow/.../` 15 个 | 优先补 Listener/Task：级联清理异常传播、审批回写、事件去重 key |
| 19 | Medium | 性能无历史基线，仅绝对值阈值 + 夜间监控，不随提交运行 | `tests/performance/run-k6.sh:131`；`login.js:25` | 落盘基线 JSON 并按比例设回归阈值（如 p95 退化 >20% 即红） |
| 20 | Medium | `tests/affected-modules.sh` 是 11 字节残桩（`main "$@"`），增量选择未实现 | `tests/affected-modules.sh:1` | 实现或删除 |
| 21 | Medium | 计划（Scheduled）任务与 flake 检测、编排脚本均未接入 CI | `.github/workflows/`（grep 无 `run-all-tests`/`flake-check`） | 至少把 `flake-check.sh` 挂到夜间 cron |
| 22 | Low | `coverage-matrix.md/json` 为 2026-08-10 快照，规模数字全面过期（称 126 Controller / 8 L3 脚本 / 19 阶段） | `tests/coverage-matrix.md:4,59` | 标注"历史快照"或重新生成 |
| 23 | Low | `feature-ledger` 人工层近似橡皮图章：132 个 `levelFinal` 中 129 个等于 `levelAuto`；已提交账本过期（147 条 vs 现状 168 条） | `tools/feature-ledger/data/ledger-data.json`（generatedAt 2026-09-10） | 定期 `scan` 提交；人工复核仅针对 auto 与直觉冲突项并留痕 |

## 7. 最高杠杆的 5 项修复

1. **修掉 `test-api-tenant-isolation.sh` 的恒真退出码**（#1）。一行改动，但当前它让最高风险的安全探针在 CI 里永远是绿的——这是"测试很多、真正的红线没接上"的典型。
2. **让 107 个沉睡的集成测试跑起来**（#2）。修 failsafe `includes` + 去掉 `-pl zw-app`，几乎零新增编写成本即可恢复 107 个 `@Test`（含预算 BLOCK、数据权限、租户边界、Flowable 流程）。
3. **加 `pull_request` 触发，把门禁前移**（#3）。当前所有测试都在 push 到 main 之后跑，质量门禁在因果上晚了一步；这一项决定其余所有努力是否具备"阻止坏代码"的能力。
4. **清掉三重"假门禁"**：健康检查 `continue-on-error`（#4）、依赖扫描 `failBuildOnCVSS=11` + `continue-on-error`（#6）、`fast_deploy` 免测部署（#5）。三者共同解释了为什么"CI 全绿"与"生产可用"之间没有可靠因果。
5. **用脚本生成覆盖率叙事**（#8/#9/#22）：22 个 pom 阈值与 JSON 完全一致，是这套体系最扎实的部分；但三份人类可读文档各说一套（342‰ vs 417‰、app 773 vs 875、package vs verify），文档层的不一致会直接摧毁对整份测试报告的信任。

## 附：已验证但值得肯定的部分

- `pom.xml:442-462` + 22 模块 pom 阈值与 `coverage-baseline.json` **逐一相符**，且 `deploy.yml:87` 真跑 `verify` — 分档棘轮是真的。
- `lifecycle-sim-v2.sh:1645-1648` 终局硬退出、26 阶段、含 5 个负向用例与金额硬断言 — L4 是真资产。
- 28/29 个 L3 脚本有真实 `jq` 断言与退出闸门（缺 `jq` 记 FAIL 不跳过）。
- 196 个 Service 类中 193 个有对应单测；约 5808 次断言/verify 对应 2868 个用例，**测试不是空壳**。
- `zw-dashboard`（`CockpitServiceTest`）与 `zw-archive`（`ArchiveServiceFallbackTest`）的断言深度达到可用水平。
- `TestDataCleaner` 被 test-jar 化并真正被 7 个模块引用（`zw-common/pom.xml:72-92`）。
- `cleanup-garbage-data.sh:75-79` 的 `:-1` 写法是"让 SQL 报错也中止"的正面范例。
- 两个 Node 工具的 auto/manual 分层与 merge 保护是真实的（`merge.ts:81-89` 不删人工条目）。
- 报告被 commit 并确实驱动过修复（`e5b92f8`、`3b5656c`、`ad7a77c`）。

## 未验证/受限

- 无法读取 GitHub branch protection / required checks 配置（不在工作区），故"破坏性提交能否合并"仅能证明"工作流未提供 pre-merge 门禁"。
- `audit-data-round7.sh` 的"PASS=65 FAIL=0 WARN=0 INFO=40" 基线需连生产库复现，本次未执行。
- PIT 变异测试（finance 73%/sub 66%/proj 61%）无留存产物，无法复核，仅能确认 `pom.xml:521-555` 的 `mutation` profile 存在且**未接入任何 workflow**。
- `TESTING-MATURITY.md:104` 的 flaky 决策与 `playwright.config.ts:18` 的矛盾以配置文件为准（配置是执行现实）。
