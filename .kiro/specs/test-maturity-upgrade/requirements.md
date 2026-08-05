# Requirements Document

## Introduction

基于 2026-08-05 的测试体系成熟度评估（`tests/TESTING-MATURITY.md`），当前体系加权得分约 54/100：五层金字塔结构完整、数据纪律严格（骨架顶级），但覆盖深度（10.3%~62.5%）、测试有效性验证（无变异测试）、非功能测试（性能/安全空白）三项纵深不足（血肉中级）。本需求文档覆盖测试体系升级的 9 项需求，目标 12 个月内从 54 分提升至 75 分以上。

## Glossary

- **Mutation_Testing**：变异测试，向字节码注入人工缺陷（变异体），用现有测试套件验证能否"杀死"变异体，杀死率反映测试断言的真实有效性
- **Diff_Coverage**：增量覆盖率，仅统计本次变更代码行的覆盖率，防止新增代码无测试
- **Hermetic_Test**：密封测试，不依赖任何外部状态（网络/共享数据库），可在任意环境重复执行
- **Flaky_Test**：不稳定测试，代码未变时结果随机通过/失败
- **P99_Budget**：性能预算，核心接口第 99 百分位响应时间的承诺上限
- **Coverage_Baseline**：覆盖率基线，git 追踪的 JSON 文件，CI 比对确保覆盖率只升不降
- **Blocked_Ledger**：受阻台账，测试因环境等原因无法执行时的强制登记记录（位于本 spec tasks.md）

---

## Requirements

### Requirement 1: 测试成熟度正式评估文档

**User Story:** 作为项目负责人，我需要一份数字可复现的正式评估文档，以便了解测试体系真实水平并跟踪改进进度。

#### Acceptance Criteria

1. WHEN 打开 `tests/TESTING-MATURITY.md` THEN 包含评估方法、五层盘点、12 维差距矩阵、8 维加权评分、三阶段路线、覆盖率基线表、受阻规范七个部分
2. WHEN 核对文档数字 THEN 全部可通过实测命令复现（文件计数、`mvn test` + JaCoCo 采集）
3. WHEN 阶段任务完成 THEN 文档对应章节与修订记录同步更新

### Requirement 2: 覆盖率补齐与不回退门槛

**User Story:** 作为开发者，我需要核心模块覆盖率提升至 60% 且 CI 保证不回退，以便重构时有真实的安全网。

#### Acceptance Criteria

1. WHEN 阶段一完成 THEN zw-purchase/zw-labor/zw-contract/zw-finance/zw-budget/zw-material 六模块行覆盖率 ≥60%
2. WHEN 提交代码使任一模块覆盖率低于 `tests/coverage-baseline.json` 记录值（容忍 0.1% 波动） THEN CI backend job 失败
3. WHEN 补测提升覆盖率 THEN 更新 coverage-baseline.json 后 CI 以新基线为准（只升不降）
4. THE SYSTEM SHALL 在测试无法采集覆盖率时（如环境问题）按受阻机制登记，不静默跳过

### Requirement 3: L2 集成测试扩容与实跑验证

**User Story:** 作为开发者，我需要在本地 Docker 环境真实跑通 Testcontainers 集成测试，以便验证审批流、数据权限等关键路径的端到端正确性。

#### Acceptance Criteria

1. WHEN 本地 Docker 可用 THEN `mvn test -Dtest="com.zwinsight.integration.*"` 全绿（zw-app 存量 8 个集成测试）
2. WHEN 阶段一完成 THEN zw-purchase 与 zw-material 各有 ≥1 个新增集成测试（tenant_id=9999 纪律）
3. WHEN Docker 不可用 THEN `@EnabledIfDockerAvailable` 自动跳过且不阻断构建，但实跑验证任务须按受阻机制登记汇报

### Requirement 4: 覆盖率口径统一

