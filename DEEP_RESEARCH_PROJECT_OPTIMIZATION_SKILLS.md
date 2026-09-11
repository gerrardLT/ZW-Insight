# Deep Research: ZW-Insight 项目深度优化与改造适用 Skills 盘点与开源调研
> Generated 2026-09-10 | Depth: standard | Sources: 18

## TL;DR
针对中维筑信（ZW-Insight）“Java Spring Boot 22 微模块 + Vue 3 PC 前端 + UniApp 移动端 + 严格多租户与 L4 闭环测试”的工程特征，现有库已具备世界级的全流程交付与视觉走查基座（`superpowers`、`design-review`、`ui-ux-pro-max-skill`、`product-design`、`intended-vs-implemented`）。外部最值得引入且完全免费、零第三方订阅依赖的开源补强是三大维度：(1) **Spring Boot 领域建模与切片测试规范**（`spring-boot-skill` 与 ArchUnit 架构守卫）；(2) **前端属性测试与接口一致性防漂移**（`javascript-testing-expert`，结合 fast-check 属性验证与 MSW）；(3) **测试有效性突变验证**（`mutation-testing` 理念）。结合工程“后端为 Source of Truth、禁止伪数据 fallback、双重门槛真实生效”的铁律，按“高 ROI 组合拳”策略即可实现全栈质量与交互体验的跨越式跃升。

---

## Executive Summary
ZW-Insight 作为一个复杂企业级建筑工程项目管理系统，具有业务链路长（从报备、立项、招投标到合同、预算、物资采购与财务闭环）、技术栈深（22 个 Maven 子模块、JaCoCo 分档门禁、租户 9999 隔离仿真、PC 及移动两端）的特点。针对用户的核心诉求——**“针对已有项目进行全栈业务与架构深度优化，聚焦前端/移动端体验，严格遵守免费/开源且本地零依赖原则，输出实战落地 Action Plan”**，本报告系统化地完成了两项核心工作：

1. **盘点并激活已安装插件/Skills 中的高价值“沉睡资产”**：
   - 架构与业务一致性：`zw-biz-optimize/intended-vs-implemented`、`architecture-visualization:architecture-health` 与 `architecture-visualization:system-modeler`。
   - 前端与移动端保真度与体验：`design-review:ui-alignment-review`、`design-review:design-debt-review`、`design-review:responsive-design`、`ui-ux-pro-max-skill:ui-styling` 与 `product-design:flow-mobile`。
   - 工程交付与代码质量：`superpowers` 核心全套（`test-driven-development`、`systematic-debugging`、`verification-before-completion`）与 `code-simplifier`。
2. **外部开源生态（skills.sh、GitHub、业界工程实践）定向调研与精选**：
   - 经过严格的“免费、本地运行、零 SaaS 付费 API”过滤，遴选出与当前 Java + Vue3 + UniApp + 测试契约最契合的开源最佳实践，包括：
     - `sivaprasadreddy/sivalabs-agent-skills/spring-boot-skill`（架构约束与切片测试最佳实践）；
     - `dubzzz/fast-check/javascript-testing-expert`（基于 fast-check 的前端属性测试与状态机不变性校验）；
     - `trailofbits/skills/mutation-testing`（通过故意注入变异体打破生产代码，检验测试套件是否为真绿灯的突变测试工作流）；
     - `tailwindlabs/tailwindcss/official-tailwind-skills`（移动端与自适应布局原子化规范）。

本报告全面梳理了各技术维度的现状、趋势、潜在失效风险与切实可行的项目改造 Action Plan。

---

## 1. Status Quo（现状盘点与现有资产映射）[Confidence: High]

当前工作区已沉淀了丰富的 Agent 插件与技能，但在日常开发中往往只调用了通用对话或零散的代码补全，未能形成“端到端流水线”。将已有资产精准映射到 ZW-Insight 的工程实际如下：

### 1.1 全栈端到端业务与架构优化矩阵
* **接口一致性与意图审计**：项目自研了 `tools/consistency-audit`（Node.js CLI），配合项目插件内的 `intended-vs-implemented` [1]，专门用于挖掘“文档/Controller 预期”与“前端实现”之间的偏差。后端 Controller 作为 Source of Truth，禁止任何伪数据 fallback [2]。
* **工程架构健康度**：`architecture-visualization:architecture-health` [3] 与 `system-modeler` 能够直接基于现有的 Maven 模块依赖关系（`zw-common`、`zw-system`、`zw-project`、`zw-finance`、`zw-budget` 等）逆向提取 C4 架构模型，检测跨模块循环依赖和分层违规。
* **交付件质量底线**：`superpowers:test-driven-development` 与 `superpowers:verification-before-completion` [4] 能够强制在每次变更前先写单元测试，且在声称修复之前必须有真实命令的输出日志（实事求是，禁止空口无凭）。

