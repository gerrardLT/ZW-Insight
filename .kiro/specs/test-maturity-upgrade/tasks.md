# Implementation Plan: 测试成熟度升级（三阶段）

## Overview

基于 `tests/TESTING-MATURITY.md` 评估（54/100），按"覆盖与门禁 → 有效性验证与非功能 → 效率与顶级门槛"三阶段补齐测试体系纵深。需求见 requirements.md（R1~R9），技术决策见 design.md（D1~D7）。

## 执行约定（每个执行者必读）

1. 每个补测模块完成验收：`mvn -pl <模块> -am test` 全绿 → 采集覆盖率更新 `tests/coverage-baseline.json` → 勾选任务 → TESTING-MATURITY.md 附录 A 对应行更新
2. **任何环境受阻（Docker/k6/CodeQL/SSH/覆盖率采集）→ 立即触发受阻机制（见文末台账），禁止静默跳过、禁止假数据替代**
3. 本机采集覆盖率必须带 `-Djacoco.destFile=<ASCII路径>` `-Djacoco.dataFile=<同一ASCII路径>`（中文路径坑，详见 TESTING-MATURITY.md 附录 B.5）
4. 基线千分比计算：`floor(LINE_COVERED * 1000 / (LINE_COVERED + LINE_MISSED))`，CI 比对容忍 ±1‰

## Tasks

### 阶段一：覆盖与门禁（目标：6 核心模块 ≥60% + 覆盖率不回退）

- [x] 1.1 落地 `tests/TESTING-MATURITY.md` 正式评估文档（含附录 A 基线表、附录 B 受阻规范）_需求: R1_
- [x] 1.2 修正 `tests/README.md` 过时内容：L4 阶段数 10→19、覆盖率门槛表述统一（60% 现状 + 80% 阶段三目标）、L2 章节补 Testcontainers 本地模式 _需求: R4_
- [x] 1.3 统一口径：AGENTS.md 第 8 节覆盖率表述改为"当前门槛 60%（pom jacoco check，verify 阶段），80% 为阶段三目标"；新增"测试受阻汇报规则" _需求: R4, R9_
- [x] 1.4 覆盖率基线守护：`tests/coverage-baseline.json`（22 模块千分比，git 追踪）+ `.github/workflows/deploy.yml` backend job 尾部增基线比对步骤（任一回退 CI 失败）_需求: R2_
- [x] 1.5 模块补测（六核心模块全部达标 ≥60%，新增约 30 个测试类）_需求: R2_
  - [x] 1.5.1 zw-purchase（基线 10.3% → **70.5%**）：新增 8 个测试类 37 用例（SupplierSms/SupplierAuth/SupplierInquiry/SupplierQuotation/Quotation/BidRanking/PublicQuotation/PurchaseSettlement），118/118 全绿；基线 JSON 已更新 705‰（2026-08-05）
  - [x] 1.5.2 zw-labor（20.7% → **70.3%**）：新增 SalaryStatisticsServiceImpl/LaborRoster/Team/LaborRewardPunish/LaborRosterBatchHandler 等 5 测试类，121/121 全绿；顺带修复 JDK21 JaCoCo+Mockito agent 共存（surefire 加 -XX:+EnableDynamicAgentLoading）与 commons-io 2.11/easyexcel 3.3.4 不兼容（钉住 2.16.1）两个真实缺陷
  - [x] 1.5.3 zw-contract（23.5% → **62.0%**）：新增 OtherContract/ContractTemplate/FinalSettlement/ChangeVisa/QuantityList/BomItem/ContractExpiryTask/BoqServiceUpload/BoqReadListener/ConstructionContractApprovalListener 共 10 测试类，220/220 全绿
  - [x] 1.5.4 zw-finance（27.6% → **64.8%**）：新增 ProjectSettlementServiceFull/FundTransfer/InvoiceSummary/RetentionReturn/ProjectReimbursement/ReserveFundApply/RetentionMoney/PersonalReimbursement 共 8 测试类，513/513 全绿
  - [x] 1.5.5 zw-budget（39.6% → **71.8%**）：新增 BudgetConfig/CostSubcategory/BudgetChangeApprovalListener/BudgetControlCheckFull/BudgetChangeServiceFull 共 5 测试类，356/356 全绿
  - [x] 1.5.6 zw-material（41.6% → **72.0%**）：新增 MaterialRefund/StockWarningTask/ProjectMaterialStock/MaterialTransferApprovalListener 共 4 测试类，100/100 全绿
