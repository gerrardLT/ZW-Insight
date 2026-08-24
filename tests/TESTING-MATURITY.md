# ZW-Insight 测试体系成熟度评估报告

> 文档状态：正式版 v2.0 再评估（2026-08-24）；上一版 v1.0（2026-08-05，54/100）
> 评估基线：main 分支 HEAD（3c78870）；最近全链绿 run 32647849931（2026-08-23）
> 维护约定：本文档数字必须可复现；每次阶段任务完成后更新对应章节，并在文末"修订记录"追加一行
> 本次评估方法：只读重评，不改任何 CI/源码/测试；数字均标注实测来源（命令/run 编号/文件），不估算

---

## 1. 评估方法

- **数据来源**：2026-08-24 只读再评估——资产规模由文件遍历实测（Get-ChildItem/Glob），运行证据引自 CI run 记录与 `.kiro/specs/test-maturity-upgrade/tasks.md` 台账；最近一次全链验证 run 32647849931（2026-08-23）全绿，未重跑（重跑无增量证据）
- **计数方式**：测试类/spec/脚本数量由文件遍历统计，非估算
- **对标基准**：Google/Stripe 等公开的工程测试实践（Testing on the Toilet、DORA elite performers）与测试金字塔经典模型
- **评分原则**：只认实测证据。"有配置但从未真实运行过"不计分；"有文档但无执行证据"降档；**门槛未实际强制的不计为已达标**（如 pom jacoco 0.80 绑 verify 而 CI 跑 package 未触发）

## 2. 体系盘点（实测，2026-08-24）

### 2.1 五层金字塔

| 层级 | 规模（实测） | 框架/工具 | 已验证能力 | 实测短板 |
|------|------------|----------|-----------|---------|
| L1 单元测试 | 后端 **322 个测试类**（Get-ChildItem 实测，v1.0 时 158）；web **103 文件 1098+2 例**；app 16 文件 111 例；portal 22 例 | JUnit5+Mockito+AssertJ+jqwik+JaCoCo；vitest+happy-dom+fast-check | 22 后端模块 Service 层 1:1 覆盖（阶段四 57 缺口清零）；前端 106 views 页面级全覆盖（2026-08-15）；三前端入 CI+基线门禁 | 后端行覆盖率 34.2%~88.4%（baseline JSON 实测），仅 zw-archive/zw-app/zw-budget 三模块 ≥80%，其余 19 模块低于该目标；前端覆盖集中于页面交互，跨页面业务流仍靠 L5 兼覆盖 |
| L2 集成测试 | **21 个集成测试类**（实测，v1.0 时 16） | Testcontainers（MySQL 8+Redis）+ @EnabledIfDockerAvailable；联调服务器真实库 SSH 隧道模式 | 审批回滚、数据权限、租户隔离、Flowable、安全登录链、预算 BLOCK、投标流转 | CI 仅手动 workflow_dispatch 触发（push 默认不跑，用户决策 2026-08-13）；多数业务模块仍无 L2 |
| L3 API 测试 | **25 个 shell 脚本**（实测 keys/test-api-*.sh，v1.0 时 8） | verify-base.sh 基座（真实登录）+ jq 字段结构断言；batch2-4 自包含租户 9999 建链（不依赖演示数据态） | 全部模块 Controller 契约覆盖（run 32647849931：25/25）；负向断言+零残留闭环 | 依赖线上真实服务（非 hermetic）；jq 断言以结构为主，字段级语义校验弱于 L5 consistency |
| L4 端到端 | **lifecycle-sim-v2.sh 26 阶段**（实测 stage_ 函数数，v1.0 时 19） | bash+真实接口+金额硬断言；隔离租户 9999；pre_clean_flowable 预清理 | 收入+支出+多类型付款+驳回+竞工结算+变更签证+质保金+备用金+退货退款+材料调拨+HR 审批（run 32647849931：26/26，210 断言） | 单用户串行，仅一条主干业务变体；依赖线上服务 |
| L5 前端 E2E | **63 个 spec**（实测）；三套均入 CI：api-tests 25 文件 **445 例**（vitest）+ UI real **168 passed/3 skipped**（Playwright）+ consistency **55 例** | Playwright 1.61 全真实模式；consistency 字段级前后端一致性 afterAll 硬断言 | 功能表 21 模块用例矩阵驱动（frontend-test-case-matrix.md，1123 用例枚举）；接口错误硬失败不静默 | 页面覆盖深但依赖演示数据态（历次 DATA 受阻主因）；3 条件性 skip 为数据态防护显式标注 |
| 移动端 | app **875‰** 基线（16 文件 111 例，23/29 页覆盖） | vitest+fast-check+uni 存储桩+happy-dom 组件环境 | 页面级组件测试（登录/签到/审批/材料/财务/现场）+ 离线队列/水印不变量；孤儿页 6 页补齐后 orphan-pages 9 例钉住 | 拖拽排序/水印合成 DOM 重依赖豁免（待真机验收，待决策 #10） |
| supplier-portal | 22 例 857‰ 基线 | vitest+happy-dom | token 注入/401 登出；四视图渲染与交互 | 仅 router/main 引导未覆盖 |
| 性能基线 | k6 3 场景（login/page-query/payment-submit）+ 真实验证码桥 | performance-k6.yml 每晚 cron；阈值硬断言 | P95/P99 基线已建（login p99 1.94s、分页 p99≤230ms、付款 p99 103ms）；事故复演能力（限速修复后 p95 13.81ms vs 事故日 10.24s） | 无预算制持续回归（仅夜间基线，未接入 PR 门禁） |
| 安全扫描 | CodeQL（java+js，周扫）+ dependency-check（周扫） | codeql.yml/security-scan.yml | 高危逐项处置（升级 9 项+豁免 4 项）；CodeQL 303 告警逐项复核 | 无 DAST；SecurityBoundaryIntegrationTest 仍为手写 |