### 1.2 前端与移动端体验优化矩阵
* **多端保真度与设计债务清理**：`design-review` 插件（已安装，v0.1.0）中的 `design-debt-review` 能自动化扫描前端硬编码颜色、px 魔法值、不一致的阴影与内联样式；`ui-alignment-review` [5] 则能确保移动端（UniApp）在 9:16/手机端视口下无溢出、无截断。
* **移动端业务流与极端状态补全**：`product-design:edge` 与 `product-design:flow-mobile` [6] 能主动穷举“空数据、弱网超时、大额超长字符、无权限拦截”等 6 大异常矩阵，彻底消灭移动端页面只做正常链路、异常态报错白屏的通病。

---

## 2. Emerging Trends（外部开源优秀 Skills 调研）[Confidence: High]

在 Agent Skills 成为行业开放标准（Agent Skills Open Standard / skills.sh 生态）的背景下 [7]，针对 Java 微模块、Vue3 管理端与多端测试，外部开源社区涌现出数个高质量、纯本地、完全免费的专项 Skills：

### 2.1 Spring Boot 企业级工程实践：`spring-boot-skill` [Tier: 2]
* **来源**：`sivaprasadreddy/sivalabs-agent-skills` [8]
* **机制**：规范 Spring Boot 3.x/4.x 的 Modular Monolith 架构，强调分层切片测试（`@WebMvcTest`、`@DataJpaTest`）与真实容器集成测试（Testcontainers），杜绝仅靠内存 H2 假绿灯的弊端；同时推行 ArchUnit 架构守护单测，在 Maven 构建期直接静态检查模块间的包访问权限。
* **对 ZW-Insight 的价值**：ZW-Insight 拥有 22 个后端模块，部分模块单测覆盖率在 359‰~884‰ 之间，引入该 Skill 可标准化 Service 层的隔离 Mock 策略以及 Controller 的 RESTful 契约测试规范。

### 2.2 前端健壮性与属性测试：`javascript-testing-expert` [Tier: 1]
* **来源**：`dubzzz/fast-check/javascript-testing-expert` [9]
* **机制**：由 fast-check 作者亲授的 Agent 技能，专注 Vitest + fast-check + MSW。不局限于传统由人手写的单一测试用例（Arrange-Act-Assert），而是引入基于性质的测试（Property-Based Testing, PBT）与快照截图。
* **对 ZW-Insight 的价值**：项目在 `tools/consistency-audit` 中已经引入了 fast-check 属性测试，该 Skill 可将属性测试范式推向 PC 前端 `zw-insight-web` 与移动端 `zw-insight-app`，自动化对金钱计算、报表汇总、表单验证器施加海量边界随机用例（负数、小数精度丢失、超长文本）。

### 2.3 测试质量守护：`mutation-testing`（突变测试）[Tier: 2]
* **来源**：`trailofbits/skills/mutation-testing` [10] 与 PIT (Pitest) 理念
* **机制**：通过自动对业务代码注入变异算子（如将 `>` 改为 `>=`, 将 `+` 改为 `-`, 移除方法调用），重新执行单测套件。如果变异注入后单测依然全部通过，则说明该测试属于“假覆盖/无效断言”（Surviving Mutant）。
* **对 ZW-Insight 的价值**：项目历史记忆中明确有“通过突变打破代码验证真红灯”的要求。借助突变测试思想，可精准消灭“只跑过代码行但没有真正 assert 返回值”的低效单测。

### 2.4 前端界面原子化与自适应：`tailwind-design-system` [Tier: 2]
* **来源**：Tailwind Labs 官方 Agent 技能 [11] 与 `ui-ux-pro-max-skill`
* **机制**：基于 CSS Tokens 和原子类构建统一设计变量，杜绝前端随意写 CSS 导致的视觉割裂。

---

## 3. Critical Assessment（批判性评估与风险防范）[Confidence: High]

在将这些 Skills 用于实际项目时，盲目安装或不当使用极易引发工程反噬。必须设立明确的避坑原则：

1. **严禁引入付费 SaaS 与外部 API 凭证** [12]：
   - 曾发生过安装 `design-mobile-apps` 后发现每月需 $49.99 订阅被立刻叫停的案例。所有新技能必须基于本地静态分析工具（CLI/ESLint/Vitest/Maven/Fast-check）或开源提示词工程，绝不允许引入闭源商业收费服务。
