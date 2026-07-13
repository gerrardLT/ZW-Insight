# 实施计划：ZW-Insight 全层次测试体系

## 概述

基于 5 层测试金字塔设计，按增量方式构建完整测试体系。从基础设施出发，逐层实现 L1（单元）→ L2（集成）→ L3（API）→ L4（端到端业务流）→ L5（前端 E2E），最终通过统一编排脚本和 CI 流水线串联所有层级。

技术栈：Java 21 + Spring Boot 3.2.6 + MyBatis-Plus 3.5.5 + JUnit 5 + Mockito + jqwik 1.9.1（后端），Playwright 1.61 + TypeScript（前端 E2E），Shell/Bash（L3/L4 脚本）。

## 任务

- [x] 1. 测试基础设施与数据清理框架
  - [x] 1.1 创建 TestConstants 常量类
    - 在 `zw-common/src/test/java/com/zwinsight/common/base/` 目录下创建 `TestConstants.java`
    - 定义 TEST_TENANT_ID=9999L、TEST_TENANT_NAME="自动化测试租户"、TEST_USER、TEST_PASS、REDIS_TEST_PREFIX="test:t9999:" 等常量
    - 定义受影响表的拓扑顺序列表 `TABLE_DELETE_ORDER`（按外键依赖逆序排列）
    - _需求: 1.1_

  - [x] 1.2 实现 TestDataCleaner 数据清理组件
    - 在 `zw-common/src/test/java/com/zwinsight/common/base/` 目录下创建 `TestDataCleaner.java`
    - 实现 `cleanByTenantId(Long tenantId)` 方法：校验 tenantId==9999 否则抛异常，按 TABLE_DELETE_ORDER 逆序执行 DELETE
    - 实现 `cleanRedisKeys(String pattern)` 方法：删除 Redis 中 `test:t9999:*` 模式的所有键
    - 标注 `@Component` 注入 JdbcTemplate 和 RedisTemplate
    - _需求: 1.2, 1.3, 1.4, 8.2, 8.3_

  - [x]* 1.3 编写 TestDataCleaner 属性测试
    - **Property 4: 安全护栏——拒绝非测试租户**
    - 使用 jqwik `@ForAll Long tenantId` 生成任意非 9999 的 tenantId，断言 cleanByTenantId 抛出异常
    - **Property 3: 清理顺序正确性**
    - 验证 TABLE_DELETE_ORDER 满足外键拓扑约束（对于外键 A→B，B 在 A 之后出现）
    - **验证: 需求 1.3, 1.4**

  - [x] 1.4 实现 IntegrationTestBase 集成测试基类
    - 在 `zw-common/src/test/java/com/zwinsight/common/base/` 目录下创建 `IntegrationTestBase.java`
    - 封装 `getAuthToken()` 方法：调用登录接口 + Redis 读取验证码 → 返回 JWT token
    - 失败时抛出 `TestAuthenticationException`（自定义异常类）
    - 提供 `@BeforeAll` 和 `@AfterAll` 模板方法调用 TestDataCleaner
    - _需求: 1.5, 1.7_

  - [x] 1.5 实现 AssertUtils 公共断言工具类
    - 在 `zw-common/src/test/java/com/zwinsight/common/util/` 目录下创建 `AssertUtils.java`
    - 实现 `assertApiSuccess(ResponseEntity)`: 断言 HTTP 2xx + body.code==200
    - 实现 `assertPageResult(Object data, int expectedMin)`: 断言分页结构正确且记录数 >= expectedMin
    - 实现 `mask(String sensitive)`: 日志脱敏函数，隐藏密码/token
    - _需求: 1.6, 8.6_

  - [x]* 1.6 编写 AssertUtils 属性测试
    - **Property 5: 断言函数正确性**
    - 使用 jqwik 生成任意 HTTP 状态码 + JSON body 组合，验证 assertApiSuccess 正确判定通过/失败
    - **Property 8: 日志脱敏完整性**
    - 使用 jqwik 生成包含敏感信息的字符串，验证 mask() 输出不包含原始敏感值的连续子串(>=4字符)
    - **验证: 需求 1.6, 8.6**

  - [x] 1.7 创建 Maven Profile 与测试配置文件
    - 在父 pom.xml 中新增 `integration-test` profile，配置 Failsafe 插件执行 `*IntegrationTest.java`
    - 创建 `src/test/resources/application-integration-test.yml`：配置服务器 MySQL 3306 / Redis 6379 连接信息
    - 配置 Surefire 插件：parallel=classes, threadCount=4, forkCount=1C
    - _需求: 2.3, 3.4_

  - [x] 1.8 创建手动清理脚本 cleanup-test-data.sh
    - 在 `keys/` 目录下创建 `cleanup-test-data.sh`
    - 连接服务器 MySQL，按拓扑逆序 DELETE WHERE tenant_id=9999
    - 清除 Redis test:t9999:* 键
    - 添加执行前确认提示，防止误执行
    - _需求: 8.7_