**User Story:** 作为维护者，我需要 AGENTS.md、tests/README.md、pom.xml 三处覆盖率口径一致，以便新人不被互相矛盾的文档误导。

#### Acceptance Criteria

1. WHEN 查阅三处文档 THEN 均表述为"当前 CI 门槛 60%（pom jacoco check，verify 阶段），80% 为阶段三目标"
2. WHEN tests/README.md 描述 L4 THEN 阶段数为 19（非过时的 10）
3. WHEN tests/README.md 描述 L2 THEN 包含 Testcontainers 本地模式说明（非仅"直连服务器"）

### Requirement 5: 变异测试（测试有效性验证）

**User Story:** 作为质量负责人，我需要变异测试证明现有断言真的能捕获缺陷，以便测试覆盖率数字有可信度。

#### Acceptance Criteria

1. WHEN 执行 `mvn -pl zw-finance,zw-subcontract,zw-project -Pmutation verify` THEN 生成 PIT 变异报告
2. WHEN 某测试类变异杀死率 <70% THEN 补充断言直至达标或登记豁免理由
3. THE SYSTEM SHALL 将 PIT 以 `-Pmutation` profile 隔离，不进主构建链路（避免拖慢日常构建）

### Requirement 6: 性能基线（k6）

**User Story:** 作为运维负责人，我需要核心接口（登录/分页查询/付款提交）的 P99 基线，以便容量评估与性能回归有参照。

#### Acceptance Criteria

1. WHEN 执行 `tests/performance/` 下 3 个 k6 脚本（夜间低峰、并发≤20、单次≤5 分钟） THEN 生成 P95/P99 基线数据
2. WHEN 基线建立后 THEN 数据记录进 spec tasks.md，后续大版本升级前复测对比
3. WHEN k6 无法安装或执行 THEN 按受阻机制登记（允许 docker run grafana/k6 备选方案）

### Requirement 7: 安全扫描

**User Story:** 作为安全责任人，我需要代码与依赖的漏洞扫描进 CI，以便高危漏洞在合并前被发现。

#### Acceptance Criteria

1. WHEN 推送代码 THEN CodeQL workflow 对 java 与 javascript-typescript 双语言执行分析
2. WHEN 执行 `mvn -Psecurity verify` THEN dependency-check 输出依赖漏洞报告
3. WHEN 首轮扫描发现高危漏洞 THEN 逐项记录于 tasks.md 并处置（修复或登记豁免理由），禁止静默忽略

### Requirement 8: 测试效率与稳定性

**User Story:** 作为开发者，我需要增量测试选择与 flaky 检测，以便反馈环缩短且不稳定测试被显式治理。

#### Acceptance Criteria

1. WHEN 使用 `tests/affected-modules.sh` THEN 按 git diff 输出受影响模块的 `mvn -pl` 列表
2. WHEN 使用 `tests/flake-check.sh <测试类>` THEN 连跑 3 次，结果不一致即标记 FLAKY 并写入受阻台账
3. WHEN CI 执行测试 THEN surefire 配置 rerunFailingTestsCount=1（仅 CI，本地不开）

### Requirement 9: 测试受阻汇报与记录机制

**User Story:** 作为项目负责人，我需要在测试因环境或其他问题无法执行时得到显式汇报与记录，以便不产生"看似通过实则未测"的假象。

#### Acceptance Criteria

1. WHEN 测试因环境/依赖/网络/凭证/数据无法执行 THEN 禁止静默跳过或标记通过，禁止用假数据替代
2. WHEN 受阻发生 THEN 立即停止该项执行、登记 tasks.md 受阻台账（日期/层级/测试项/分类/原因/影响/处置/决策人/状态）、向用户汇报三选项（修复环境/延期/缩减范围）
3. WHEN 用户决策后 THEN 回填台账处置决策与决策人列
4. THE SYSTEM SHALL 将本规则写入 AGENTS.md 测试开发规则章节，AI 代理与人均适用