### 2.2 特色资产（超出同级项目平均水平）

1. **consistency-audit 工具**（tools/consistency-audit，14 个 TS 源文件）：自动扫描后端 Controller ↔ PC 前端 api ↔ 移动端 api 三端一致性，输出 JSON+Markdown 审计报告，按 Critical/Major/Minor 分级
2. **测试数据纪律**：tenant_id=9999 隔离 + TestDataCleaner 强校验（非 9999 直接抛异常）+ L4 动态资源跟踪删除 + trap EXIT 兜底清理
3. **断言自证实践**：L4 通过篡改期望值确认失败路径真实触发（测试本身可信的工程证明）
4. **真实口径统一**：totalExpense 付款口径、编号租户内唯一、付款多合同类型路由——均以 L4 硬断言固化
5. **flow-matrix 颗粒级追溯**（tests/coverage-matrix.md/json）：功能表 46 模块逐功能点 × L1-L5 映射，**737 个流程条目 gap=0 + 23 个审批流全覆盖**（阶段四/五销项，2026-08-15 后历批更新），缺口以追溯矩阵而非记忆驱动
6. **受阻台账机制实跑**：附录 B 规范在 test-maturity-upgrade/tasks.md 台账中多次实跑闭环（k6 不存在的 flag 静默吞错、Docker 缺失、sms:daily 上限打满等），每项均「停→登记→三选项汇报→用户决策回填」，无一次静默降级
7. **E2E 零残留与幂等复用**：api-tests 四遍实跑 + 12 项残留核查全零（2026-08-14）；batch2-4 自包含租户 9999 建链不依赖演示数据态；L4 幂等复用 + trap EXIT 兜底清理

## 3. 顶级标准差距矩阵