- [x] 2. 核心模块 L1 单元测试（project/contract/finance）
  - [x] 2.1 编写 ProjectService 单元测试
    - 创建 `zw-project/src/test/java/com/zwinsight/project/service/ProjectServiceTest.java`
    - 使用 @Mock 模拟 ProjectMapper、RedisTemplate 等依赖
    - 覆盖：创建项目（正常+名称为空）、查询项目（存在+不存在）、状态变更（正常+非法状态转换）
    - 使用 AssertJ 断言
    - _需求: 2.1, 2.2, 2.6_

  - [x] 2.2 编写 ContractService 单元测试
    - 创建 `zw-contract/src/test/java/com/zwinsight/contract/service/ContractServiceTest.java`
    - 覆盖：合同创建（正常+金额校验）、合同变更（正常+非法状态）、合同查询（分页+详情）
    - 重点测试金额字段 BigDecimal 精度
    - _需求: 2.1, 2.2_

  - [x] 2.3 编写 FinanceService 单元测试
    - 创建 `zw-finance/src/test/java/com/zwinsight/finance/service/FinanceServiceTest.java`
    - 覆盖：收支录入（正常+金额为负）、报表汇总计算、税率计算
    - 重点验证含税金额 = 不含税金额 × (1 + 税率) 的会计恒等式
    - _需求: 2.1, 2.2, 2.5_

  - [x]* 2.4 编写核心模块数值计算属性测试
    - **Property 6: 数值计算会计恒等式**
    - 在 `zw-finance/src/test/java/` 下创建 `FinancePropertyTest.java`
    - 使用 jqwik `@ForAll @BigRange BigDecimal amount` + `@ForAll @BigRange BigDecimal taxRate`
    - 验证：taxAmount = amount × (1 + taxRate) 的精度不丢失（scale=2, RoundingMode.HALF_UP）
    - 覆盖金额范围 [0.01, 999999999.99]，税率范围 [0, 0.99]
    - **验证: 需求 2.5**

- [x] 3. 核心模块 L2 集成测试
  - [x] 3.1 编写 ProjectService 集成测试
    - 创建 `zw-project/src/test/java/com/zwinsight/project/integration/ProjectIntegrationTest.java`
    - 继承 IntegrationTestBase，使用 @SpringBootTest + @ActiveProfiles("integration-test")
    - 覆盖：项目 CRUD 完整往返（创建→查询→修改→删除）
    - @BeforeAll 插入测试租户数据，@AfterAll 调用 TestDataCleaner 清理
    - _需求: 3.1, 3.2, 3.3, 3.5_

  - [x] 3.2 编写 ContractService 集成测试
    - 创建 `zw-contract/src/test/java/com/zwinsight/contract/integration/ContractIntegrationTest.java`
    - 覆盖：合同 CRUD 往返 + 审批流推进（提交→审核→通过/驳回）
    - 验证审批状态正确流转
    - _需求: 3.1, 3.5_

  - [x] 3.3 编写 FinanceService 集成测试
    - 创建 `zw-finance/src/test/java/com/zwinsight/finance/integration/FinanceIntegrationTest.java`
    - 覆盖：收支记录 CRUD + 关联合同验证 + 金额汇总查询
    - _需求: 3.1, 3.5_

  - [x] 3.4 编写 Flowable 工作流集成测试
    - 创建 `zw-system/src/test/java/com/zwinsight/system/integration/FlowableIntegrationTest.java`
    - 覆盖：流程定义部署、流程实例创建、任务完成、流程结束
    - 使用 tenant_id=9999 隔离流程数据
    - _需求: 3.5_

  - [x]* 3.5 编写集成测试数据隔离属性测试
    - **Property 1: 数据隔离不变量**
    - 验证集成测试创建的所有数据记录 tenant_id 字段均为 9999
    - **Property 2: 无残留性**
    - 验证 @AfterAll 执行后 tenant_id=9999 的行数为 0 且 Redis test:t9999:* 键数量为 0
    - **验证: 需求 8.1, 8.2, 8.3**

  - [x] 3.6 处理服务器不可达场景
    - 实现 `ServerAvailabilityCondition` 自定义 JUnit 5 Condition
    - 当 MySQL/Redis 连接失败时标记测试为 @Disabled("Server unreachable")
    - CI 输出中明确显示跳过原因
    - _需求: 3.6_

