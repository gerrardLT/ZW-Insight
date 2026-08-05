# Design Document

## Overview

本设计文档记录测试成熟度升级三阶段的关键技术决策与选型理由。总体原则：**复用已有基建优先**（Testcontainers、verify-base.sh、consistency-audit 均已存在）、**门禁渐进收紧**（先不回退、后提门槛）、**环境受阻显式化**（任何无法执行的测试必须登记汇报）。

## Architecture

```
质量门禁演进路线：

现状                      阶段一                    阶段三
mvn package              mvn package               mvn verify
(测试跑+JaCoCo报告)   →   + 基线JSON不回退比对   →   + jacoco check 0.80
(无强制门槛)              (只升不降)                (核心模块强制)

测试有效性演进：
覆盖率数字（可被"空断言测试"刷高）
    ↓ 阶段二引入
PIT 变异杀死率（证明断言真能抓 bug）
```

## Key Decisions

### D1. 覆盖率守护：基线 JSON vs PR diff 覆盖率 Action

**决策**：采用 `tests/coverage-baseline.json` 基线比对（CI 内联脚本），不采用 PR diff Action（如 Madrapps/jacoco-report）。

**理由**：
- 本仓库无 PR 流程（workflow 仅 `push main` + `workflow_dispatch` 触发），diff Action 无挂载点
- 基线方案不依赖第三方 Action（无断供风险），比对逻辑 20 行内联脚本可完全掌控
- 缺点：不限制"新增代码覆盖率"，仅防存量回退——接受此局限，阶段二 PIT 补位验证增量质量

**实现**：
- 基线格式：`{"zw-purchase": 103, "zw-labor": 207, ...}`（行覆盖率千分比整数，容忍 0.1% 波动）
- CI 步骤：解析各模块 `target/site/jacoco/jacoco.csv` 的 LINE_COVERED/LINE_MISSED → 千分比 → 与基线比对，任一模块下降则 `exit 1`
- Windows 中文路径坑：CI 为 ubuntu 不受影响；本机手动采集须 `-Djacoco.destFile=<ASCII路径>`

### D2. L2 hermetic 化：复用 Testcontainers，不新建

**决策**：不新建任何基建，直接使用已有 `zw-app/src/test/java/com/zwinsight/integration/BaseIntegrationTest.java`（MySQLContainer 8.0 + Redis + `init-test-schema.sql`）。

**理由**：
- 该基类含 `@EnabledIfDockerAvailable`（DockerAvailableCondition），无 Docker 环境自动跳过——CI/他人环境不炸
- H2 替代方案被否决：方言差异大（`init-h2-schema.sql` 仅为备用），违背"真实流程"原则

**新增集成测试落位**：zw-purchase/zw-material 的新集成测试放各自模块 `src/test/java/.../integration/`，继承基类或复用同等 Testcontainers 配置，数据一律 tenant_id=9999 + @AfterAll 清理。

### D3. PIT 变异测试选型与隔离

**决策**：`org.pitest:pitest-maven:1.15.x` + `pitest-junit5-plugin:1.2.x`，放入 `-Pmutation` profile，不绑定默认构建。

**理由**：
- PIT 是 Java 生态事实标准（Stryker 主攻 JS/TS）
- 变异测试耗时大（分钟~小时级），进主构建会拖垮反馈环，必须 profile 隔离
- 首轮范围限定 zw-finance/zw-subcontract/zw-project（缺陷密度最高+测试最需验证的三模块）

**目标**：杀死率 ≥70%；低于阈值的测试类逐个补断言或在台账登记豁免理由。

### D4. k6 性能基线：本地脚本 + 手动执行，不进 CI

**决策**：`tests/performance/` 下 3 个 k6 脚本（login.js/page-query.js/payment-submit.js），手动执行建立基线，不挂 CI。

**理由**：
- CI 每次推送对真实服务器加压，会污染生产同库环境且结果不稳
- 基线用途是"大版本前对比"，低频手动执行足够
- 执行约束写入脚本头注释：夜间低峰、并发≤20、单次≤5 分钟、目标租户仅用 9999 或专用测试账号

**备选**：k6 无法安装时 `docker run --rm -i grafana/k6 run -`。

### D5. 安全扫描双通道

**决策**：
1. 新建 `.github/workflows/codeql.yml`：java + javascript-typescript 双语言，push main 与每周定时（cron）触发
2. 根 pom 增 `org.owasp:dependency-check-maven:10.x`，`-Psecurity` profile 隔离（NVD 数据源首次下载慢）

**首轮处置策略**：只处理 error/高危级；中低危登记台账逐步消化，防止告警淹没。

### D6. 受阻台账位置：spec tasks.md

**决策**：受阻登记台账固定在 `.kiro/specs/test-maturity-upgrade/tasks.md` 末尾表格，不放 `tests/reports/`。

**理由**：`.gitignore` 忽略 `tests/reports/`（测试产物目录），台账必须 git 追踪且跨会话可读。

### D7. 增量测试选择实现

**决策**：`tests/affected-modules.sh`——`git diff --name-only HEAD~1`（或指定 base）→ 路径前缀映射模块名 → 输出逗号分隔的 `mvn -pl` 参数；`run-all-tests.sh` 增 `--affected` 参数调用。

**映射规则**：`zw-insight-server/{module}/` → 该模块 + zw-common 变更触发全量（公共模块影响面广）。

## Testing Strategy

- 本升级自身的质量保证：基线比对机制需自证（人为调低某模块覆盖率推 CI 应变红）
- 每个补测模块验收：`mvn -pl <m> -am test` 全绿 + 覆盖率 ≥60% + 基线 JSON 更新
- 阶段二 PIT 首跑结果、k6 基线数据、CodeQL 高危清单均回填 tasks.md（可追溯）

## Risks and Mitigations

| 风险 | 缓解 |
|---|---|
| 补测工作量大，跨会话上下文丢失 | tasks.md 按模块原子化勾选；基线 JSON 保证进度不回退；TESTING-MATURITY.md 修订记录留痕 |
| Docker 未装/未启导致 L2 无法实跑 | `@EnabledIfDockerAvailable` 保证不炸；实跑任务按受阻机制登记汇报，不静默 |
| k6 Windows 安装失败 | 受阻登记；备选 docker run grafana/k6 |
| CodeQL 首跑告警淹没 | 首轮只处理高危，其余登记台账 |
| 基线比对误报（舍入波动） | 千分比整数 + 容忍 1‰ |
| PIT 耗时超预期 | profile 隔离；首轮仅 3 模块；可 `-Dthreads=4` 加速 |

## Alternatives Considered（已否决）

1. **PR 级 diff 覆盖率 Action**：无 PR 流程，无挂载点（见 D1）
2. **H2 替代 Testcontainers**：方言差异 + 违背真实流程原则（见 D2）
3. **CI 立即强制 80% verify 门槛**：当前 20-62%，立即全红阻断部署
4. **重写测试体系**：评估结论是骨架合格，问题在纵深，增量补齐即可
5. **Stryker 做 Java 变异**：Stryker 主攻 JS/TS，Java 生态选 PIT
6. **性能测试进 CI 常跑**：对真实服务器持续加压且结果不稳（见 D4）