| # | 维度 | 顶级标准 | 当前现状（2026-08-24 实测） | 差距 |
|---|------|---------|---------|------|
| 1 | 覆盖率与门槛 | 核心 85%+，CI 强制，增量代码 ≥90% | 22 模块基线棘轮生效（342‰~884‰，coverage-baseline.json，CI 比对只升不降）；门槛已强制（分档，2026-08-24）：根 pom jacoco.min.coverage 属性化（默认 0.80），22 模块按基线取分档值，CI backend 切 verify 真实触发（run 32695969990 全绿）；增量无门槛 | 🟠（v1.0 🔴） |
| 2 | 变异测试 | PIT/Stryker 定期跑，验证测试能否杀死注入缺陷 | PIT 3 模块实测：finance 73%/subcontract 66%/project 61%（2026-08-07，38 个断言强化用例 + 暴露 1 个真实 NPE 风险加固）；未入 CI、未常态化 | 🟠（v1.0 🔴） |
| 3 | 增量测试选择 | 按变更影响面选测试，PR 反馈 <10 分钟 | affected-modules.sh 已落地（commit 9b8edfd）但未集成 CI，仍全量执行 | 🟠 |
| 4 | Hermetic 隔离 | 测试不依赖任何外部状态 | L2 Testcontainers 化；L3/L4/L5 依赖线上真实服务（租户 9999 隔离 + batch2-4 自包含建链缓解） | 🟠 |
| 5 | Flaky 治理 | 隔离队列 + flake score + 自动重试追踪 | flake-check.sh 已落地（commit b8c9032）；自动重试已决策不配置（用户 2026-08-10：实证历次红灯均为真问题）；无 flake score | 🟠 |
| 6 | 契约测试 | Pact/消费者驱动契约 | consistency-audit 静态扫描 + L5 consistency 55 例字段级运行时硬断言 + L3 jq 结构断言（2026-08）；无 Pact 级运行时契约 | 🟡 |
| 7 | 性能/负载 | 预算制 P99 基线 + 持续回归（k6/Gatling） | k6 3 场景入夜间 cron（performance-k6.yml，真实验证码桥无 mock；首轮 run 31322829488 全通）；P95/P99 基线已建并事故复演验证；无预算制回归、未接 PR 门禁 | 🟠（v1.0 🔴） |
| 8 | 安全测试 | SAST + DAST + 依赖漏洞扫描进 CI | CodeQL（java+js 周扫）+ dependency-check 周扫均入 CI；高危逐项处置（升级 9 项 + 豁免 4 项，用户决策 2026-08-10）；CodeQL 303 告警逐项复核；无 DAST | 🟠（v1.0 🔴） |
| 9 | 混沌/故障注入 | 依赖故障演练、限流熔断验证 | 零（当前规模可缓） | 🟠 |
| 10 | 生产验证 | Synthetic 巡检、canary、feature flag | L3/L4/k6 夜间对生产环境实跑 = 巡检雏形；无持续监控/canary/feature flag | 🟠 |
| 11 | 测试数据管理 | Builder/Factory 体系化 | tenant 9999 自包含 + TestDataCleaner 强校验 + 零残留核查闭环；未体系化为 Builder/Factory | 🟡 |
| 12 | 治理 | 测试 owner 制、triage 流程、质量门禁评审 | spec 驱动 + AGENTS.md 成文规则 + 受阻台账实跑 + 三基线棘轮门禁 + 修订记录制；无 owner/triage | 🟡 |

## 4. 成熟度评分