- [~] 4. Checkpoint - 基础设施与核心测试验证
  - 确保所有测试通过，ask the user if questions arise.
  - 验证 `mvn test` 核心模块单元测试通过
  - 验证 `mvn verify -Pintegration-test` 集成测试通过（需服务器可达）

- [ ] 5. 补齐剩余业务模块单元测试
  - [x] 5.1 编写 BudgetService 单元测试
    - 创建 `zw-budget/src/test/java/com/zwinsight/budget/service/BudgetServiceTest.java`
    - 覆盖：预算编制（正常+超限）、预算调整（正常+已锁定）、预算查询
    - _需求: 2.1, 2.2_

  - [x] 5.2 编写 MaterialService 单元测试
    - 创建 `zw-material/src/test/java/com/zwinsight/material/service/MaterialServiceTest.java`
    - 覆盖：材料入库（正常+库存不足）、出库（正常+超量出库）、库存查询
    - _需求: 2.1, 2.2_

  - [x] 5.3 编写 MachineService 单元测试
    - 创建 `zw-machine/src/test/java/com/zwinsight/machine/service/MachineServiceTest.java`
    - 覆盖：机械台班记录（正常+重复记录）、结算计算、台班查询
    - _需求: 2.1, 2.2_

  - [-] 5.4 编写 LaborService 单元测试
    - 创建 `zw-labor/src/test/java/com/zwinsight/labor/service/LaborServiceTest.java`
    - 覆盖：劳务人员管理（正常+重复身份证）、考勤记录、工资计算
    - _需求: 2.1, 2.2_

  - [-] 5.5 编写 SubcontractService 单元测试
    - 创建 `zw-subcontract/src/test/java/com/zwinsight/subcontract/service/SubcontractServiceTest.java`
    - 覆盖：分包合同管理、结算审批流、计量确认
    - _需求: 2.1, 2.2_

  - [-] 5.6 编写 PurchaseService + FieldService 单元测试
    - 创建采购模块和现场模块的 Service 单元测试
    - 覆盖：采购申请/入库/退料流程、现场日志/签证/变更
    - _需求: 2.1, 2.2_

  - [-] 5.7 编写 TenderService 单元测试
    - 创建 `zw-tender/src/test/java/com/zwinsight/tender/service/TenderServiceTest.java`
    - 覆盖：招标发布、投标记录、开标评标流程
    - _需求: 2.1, 2.2_