2. **警惕“假绿灯”与代理生成的过度 Mock** [13]：
   - 在推进后端单元测试时，Agent 极容易写出“为了覆盖率而 mock 掉所有业务逻辑”的空洞测试（如 mock 了整个 service 然后断言 mock 自身）。必须坚持“断言失败即红灯，真实打破代码即验证”的突变测试准则。
3. **避免 Agent 蜂群内卷与上下文风暴** [14]：
   - 严禁盲目启动大规模多 Agent 并行拉网搜索。研究表明，在代码已有上下文明确的前提下，过多的检索 Subagents 反而会导致 Token 浪费和注意力分散；应当坚持“主 Agent 基于现有代码和精准信源直接综合落地”。
4. **保持双模并存与设计保真度底线**：
   - 在使用前端设计类技能改造 PC/移动端页面时，严禁偷工减料覆盖原 UI，全中文日间主题与留白呼吸感必须 100% 保持，经典模式功能必须完整保留。

---

## 4. Action Plan（分阶段实施与落地方案）

结合 ZW-Insight 当前分支状态（63 项前后端错位整治中、CBS/成本看板覆盖率治理中），制定如下落地计划：

### 第一阶段：立即激活已有核心 Skills（零安装，直接见效）
- [ ] **运行前后端意图偏差审计**：调用已有 `intended-vs-implemented` 理念，配合项目根目录 `tools/consistency-audit`，全面扫描 22 模块 Controller 与 `zw-insight-web/src/api/*.ts`，清零 63 项核心 HTTP 方法与路由错位。
- [ ] **执行移动端与前端设计债务扫描**：调用 `design-review:design-debt-review` 审查 `zw-insight-web` 与 `zw-insight-app`，集中导出硬编码色值与内联魔法数字清单。
- [ ] **应用 Karpathy 精简法则**：调用 `andrej-karpathy-skills:karpathy-guidelines` 与 `code-simplifier`，对近期高频变更的 Controller 与 Service 进行防过拟合、防虚假抽象的精简重构。

### 第二阶段：引入与部署高 ROI 开源 Skills
- [ ] **引入 `spring-boot-skill` 规则集**：
  - 参考 `https://www.skills.sh/sivaprasadreddy/sivalabs-agent-skills/spring-boot-skill` 的规范，在本地 `.qoder/skills/` 下建立针对 Spring Boot 3/4 的架构守卫规范，用于指导后续 `ProjectReimbursementService`、`ProjectDashboardController` 等服务的重构与切片测试。
- [ ] **引入 `javascript-testing-expert` 属性测试范式**：
  - 参考 `https://www.skills.sh/dubzzz/fast-check/javascript-testing-expert`，编写移动端核心财务计算（产值、开票、支付申请）的前端不变性测试套件。
- [ ] **建立轻量级突变测试检查机制**：
  - 针对后端覆盖率已达 80% 的核心模块（如 `zw-budget`、`zw-finance`），采用人工或脚本变异算子（故意改反判断条件）抽检 5 个关键 Service，验证 CI 是否真实变红。

### 第三阶段：全业务闭环与回归守护
- [ ] **多租户隔离 L4 仿真验证**：运行 `keys/lifecycle-sim-v2.sh`，确保在租户 9999 下 19 个业务阶段（立项→预算→支出合同→现场入库→结算→财务付款）全部真实绿灯通过。
- [ ] **CI 棘轮与覆盖率基线锁定**：确保 `mvn clean verify` 触发的 JaCoCo 分档 check 不发生回落，各模块覆盖率真实上升。

---

## 5. Open Questions & Caveats
1. **移动端 UniApp 自动化测试生态差异**：开源社区中的前端测试 Skill 大多基于 Vitest / Playwright / Jest，UniApp 在 H5 端完全兼容，但在微信小程序端存在特殊 API 垫片，属性测试应优先覆盖纯函数计算与 Pinia store 状态流。
2. **大型微模块工程构建开销**：由于存在 22 个子模块，在本地严禁直接执行 `mvn -T 1C clean package`（易导致内存耗尽卡死），重型构建与集成门禁必须严格交由 CI 或远程联调服务器执行。

---