| 维度 | 权重 | v1.0 | v2.0 | 依据（一句话） |
|------|-----|------|------|--------------|
| 架构分层（金字塔形态） | 15% | 8 | **9** | 五层俱全之外新增性能/安全两层，L5 三套全部入 CI |
| 覆盖深度 | 20% | 4 | **7** | 22 模块基线棘轮 + 6 核心模块补测达标 + 前端 106 页面全覆盖；门槛已强制（分档，2026-08-24：22 模块分档值=当前基线，CI verify 真实触发，补测后逐档上调） |
| 隔离与数据纪律 | 10% | 6 | **7** | batch2-4 自包含租户 9999 建链 + 零残留核查闭环；L3/L4/L5 仍非 hermetic |
| 可复现/稳定性 | 10% | 5 | **7** | 三基线只升不降门禁 + flake-check.sh + api-tests 四遍实跑零失败可复现；扣分：无 flake score 自动机制 |
| 自动化与反馈环 | 15% | 6 | **7** | L1/L3/L4/L5 + k6/CodeQL/依赖扫描全入 CI（三项 cron）；L2 手动触发、增量选择未集成 |
| 测试有效性验证 | 10% | 5 | **7** | PIT 3 模块实测 + 38 断言强化 + 变异暴露真实 NPE 风险；未常态化（未入 CI） |
| 非功能测试 | 10% | 1 | **5** | k6 基线 + CodeQL 303 告警复核 + 依赖漏洞 13 项处置均为真实运行；无 DAST/混沌/性能预算制 |
| 治理与文档 | 10% | 7 | **8** | 受阻台账实跑闭环 + 基线实测校准纪律 + 用例矩阵驱动；无 owner/triage |

**加权总分 ≈ 72 / 100**（v1.0 为 54，+18；八维全部上升）

### 4.1 v1.0 → v2.0 趋势对比

| 维度 | v1.0 | v2.0 | 关键事件 |
|------|------|------|---------|
| 架构分层 | 8 | 9 | L5 三套入 CI（2026-08）；k6/codeql/security-scan 三 workflow 上线 |
| 覆盖深度 | 4 | 7 | 阶段一 6 核心模块补测；阶段四 57 个 Service 缺口清零；前端页面级全覆盖（2026-08-15） |
| 隔离与数据纪律 | 6 | 7 | batch2-4 自包含租户 9999；12 项残留核查全零（2026-08-14） |
| 可复现/稳定性 | 5 | 7 | 后端 22 模块 + 前端 3 端基线棘轮；flake-check.sh（b8c9032） |
| 自动化与反馈环 | 6 | 7 | k6 夜间 cron（31322829488）；CodeQL/依赖周扫；三前端 matrix 化入 CI |
| 测试有效性验证 | 5 | 7 | PIT finance 73%/sub 66%/proj 61%（2026-08-07）；NPE 生产加固 |
| 非功能测试 | 1 | 5 | k6 P95/P99 基线回填；依赖升级 9 项 + 豁免 4 项（2026-08-10 决策） |
| 治理与文档 | 7 | 8 | 受阻台账多次实跑；2026-08-13 基线校准纠偏（zw-app/zw-archive） |

定位结论：
- 高于行业平均的幅度进一步扩大（L4 业务链 + 变异 + 性能基线三件套在同类项目中罕见）
- 相当于**成熟中大型工程团队**水平；距顶级（90+）差距收敛为三点：~~门槛未强制~~（2026-08-24 已解决：分档门槛入 CI verify 真触发）、有效性验证未常态化（PIT 未入 CI）、非功能纵深（无 DAST/混沌/性能预算）
- 一句话：**骨架仍是顶级的，血肉已大幅补齐——下一步是把「有门禁」变成「门禁真生效」，把「有基线」变成「持续回归」**

## 5. 剩余短板与改进候选清单（用户已决策 2026-08-24：事项 1/3/4 实施，2/5 暂缓，6 销项）

> v1.0 的三阶段路线（覆盖与门禁 → 有效性验证与非功能 → 效率与顶级门槛）已于 2026-08 全部完成并销项，执行台账归档于 `.kiro/specs/test-maturity-upgrade/tasks.md`。以下为再评估识别的剩余短板，按「事实/选项/建议」登记；决策结果列为用户 2026-08-24 决策及落地状态。