- [ ] 6. 系统/安全/工作流模块测试
  - [-] 6.1 编写 JWT 认证与权限单元测试
    - 创建 `zw-system/src/test/java/com/zwinsight/system/service/AuthServiceTest.java`
    - 覆盖：token 生成（正常+过期）、token 验证（有效+无效+篡改）、权限校验（有权限+无权限）
    - _需求: 2.1_

  - [-] 6.2 编写验证码与 Redis 缓存测试
    - 创建 `zw-system/src/test/java/com/zwinsight/system/service/CaptchaServiceTest.java`
    - 覆盖：验证码生成（正常返回 UUID+图片）、验证码校验（正确+错误+过期）
    - Mock RedisTemplate 验证 key 格式为 `captcha:{uuid}`
    - _需求: 2.1, 2.2_

  - [-] 6.3 编写菜单树与数据权限测试
    - 创建 `zw-system/src/test/java/com/zwinsight/system/service/MenuServiceTest.java`
    - 覆盖：菜单树构建（多级嵌套+空节点）、角色数据范围计算
    - _需求: 2.1_

  - [-] 6.4 编写 Flowable 审批流 Service 单元测试
    - 创建 `zw-system/src/test/java/com/zwinsight/system/service/WorkflowServiceTest.java`
    - Mock ProcessEngine 和 TaskService
    - 覆盖：流程发起（正常+流程定义不存在）、任务审批（通过+驳回+转办）
    - _需求: 2.1, 2.2_

- [~] 7. Checkpoint - L1 单元测试全量验证
  - 确保所有测试通过，ask the user if questions arise.
  - 执行 `mvn test` 确认所有 22 模块单元测试通过
  - 确认无编译错误和测试隔离问题

- [ ] 8. 升级 lifecycle-sim.sh（L4 端到端业务流）
  - [~] 8.1 创建 lifecycle-sim-v2.sh 基础框架
    - 在 `keys/` 目录下创建 `lifecycle-sim-v2.sh`
    - 实现 TEST_TENANT_ID=9999 变量、CREATED_IDS 数组追踪、strict_assert() 函数
    - 实现 trap EXIT → cleanup_all 确保无论如何都执行清理
    - source verify-base.sh 复用登录/调用基座
    - _需求: 5.1, 5.3, 5.5_

  - [~] 8.2 实现 10 阶段业务流与严格断言
    - 实现 10 个阶段的 API 调用链（创建项目→合同→预算→采购→施工→计量→结算→验收→完工→归档）
    - 每阶段调用后 strict_assert 验证 HTTP 2xx + code=200
    - 每阶段创建的资源 ID 加入 CREATED_IDS 追踪
    - 严格模式失败时立即 BREAK 并触发 cleanup_all
    - _需求: 5.2, 5.4_

  - [~] 8.3 实现自动清理与兜底 SQL + JSON 报告
    - cleanup_all 函数：逆序遍历 CREATED_IDS 调用 DELETE 接口
    - 兜底清理：docker exec zwi-mysql 执行 DELETE WHERE tenant_id=9999
    - 生成结构化 JSON 报告：`{passed, failed, skipped, cleanedRecords, stages: [...]}`
    - 输出到 `tests/reports/lifecycle-sim-report.json`
    - _需求: 5.4, 5.6, 5.7_

  - [ ]* 8.4 编写 lifecycle-sim 报告正确性验证
    - **Property 7: 测试报告正确性**
    - 验证 JSON 报告中 passed + failed + skipped = total
    - 验证 failed > 0 时脚本退出码非零，failed = 0 时退出码为零
    - 可通过 shell 脚本模拟不同阶段结果组合来验证
    - **验证: 需求 5.7**

- [ ] 9. L3 API 接口测试脚本
  - [~] 9.1 创建 API 测试基础框架与项目模块脚本
    - 创建 `keys/test-api-project.sh`：项目模块 CRUD + 审批 + 分页查询测试
    - 定义 assert_http()、report_summary() 公共函数
    - source verify-base.sh 复用登录和调用能力
    - 实现测试资源清理逻辑（测试结束前 DELETE 已创建资源）
    - _需求: 4.1, 4.2, 4.3, 4.5_

  - [~] 9.2 创建合同/财务/采购模块 API 测试脚本
    - 创建 `keys/test-api-contract.sh`：合同 CRUD + 审批接口测试
    - 创建 `keys/test-api-finance.sh`：财务收支 CRUD + 汇总查询测试
    - 创建 `keys/test-api-purchase.sh`：采购申请/入库/退料测试
    - 每个脚本独立执行，输出通过/失败计数
    - _需求: 4.1, 4.3, 4.4, 4.6_

  - [~] 9.3 创建剩余业务模块 API 测试脚本
    - 创建 `keys/test-api-material.sh`、`keys/test-api-machine.sh`、`keys/test-api-labor.sh`、`keys/test-api-subcontract.sh`
    - 每个脚本覆盖对应模块的 CRUD 端点
    - 断言 HTTP 2xx + body.code=200 + 无异常堆栈
    - _需求: 4.1, 4.3, 4.4_