- [ ] 1.6 L2 扩容与实跑验证（决策 B：延期至 CI 验证；deploy.yml backend job 已加 L2 Testcontainers 步骤，待 push 首跑）_需求: R3_
  - [x] 1.6.1 CI 首跑验证 Testcontainers 基建：`mvn verify -pl zw-app -am -Pintegration-test -Dit.test=com.zwinsight.integration.*`（存量 8 个集成测试）；首跑失败→登记台账并修复 —— 结果：一~七跑修复 7 类基建问题（台账 2026-08-05），八跑（run 30988370889）起 L2 Testcontainers 实跑通过并持续全绿（run 31028963433 确认）
  - [ ] 1.6.2 zw-purchase 新增 1 个集成测试（前置：1.6.1 通过 + init-test-schema.sql 补 biz_purchase_contract 表；tenant=9999 + 清理）—— 前置已满足（biz_purchase_contract 已在四跑时补齐）
  - [ ] 1.6.3 zw-material 新增 1 个集成测试（前置同上，补 biz_material_* 表）
- [x] 1.7 verify 门槛验证：`mvn verify -pl zw-subcontract` 通过（jacoco:check 0.60 无告警，BUILD SUCCESS，2026-08-05）；workflow backend job 保持 package 并注释“何时切 verify”（8 核心模块全 ≥80% 后，见 3.3）_需求: R2_

### 阶段二：有效性验证与非功能（目标：证明测试能杀 bug + 性能/安全有基线）

- [ ] 2.1 PIT 变异测试 _需求: R5_
  - [ ] 2.1.1 根 pom 增 `pitest-maven 1.15.x + pitest-junit5-plugin 1.2.x`（`-Pmutation` profile 隔离）
  - [ ] 2.1.2 对 zw-finance / zw-subcontract / zw-project 跑变异，杀死率回填本表
  - [ ] 2.1.3 杀死率 <70% 的测试类补断言或登记豁免理由
- [ ] 2.2 k6 性能基线 _需求: R6_
  - [ ] 2.2.1 新建 `tests/performance/`：login.js / page-query.js / payment-submit.js（执行约束写脚本头：夜间低峰、并发≤20、单次≤5 分钟）
  - [ ] 2.2.2 建立 P95/P99 基线并回填本表；k6 装不上→受阻登记（备选 docker run grafana/k6）
- [ ] 2.3 安全扫描 _需求: R7_
  - [ ] 2.3.1 新建 `.github/workflows/codeql.yml`（java + javascript-typescript，push main + 每周 cron）
  - [ ] 2.3.2 根 pom 增 `dependency-check-maven 10.x`（`-Psecurity` profile）
  - [ ] 2.3.3 首轮高危漏洞清单登记本表并逐项处置（修复/豁免理由），不静默忽略
- [ ] 2.4 L3 契约强化：8 个 `keys/test-api-*.sh` 增 jq 字段结构断言（关键响应字段存在性），从状态码升级为契约校验 _需求: R2_

### 阶段三：效率与顶级门槛（持续）

- [ ] 3.1 增量测试选择：`tests/affected-modules.sh`（git diff → 模块映射，zw-common 变更触发全量）+ `run-all-tests.sh --affected` 参数 _需求: R8_
- [ ] 3.2 flaky 检测：`tests/flake-check.sh`（连跑 3 次不一致即 FLAKY 写入受阻台账）；CI surefire 增 `rerunFailingTestsCount=1`（本地不开）_需求: R8_
- [ ] 3.3 80% 门槛转正：8 核心模块全 ≥80% 后，pom jacoco check 0.60→0.80（按模块差异化 rule）、CI 切 verify、AGENTS.md/README 同步 _需求: R2, R4_

## 数据回填区（执行结果记录）

| 项 | 结果 | 日期 |
|---|------|------|
| PIT 杀死率（finance/subcontract/project） | 待执行 | - |
| k6 P99 基线（登录/分页/付款） | 待执行 | - |
| CodeQL 高危数 | 待执行 | - |
| 依赖扫描高危数 | 待执行 | - |

## 受阻项登记台账