| # | 事项 | 事实 | 选项 | 建议 | 决策结果（用户，2026-08-24） |
|---|------|------|------|------|------|
| 1 | CI 强制 80% 覆盖率门槛 | pom jacoco check 0.80 已绑 verify，但 CI backend 跑 `mvn package` 未触发（2026-08-13 实测校准）；22 模块中 19 个低于 800‰，最低 zw-common 342‰，仅 zw-archive 884/zw-app 850/zw-budget 816‰ 达标 | A. 补测后 CI 切 verify 全量强制 B. 分档门槛（核心模块 80%/其余按基线棘轮） C. 维持现状（仅基线不回退） | B——先分档再逐档棘轮上调，避免一次性红灯阻塞部署 | ✅ 选 B，已实施：根 pom 属性化 + 22 模块分档 + CI 切 verify（commit 714e003，run 32695969990 全绿） |
| 2 | PIT 常态化 | 仅 3 模块一次性实测（finance 73%/sub 66%/proj 61%），mutation profile 就绪但未入 CI | A. 扩至 6 核心模块 + 手动触发 workflow B. 入夜间 cron C. 维持现状 | A——手动触发档先跑通，成本可控后再评估 cron | ⏸ 暂缓：优先级让位于事项 1/3/4，待后续评估再启动 |
| 3 | E2E 残留数据卫生巡检 | 历史 DATA 受阻集中于残留数据（AWARDED 询价/SUBMITTED 报名等历次爆发点）；当前靠人工 12 项残留核查 | A. 残留巡检脚本化入 cron B. 仅强化 TestDataCleaner C. 维持现状 | A——巡检脚本复用 cleaner 逻辑，夜间与 k6 同窗执行 | ✅ 选 A，已实施：tests/residual-patrol.sh（4 项检查，先清后跑）接入 performance-k6.yml 夜间 cron（commit f4e5c56） |
| 4 | 生产验证码开启前置验证 | 待决策 #3 遗留：生产验证码当前关闭；k6 captcha-bridge 已具备真实验证码通过能力 | A. 巡检验证通过后开启 B. 维持关闭 | A——开启前先用 k6 桥对登录链做一轮验证 | ✅ 选 A，已实施：环境变量灰度开启 → 预检 3 判据全过（错码被拒/一次性消费/不可重放）+ k6 三场景带码复跑 100% → yml 固化（5a67613→7793ca2，顺带修复 run-k6.sh 桥 JSON 解析潜伏缺陷） |
| 5 | 全量套件提速 | 全链 run 32647849931 约 55 分钟（L3 25/25 + L4 26/26 + L5 全绿 + k6） | A. L2 容器缓存/并行分片 B. 集成 affected-modules.sh 做测试选择 C. 维持现状 | B——脚本已就绪（commit 9b8edfd），集成即得提速收益 | ⏸ 暂缓：当前耗时可接受，优先把门禁/巡检/验证码三项固化收益落袋 |
| 6 | flaky score 机制 | 当前仅人工 + flake-check.sh；自动重试已决策不配置（用户 2026-08-10，实证红灯均为真问题） | A. 建 flake score 统计 B. 维持现状 | B——无纯环境抖动实证案例，出现时再引入 | ⏹ 销项：已评估，主动不做（无实证场景，与红灯必真原则冲突） |

## 6. 附录 A：覆盖率基线（v2.0 校准，与 `tests/coverage-baseline.json` 一致，2026-08-24 读取）

后端 22 模块（LINE，千分比，按基线降序）：

| 模块 | 基线‰ | ≥80% | 模块 | 基线‰ | ≥80% |
|------|------|------|------|------|------|
| zw-archive | 884 | ✅ | zw-machine | 585 | ❌ |
| zw-app | 850 | ✅ | zw-project | 572 | ❌ |
| zw-budget | 816 | ✅ | zw-workflow | 557 | ❌ |
| zw-contract | 794 | ❌ | zw-file | 517 | ❌ |
| zw-finance | 779 | ❌ | zw-message | 505 | ❌ |
| zw-material | 734 | ❌ | zw-basedata | 498 | ❌ |
| zw-purchase | 732 | ❌ | zw-dashboard | 457 | ❌ |
| zw-labor | 715 | ❌ | zw-system | 417 | ❌ |
| zw-subcontract | 672 | ❌ | zw-common | 342 | ❌ |
| zw-hr | 671 | ❌ | | | |
| zw-security | 656 | ❌ | | | |
| zw-site | 634 | ❌ | | | |
| zw-tender | 623 | ❌ | | | |