- [ ] 10. 前端 Playwright E2E 改造（L5）
  - [~] 10.1 修改 playwright.config.ts 新增 e2e-real project
    - 在 `zw-insight-web/playwright.config.ts` 中新增 `e2e-real` project 配置
    - 设置 baseURL 指向服务器 :18081
    - 配置 storageState 引用 `./e2e/.auth/storage-state.json`
    - 设置 workers=4 并行执行
    - 保留原有 `e2e` project 作为 Mock 模式
    - _需求: 6.1, 6.5, 6.6_

  - [~] 10.2 创建 auth-real.setup.ts 真实登录 setup
    - 创建 `zw-insight-web/e2e/fixtures/auth-real.setup.ts`
    - 实现真实登录流程：导航登录页→填写用户名密码→调用 /api/v1/test/captcha-code 获取验证码→提交→保存 storageState
    - 当 captcha-code 接口不可用时输出降级警告
    - _需求: 6.2, 6.3, 6.4_

  - [~] 10.3 创建真实模式 E2E 测试用例
    - 创建 `zw-insight-web/e2e/tests/real/login.spec.ts`：真实登录流程验证
    - 创建 `zw-insight-web/e2e/tests/real/project-crud.spec.ts`：项目列表/创建/编辑/删除
    - 创建 `zw-insight-web/e2e/tests/real/workflow.spec.ts`：审批流操作验证
    - 所有操作使用 tenant_id=9999 数据
    - _需求: 6.1, 6.2_

- [~] 11. Checkpoint - L3/L4/L5 测试验证
  - 确保所有测试通过，ask the user if questions arise.
  - 验证 `bash keys/test-api-project.sh` 等 L3 脚本可正常执行
  - 验证 `bash keys/lifecycle-sim-v2.sh` L4 脚本通过
  - 验证 `npx playwright test --project=e2e-real` L5 可执行（需服务器可达）

- [ ] 12. 统一测试编排脚本 run-all-tests.sh
  - [~] 12.1 创建 run-all-tests.sh 主脚本
    - 在 `tests/` 目录下创建 `run-all-tests.sh`
    - 支持 `--layers=L1,L2,L3,L4,L5` 参数选择执行层级
    - 支持 `--fail-fast` 参数：首个失败层级后停止
    - 按 L1→L2→L3→L4→L5 顺序依次执行
    - 每层级调用对应命令：L1=`mvn test`，L2=`mvn verify -Pintegration-test`，L3=`bash keys/test-api-*.sh`，L4=`bash keys/lifecycle-sim-v2.sh`，L5=`npx playwright test --project=e2e-real`
    - _需求: 9.1, 9.2, 9.3, 9.5_

  - [~] 12.2 实现汇总报告输出
    - 每层级执行后记录：通过数/失败数/跳过数/耗时
    - 所有层级完成后输出格式化汇总表格
    - 输出 JSON 格式报告到 `tests/reports/all-tests-report.json`
    - 有任何失败时以非零退出码结束
    - _需求: 9.4_

  - [ ]* 12.3 编写编排脚本属性测试
    - **Property 9: 编排脚本层级选择**
    - 验证 --layers 参数解析正确：仅执行指定层级，未指定不执行
    - 验证 --fail-fast 模式：首个失败后停止后续
    - **Property 7: 测试报告正确性**
    - 验证汇总报告 passed + failed + skipped = total
    - **验证: 需求 9.3, 9.4, 9.5**