> 规则见 AGENTS.md"测试受阻汇报规则"与 TESTING-MATURITY.md 附录 B。分类：ENV 环境 / DEP 依赖 / NET 网络 / CRED 凭证 / DATA 数据 / OTHER。

| 日期 | 层级 | 测试项 | 分类 | 原因 | 影响范围 | 处置决策 | 决策人 | 状态 |
|------|------|--------|------|------|---------|---------|--------|------|
| （示例）2026-07-31 | L1 | JaCoCo 覆盖率采集 | ENV | Windows 中文路径导致 agent 无法写 exec | 覆盖率数据缺失 | destFile 重定向 ASCII 路径 | 用户 | 已解决 |
| 2026-08-05 | L2 | 任务 1.6.1 zw-app 集成测试实跑（含 1.6.2/1.6.3 新增验证） | ENV | 本机未安装 Docker（`docker` 命令不存在），Testcontainers 无法启动 MySQL/Redis 容器 | L2 层实跑验证缺失（存量 8 个集成测试与新增集成测试无法验证）；测试代码本身不受影响（无 Docker 时 @EnabledIfDockerAvailable 自动跳过） | B. 延期至 CI 验证：deploy.yml backend job 已增 L2 Testcontainers 步骤（仅跑 com.zwinsight.integration.*），待 push 首跑；1.6.2/1.6.3 新增集成测试待基建被 CI 证明可用后再写 | 用户 | 待 CI 首跑 |
| 2026-08-05 | L1/L2 | CI 首跑（run 30978957773）Backend Build 失败：3 个 MapperTest 全部 ERROR | OTHER | 真实配置缺陷：BaseH2MapperTest 嵌套 @SpringBootApplication+@MapperScan(短名) 位于 com.zwinsight.test 包，被全量上下文组件扫描扫入，与启动类 @MapperScan(全限定名) 重复注册全部 Mapper → NoUniqueBeanDefinitionException。本地无 Docker 时集成测试跳过未暴露，生产启动正常（SupplierAutoScoreService 实跑验证），仅测试上下文受影响 | 全部 @SpringBootTest 集成测试无法启动 | 已修复（commit 2ba70ab）：3 个 H2 测试类迁移至 h2test 包（扫描域外）+ 位置警告注释；zw-app 本地 210/210 绿；CI 重跑验证中 | AI+规则 | 已解决（待 CI 确认） |
| 2026-08-05 | L2 | CI 二跑（run 30980907217）：Flyway 迁移失败 Table 'zw_test.biz_project' doesn't exist | ENV | 集成测试 DB 由 Testcontainers withInitScript(init-test-schema.sql) 建表，但 Spring Boot Flyway 自动启用去执行 V2__add_validation_constraints.sql，引用 initScript 未建的表 | 集成测试上下文启动失败 | 已修复（commit 8ba4fd6）：application-test.yml 禁用 spring.flyway.enabled=false（测试建表走 initScript，无需 Flyway） | AI+规则 | 已解决（待 CI 确认） |
| 2026-08-05 | L2 | CI 三跑（run 30981906151）：MinIO endpoint must not be null | ENV | MinioClient Bean 创建时 minio.endpoint 为 null（test profile 无 MinIO 配置） | 集成测试上下文启动失败 | 已修复（commit b768c52）：application-test.yml 补 MinIO 哑配置（仅满足启动，集成测试不实际上传文件）；预扫描确认其余配置无阻塞项 | AI+规则 | 已解决（待 CI 确认） |
| 2026-08-05 | L2 | CI 四跑（run 30983162034）：上下文启动成功，但 3 个 MapperTest 共 23 用例失败 | ENV | 双重根因：① init-test-schema.sql 缺 8 张表（biz_subcontract/biz_purchase_contract/biz_labor_contract/biz_payment_apply/biz_entry_apply/biz_resign_apply/sys_org/sys_post）→ Table doesn't exist；② 无登录态时租户插件注入 tenant_id=0，而测试 INSERT 未带 tenant_id → 查询空 | BudgetOccupied(9E)/HrStatistics(9E)/BizProjectMember(5F) 共 23 用例失败 | 已修复：init-test-schema.sql 从 00_schema.sql 提取补齐 8 表；BaseIntegrationTest 每用例设租户上下文 9999（@BeforeEach/@AfterEach）；3 个 MapperTest 的 INSERT 补 tenant_id=9999（HrStatistics 的 TENANT_ID 常量 1→9999） | AI+规则 | 已解决（待 CI 确认） |
| 2026-08-05 | L2 | CI 五跑（run 30984994328）：仅剩 HrStatisticsMapperTest 9 用例失败（其余全绿） | ENV | init-test-schema.sql 的 sys_user 为手写简化版，缺 org_id/post_id 列（00_schema.sql 已有），HrStatistics 的 INSERT 引用报错 Unknown column 'org_id' | HrStatisticsMapperTest 9 用例 | 已修复：sys_user 补 org_id/post_id 两列（与 00_schema.sql 对齐）；另确认 init-test-schema 为手写简化版 schema，后续如有新列缺失可同样对照 00_schema 补列 | AI+规则 | 已解决（待 CI 确认） |
| 2026-08-05 | L2 | CI 六跑（run 30986125863）：单测+基线检查全过，L2 步骤报 No tests matching pattern | OTHER | CI L2 步骤命令用了 -DfailIfNoTests=false（仅对 surefire 生效），failsafe 在根聚合模块无匹配测试即报错退出，全部模块 SKIPPED | L2 failsafe 未实际执行 | 已修复（commit dd3646f）：补 -Dfailsafe.failIfNoSpecifiedTests=false | AI+规则 | 已解决（待 CI 确认） |
| 2026-08-05 | L2 | CI 七跑（run 30987305822）：L2 步骤 jacoco check 门槛失败 | OTHER | L2 步骤用 mvn verify，verify 阶段触发 jacoco check（BUNDLE LINE ≥60%），当前各模块覆盖率 24~72% 未达标，reactor 首模块 zw-insight-common 即失败，failsafe 未实际执行 | L2 failsafe 未实际执行 | 已修复（commit acb2907）：L2 步骤加 -Djacoco.skip=true（覆盖率守护由 Build 步骤基线比对负责，职责分离） | AI+规则 | 已解决（待 CI 确认） |
| 2026-08-05 | L3 | CI 八跑（run 30988370889）：Backend Build+Deploy 首次全绿（L2 Testcontainers 首跑通过，①②③修复随镜像部署），Integration Test 中 L3 20 用例失败 | OTHER | L3 脚本与业务演进漂移：①分包结算创建/更新缺 details（后端新增必填校验）②采购询价发布要求至少一个询价物料③labor 预算管控 BLOCK 拦截（项目 90001 劳务预算已用尽）④DELETE 断言漂移（非草稿资源不可删）⑤project 成员残留 | L3 20/276 失败（L4 19/19 全过） | 部分修复（commit 12a357e）：分包补 details、采购补 items；剩余（labor 预算/DELETE 断言/成员残留）待下轮实测后修复 | AI+规则 | 部分解决（待 CI 确认） |
| 2026-08-05 | L3 | CI 九~十二跑（run 30992179049→31028963433）：L3 剩余失败逐项清零 | OTHER | 四类根因：①9 处 DELETE 断言删除已提交资源被"仅草稿可删除"正确拒绝（用户决策方案B：改脚本流程而非改断言预期）②labor 创建测试本就是预算拦截验证（种子 1000万合同+400万付款超 1200万预算，执行率 117% 为预期行为），分页取到的种子 EFFECTIVE 合同不可编辑/提交③finance 付款申请 contractId=99431 过期且 contractCategory=MATERIAL 不在 resolvePayable 的 MODULE_CATEGORIES（PURCHASE/LABOR/MACHINE/SUBCONTRACT），误落 biz_other_contract 分支④project 创建自动加入 userId=1 为唯一 PM，重复添加被唯一性校验拒绝、删除触发"至少保留一名项目经理" | L3 13→1→0 失败 | 方案B修复 9 处 DELETE：新建草稿→按 status=DRAFT（或 createdAt 降序，machine/material 分页无 status 过滤）取最新一条→删除；labor 流转测试改新建无金额草稿（金额为空时预算切面放行，不绕过管控只避开额度）；finance 改 contractId=91501+category=PURCHASE，其删除测试同用方案B；project 添加成员改 userId=2。commits：5721900/da38bf5/ded671c/b67d6a8 | 用户+AI | 已解决（run 31028963433 全链路 success，L3 汇总 通过=8 失败=0，L4 140/140） |