前端 3 端（`tests/frontend-coverage-baseline.json`）：zw-insight-web 665‰ / zw-insight-app 875‰ / zw-supplier-portal 857‰。

> 基线的可执行版本为两个 baseline JSON（git 追踪，CI 比对只升不降）。本表为人类可读快照，两者不一致时以 JSON 为准。
> ⚠️ **校准记录**：① 2026-08-13 实测发现 zw-app/zw-archive 基线登记为 0 与实际不符（实际 850‰/175‰），已修正；同时确认 pom jacoco check 0.80 绑定 verify 而 CI 跑 package 未触发，实际生效门槛=基线只升不降。② 2026-08-24 本表按基线 JSON 全量刷新（v1.0 表为 2026-07-31 快照，已废弃）。

## 7. 附录 B：测试受阻汇报与记录规范

### B.1 原则

**测试因环境或其他原因无法执行时，禁止静默跳过、禁止标记为通过、禁止用假数据/mock 降级替代**（与项目规则"真实接口真实流程"一致）。必须显式记录并向用户汇报。

### B.2 受阻分类枚举

| 代码 | 分类 | 典型例子 |
|------|------|---------|
| ENV | 环境 | 本地 Docker 未启动、JaCoCo 中文路径问题、Node 版本不匹配 |
| DEP | 依赖 | k6/工具安装失败、镜像拉取超时、私服不可达 |
| NET | 网络 | 测试服务器不可达、SSH 超时 |
| CRED | 凭证 | pem 权限被重置、token 过期、GitHub secrets 缺失 |
| DATA | 数据 | 测试租户未初始化、BPMN 未部署、种子数据缺失 |
| OTHER | 其他 | 上述均不适用的情况 |

### B.3 强制流程（AI 代理与人均适用）

1. **立即停止**该层/该任务执行，不继续假装推进
2. **登记台账**：在 `.kiro/specs/test-maturity-upgrade/tasks.md` 末尾"受阻项登记台账"表追加一行
3. **向用户汇报**，内容包含：受阻原因、影响范围、三个选项（修复环境 / 延期该项 / 缩减范围并记录）
4. **等待用户决策**后回填台账"处置决策/决策人"列
5. 禁止 AI 自行决定降级方案（如用 mock 数据替代真实接口验证）

### B.4 汇报格式模板

```
【测试受阻报告】
日期：YYYY-MM-DD
层级：L1/L2/L3/L4/L5
测试项：<具体测试或模块>
分类：ENV/DEP/NET/CRED/DATA/OTHER
原因：<一句话说清>
复现命令：<可复现的命令>
影响范围：<哪些结论暂时无法验证>
建议处置：A. 修复环境（方案：…） B. 延期 C. 缩减范围（理由：…）
```

### B.5 历史受阻案例（供参照）

| 日期 | 分类 | 案例 | 处置 |
|------|------|------|------|
| 2026-07-31 | ENV | Windows 中文路径「5个项目」导致 JaCoCo agent 无法写 exec 文件 | 已修复：destFile 重定向 ASCII 路径；根因写入记忆 |
| 2026-07-30 | CRED | pem 密钥 ACL 被重置导致 SSH 拒绝 | 已修复：icacls 重置权限 |
| 2026-07-30 | ENV | PowerShell→SSH 传 `\r` 蜕变为字母 r 破坏脚本 | 已修复：写服务器侧脚本文件执行；根因写入记忆 |

---

## 修订记录