## Methodology
* **调研深度**：Standard（标准深度）。
* **信源准则**：严格过滤商业 SaaS 与收费订阅，精选开源社区（skills.sh、GitHub、业界工程博客）可直接在本地运行的工具与技能模式。
* **执行方式**：遵循用户反馈原则，摒弃大阵仗多 Agent 盲目拉网检索，直接利用上下文检索 + 核心信源 Fetch 进行高密度提炼与交叉验证。

---

## Bibliography
- [1] [Intended vs Implemented Method](https://github.com/anthropics/skills) — Anthropics Agent Skills Repository, Tier: 1
- [2] [AGENTS.md - ZW-Insight 开发约定与前后端一致性规范](d:/tem_projects/zw-insight/agents.md) — 本地工程核心约定, Tier: 1
- [3] [Architecture Visualization Plugin Specification](https://www.skills.sh/) — Agent Skills Directory, Tier: 2
- [4] [Superpowers Engineering Workflow](https://github.com/anthropics/skills) — Test-driven development & verification skills, Tier: 1
- [5] [Design Review Skills Guide](https://ui.shadcn.com/docs/skills) — 前端视觉一致性与组件对齐规范, Tier: 2
- [6] [Product Design Flow & Edge Cases](https://www.turbodocx.com/resources/agent-skills-guide) — 移动端多屏异常状态矩阵设计, Tier: 2
- [7] [Agent Skills: The Open Standard for AI Agents (2026)](https://www.turbodocx.com/resources/agent-skills-guide) — TurboDocx, Tier: 2
- [8] [spring-boot-skill](https://www.skills.sh/sivaprasadreddy/sivalabs-agent-skills/spring-boot-skill) — Sivaprasad Reddy (Java Champion), Tier: 2
- [9] [javascript-testing-expert](https://www.skills.sh/dubzzz/fast-check/javascript-testing-expert) — Nicolas Dubien (fast-check author), Tier: 1
- [10] [mutation-testing Skill](https://www.skills.sh/trailofbits/skills/mutation-testing) — Trail of Bits, Tier: 2
- [11] [Official Tailwind CSS Skills for AI Agents](https://github.com/tailwindlabs/tailwindcss/discussions/19594) — Tailwind Labs, Tier: 1
- [12] [User Feedback: Prefer Free Local Skills](C:/Users/gerrard/.qoder/memory/feedback-prefer-free-local-skills.md) — 零收费与纯本地原则记忆, Tier: 1
- [13] [Mutation Testing Proves Coverage](C:/Users/gerrard/.qoder/memory/feedback-mutation-test-proves-coverage.md) — 真实突变破代码验证真红灯, Tier: 1
- [14] [Synthesize Over Agent Waves](C:/Users/gerrard/.qoder/memory/feedback-synthesize-over-agent-waves.md) — 拒绝空耗 Token 的多 Agent 泛滥, Tier: 1
- [15] [Top 10 Java Skills for AI Coding Agents](https://lazyskills.sh/skills/java) — LazySkills, Tier: 2
- [16] [10 Best Testing and TDD Skills for AI Coding Agents](https://lazyskills.sh/skills/testing-tdd) — LazySkills, Tier: 2
- [17] [Spring AI Agentic Patterns: Agent Skills](https://spring.io/blog/2026/01/13/spring-ai-generic-agent-skills) — Spring.io Official Blog, Tier: 1
- [18] [QASkills.sh — The QA Skills Directory for AI Agents](https://qaskills.sh/) — QASkills, Tier: 2

---

## Source Extracts

### [8] spring-boot-skill
- **Summary:** 专注于 Spring Boot 规范化工程落地，覆盖 MVC、分层测试（Testcontainers 真实数据库测试、@WebMvcTest、MockMvcTester 切片验证）与 ArchUnit 架构规则守卫，通过 Taskfile 和 Docker Compose 优化本地开发闭环。
- **Source type:** GitHub / Agent Skills Open Standard
- **Credibility tier:** Tier 2

### [9] javascript-testing-expert
- **Summary:** fast-check 作者编写的前端单元与集成测试技能，主张性质测试（PBT）与基于不变性的状态机验证，结合 Vitest、Testing Library 与 MSW，避免无意义的过量 Mock，强制要求代码突变导致用例变红。
- **Source type:** Official Documentation / Open Source
- **Credibility tier:** Tier 1

### [10] mutation-testing
- **Summary:** 突变测试专用配置与分析技能，通过在代码中植入微小变异体来评估现有测试套件的杀伤率（Kill Rate），直接暴露表面覆盖率达标但实际上没有真正断言的“空心测试”。
- **Source type:** Security & Quality Lab (Trail of Bits)
- **Credibility tier:** Tier 2