- [ ] 13. 重新启用 CI 集成测试 + dev-loop.ps1 对接
  - [~] 13.1 恢复 CI 后端测试执行
    - 修改 `.github/workflows/deploy.yml` backend job：移除 `-DskipTests`，恢复 `mvn test`
    - 确保 Surefire 插件并行配置生效
    - _需求: 7.1_

  - [~] 13.2 恢复 integration-test job
    - 移除 `.github/workflows/deploy.yml` 中 integration-test job 的 `if: false` 条件
    - 配置 SSH 步骤：使用 secrets.SSH_KEY 连接服务器执行 L3/L4 脚本
    - 收集测试结果 artifact，失败时阻止后续 deploy 步骤
    - _需求: 7.2, 7.3, 7.5, 7.6_

  - [~] 13.3 更新 dev-loop.ps1 对接测试体系
    - 修改 `keys/dev-loop.ps1`：集成 `mvn test` 单元测试步骤
    - 添加本地快速测试模式（仅 L1）和完整测试模式（L1+L2）
    - 测试失败时输出清晰的失败摘要
    - _需求: 7.1_

- [ ] 14. JaCoCo 覆盖率报告 + 持续维护机制
  - [~] 14.1 配置 JaCoCo Maven 插件
    - 在父 pom.xml 中添加 jacoco-maven-plugin 0.8.12
    - 配置 prepare-agent、report、check 三个 execution
    - 核心模块（project, contract, budget, finance, material, machine, labor, subcontract）设置 LINE COVEREDRATIO >= 0.80
    - 非核心模块设置较低阈值（>= 0.50）
    - _需求: 2.4, 7.4_

  - [~] 14.2 配置 CI JaCoCo 报告上传
    - 在 CI backend job 中添加 `mvn verify` 执行覆盖率检查
    - 覆盖率不达标时构建失败并输出未覆盖方法清单
    - 上传 JaCoCo HTML 报告为 GitHub Actions artifact
    - _需求: 7.4_

  - [~] 14.3 创建测试维护文档与规则
    - 创建 `tests/README.md`：说明测试体系架构、各层级执行方式、常见问题排查
    - 更新 `AGENTS.md`：添加测试开发规则（新模块必须包含单元测试、集成测试 tenant_id=9999、PR 前运行 L1）
    - 记录测试端点安全规则（/api/v1/test/* 仅 test profile）
    - _需求: 8.4, 8.5_

- [~] 15. Final checkpoint - 全层次测试体系验证
  - 确保所有测试通过，ask the user if questions arise.
  - 执行 `bash tests/run-all-tests.sh --layers=L1` 验证编排脚本
  - 确认 CI workflow 配置正确（可通过 dry-run 或推送到测试分支验证）
  - 确认 JaCoCo 覆盖率报告正常生成

## Notes

- 任务标记 `*` 为可选属性测试任务，可跳过以加速 MVP 交付
- 每个任务引用具体需求条目确保可追溯性
- Checkpoint 任务确保增量验证，及时发现问题
- 属性测试使用 jqwik 1.9.1（后端已有依赖）验证正确性属性
- 单元测试使用 Mockito mock 所有外部依赖，集成测试直连服务器真实数据库
- 所有测试数据使用 tenant_id=9999 隔离，测试后物理 DELETE 清理
- L3/L4 脚本基于已有 verify-base.sh 基座扩展，复用登录/调用能力
- 前端 E2E 保留 Mock 模式做 UI 回归，新增 Real 模式打真实服务器

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1", "1.2"] },
    { "id": 1, "tasks": ["1.3", "1.4", "1.5", "1.7", "1.8"] },
    { "id": 2, "tasks": ["1.6", "2.1", "2.2", "2.3"] },
    { "id": 3, "tasks": ["2.4", "3.1", "3.2", "3.3", "3.6"] },
    { "id": 4, "tasks": ["3.4", "3.5", "5.1", "5.2", "5.3"] },
    { "id": 5, "tasks": ["5.4", "5.5", "5.6", "5.7", "6.1", "6.2", "6.3", "6.4"] },
    { "id": 6, "tasks": ["8.1", "9.1", "10.1"] },
    { "id": 7, "tasks": ["8.2", "9.2", "9.3", "10.2", "10.3"] },
    { "id": 8, "tasks": ["8.3", "8.4", "12.1"] },
    { "id": 9, "tasks": ["12.2", "12.3", "13.1"] },
    { "id": 10, "tasks": ["13.2", "13.3", "14.1"] },
    { "id": 11, "tasks": ["14.2", "14.3"] }
  ]
}
```