| 日期 | 版本 | 内容 |
|------|------|------|
| 2026-08-05 | v1.0 | 初版：五层盘点、差距矩阵、54 分评分、三阶段路线、受阻规范 |
| 2026-08-05 | v1.1 | 阶段一启动：spec 三件套落地、基线 JSON + CI 不回退守护、zw-purchase 补测达标 70.5%（8 测试类 37 用例）、verify 门槛验证通过；受阻台账 #1：本机无 Docker 导致 L2 实跑受阻 |
| 2026-08-05 | v1.2 | 阶段一完成：六核心模块全部达标 ≥60%（labor 70.3%/contract 62.0%/finance 64.8%/budget 71.8%/material 72.0%），新增约 30 个测试类；顺带修复 2 个真实缺陷：① JDK21 下 JaCoCo 与 Mockito agent 共存需 -XX:+EnableDynamicAgentLoading（surefire argLine） ② commons-io 2.11 与 easyexcel 3.3.4 不兼容导致 Excel 导出 NoClassDefFoundError（钉住 2.16.1） |
| 2026-08-14 | v1.3 | 前端测试修复批次（评估 9 项问题闭环）：① L5 口径修正为 43 spec/3 project（mock 已删）；② consistency 接口 500 静默通过改硬失败（finalizeModuleConsistency afterAll 汇总断言，保全页覆盖）；③ 三前端单测全部入 CI（frontend-test matrix 化）+ 前端覆盖率度量与「只升不降」基线门禁（tests/frontend-coverage-baseline.json，实测 web 36‰/app 45‰/portal 82‰）；④ app @dcloudio 依赖版本笔误修正（40101→40105，原版本在任何 registry 不存在）并首次生成 lockfile + 移除死依赖 uni-automator；⑤ E2E 登录默认密钥路径仓库相对化 |
| 2026-08-14 | v1.4 | 前端业务模块测试用例枚举矩阵落地：tests/frontend-test-case-matrix.md（21 模块/104 页面源码逐页深读实证，1,123 个用例：页面级 1,048 + 跨模块集成 75，含现有覆盖映射与 20 项源码级盲点汇总 + P0-P3 落地优先级）；实测整体已有覆盖≈32%，财务域最低（20.7%）；盲点含源码缺陷（模板假上传/薪资筛选失效/BOQ 大小写误拒等），需修复流程处理而非仅测试钉住 |
| 2026-08-24 | v2.0 | 只读再评估（基线 commit 3c78870，最近全链绿 run 32647849931）：五层盘点按实测刷新（后端 322 测试类/L3 25 脚本/L4 26 阶段/L5 63 spec 三套入 CI，新增性能与安全两层）；差距矩阵重评（覆盖率/变异/性能/安全四项 🔴→🟠）；评分 54→72（+18，八维全升）；§5 改为剩余短板与改进候选清单 6 项（待用户决策）；附录 A 按 baseline JSON 全量校准 |
| 2026-08-24 | v2.1 | 改进实施落地（用户 2026-08-24 决策）：① 事项 1-B 分档门槛真生效——根 pom jacoco.min.coverage 属性化（默认 0.80）+ 22 模块按基线取分档值 + CI backend 切 verify（commits 714e003，run 32695969990 全绿）；② 事项 3-A 残留巡检 tests/residual-patrol.sh（4 项检查，report/--clean 双模式，Flowable 残留只报告不 DELETE）接入 performance-k6.yml 夜间 cron 先清后跑（f4e5c56）；③ 事项 4-A 生产验证码开启——compose 环境变量灰度（5a67613）→ 预检三判据全过 + k6 三场景带码复跑 100% → application-dev.yml 固化并撤环境变量（7793ca2），开启过程暴露并修复 run-k6.sh 桥 JSON 解析不容空格潜伏缺陷（开关关闭期被假象掩盖）；事项 2/5 暂缓，事项 6 销项（已评估，主动不做：无实证场景，与红灯必真原则冲突）；评分仍为 v2.0 口径 72 分，待下次再评估重算 |
