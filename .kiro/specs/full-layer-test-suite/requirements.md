# 需求文档：ZW-Insight 全层次测试体系

## Introduction

为 ZW-Insight 工程项目管理平台构建 5 层测试金字塔（L1 单元测试 / L2 集成测试 / L3 API 接口测试 / L4 端到端业务流 / L5 前端 E2E），实现从函数级到用户操作级的完整质量保障体系。所有测试通过 `tenant_id=9999` 进行数据隔离，测试完成后物理 DELETE 清理。

## Glossary

- **Test_Infrastructure**: 测试基础设施层，包含公共常量、数据清理器、基类、断言工具
- **Unit_Test_Framework**: L1 单元测试框架，使用 JUnit 5 + Mockito + AssertJ
- **Integration_Test_Framework**: L2 集成测试框架，使用 @SpringBootTest 直连服务器数据库
- **API_Test_Runner**: L3 API 接口测试执行器，基于 Shell 脚本 + verify-base.sh
- **Lifecycle_Sim**: L4 端到端业务流测试编排器（lifecycle-sim-v2.sh）
- **E2E_Runner**: L5 前端 E2E 测试执行器，基于 Playwright 双模式
- **CI_Pipeline**: GitHub Actions CI/CD 流水线
- **TestDataCleaner**: 测试数据物理清理组件
- **Test_Tenant**: 自动化测试租户（tenant_id=9999）
- **JaCoCo**: Java 代码覆盖率工具
- **Core_Module**: 核心业务模块（project, contract, budget, finance, material, machine, labor, subcontract）

## Requirements

### Requirement 1: 测试基础设施

**User Story:** 作为测试开发者，我希望有统一的测试基础设施（常量、清理器、基类、断言工具），以便所有测试层级复用公共能力并保持一致性。

#### Acceptance Criteria

1. THE Test_Infrastructure SHALL 提供 TestConstants 类，定义 TEST_TENANT_ID=9999L、TEST_TENANT_NAME、TEST_USER、TEST_PASS、REDIS_TEST_PREFIX 等常量
2. THE Test_Infrastructure SHALL 提供 TestDataCleaner 组件，支持按 tenant_id 物理删除所有受影响表的测试数据
3. WHEN TestDataCleaner.cleanByTenantId() 被调用时，THE TestDataCleaner SHALL 按表依赖拓扑逆序删除，避免外键约束冲突
4. WHEN TestDataCleaner.cleanByTenantId() 收到非 9999 的 tenant_id 时，THE TestDataCleaner SHALL 拒绝执行并抛出异常，防止误删生产数据
5. THE Test_Infrastructure SHALL 提供 IntegrationTestBase 基类，封装真实登录获取 JWT token 的逻辑
6. THE Test_Infrastructure SHALL 提供 AssertUtils 工具类，包含 assertApiSuccess()、assertPageResult() 等公共断言方法
7. WHEN IntegrationTestBase.getAuthToken() 调用登录接口失败时，THE IntegrationTestBase SHALL 抛出 TestAuthenticationException 并包含失败原因

### Requirement 2: L1 单元测试

**User Story:** 作为开发者，我希望每个业务模块的 Service 层都有充分的单元测试，以便快速发现逻辑缺陷并保证核心模块覆盖率达标。

#### Acceptance Criteria

1. THE Unit_Test_Framework SHALL 为 22 个业务模块的每个 Service 类的 public 方法提供至少一个正常路径和一个异常路径的测试用例
2. WHEN 单元测试执行时，THE Unit_Test_Framework SHALL 使用 Mockito mock 所有 Mapper 和外部依赖，不连接真实数据库
3. THE Unit_Test_Framework SHALL 配置 Surefire 插件以 parallel=classes、threadCount=4、forkCount=1C 的并行策略执行
4. WHEN 核心模块（project, contract, budget, finance, material, machine, labor, subcontract）的 JaCoCo 行覆盖率低于 80% 时，THE CI_Pipeline SHALL 使构建失败
5. THE Unit_Test_Framework SHALL 集成 jqwik 1.9.1 对数值计算（金额、税率、产值）执行属性测试
6. WHEN Service 方法声明了可空参数时，THE Unit_Test_Framework SHALL 为该方法生成空值边界条件测试

### Requirement 3: L2 集成测试

**User Story:** 作为开发者，我希望通过集成测试验证完整 Spring 容器 + MyBatis-Plus + 真实数据库的交互，以便确认业务逻辑在真实环境下正确运行。

#### Acceptance Criteria

1. THE Integration_Test_Framework SHALL 使用 @SpringBootTest 注解启动完整 Spring 容器，直连服务器 MySQL（3306）和 Redis（6379）
2. WHEN 集成测试启动前，THE Integration_Test_Framework SHALL 通过 @BeforeAll 确保 Test_Tenant 存在并初始化测试数据（tenant_id=9999）
3. WHEN 集成测试执行完毕（无论成功或失败），THE Integration_Test_Framework SHALL 通过 @AfterAll 物理 DELETE 所有 tenant_id=9999 的数据并清除 Redis 中 test:t9999:* 键
4. THE Integration_Test_Framework SHALL 使用独立 Maven Profile（integration-test）和独立配置文件 application-integration-test.yml 管理连接信息
5. THE Integration_Test_Framework SHALL 覆盖关键场景：CRUD 完整往返、审批流推进、Flowable 流程实例创建与完成
6. IF 服务器 MySQL 或 Redis 连接失败，THEN THE Integration_Test_Framework SHALL 将相关测试标记为 @Disabled("Server unreachable") 并在 CI 输出跳过原因

### Requirement 4: L3 API 接口测试

**User Story:** 作为测试工程师，我希望通过模块化 Shell 脚本测试所有 REST 端点，以便验证 API 契约在部署后仍然正确。

#### Acceptance Criteria

1. THE API_Test_Runner SHALL 基于 verify-base.sh 提供模块化测试脚本，每个业务模块对应一个 test-api-{module}.sh 文件
2. WHEN API 测试脚本执行时，THE API_Test_Runner SHALL 复用 verify-base.sh 的登录、调用、日志基座能力
3. THE API_Test_Runner SHALL 覆盖每个模块的 CRUD 端点、审批接口和分页查询接口
4. WHEN API 响应返回时，THE API_Test_Runner SHALL 断言 HTTP 状态码（2xx）、响应 JSON 中 code 字段为 200、日志无异常堆栈
5. WHEN 测试创建了资源时，THE API_Test_Runner SHALL 在测试结束前调用 DELETE 接口清理已创建资源
6. WHEN 所有测试完成后，THE API_Test_Runner SHALL 输出结构化测试报告（通过数、失败数）并在有失败时以非零退出码结束

### Requirement 5: L4 端到端业务流

**User Story:** 作为质量工程师，我希望通过升级版 lifecycle-sim 脚本模拟完整业务生命周期（从创建到审批到完工），以便验证跨模块业务流程的正确性。

#### Acceptance Criteria

1. THE Lifecycle_Sim SHALL 使用 Test_Tenant（tenant_id=9999）执行所有业务操作，与生产数据完全隔离
2. THE Lifecycle_Sim SHALL 在严格断言模式（strictAssert=true）下，对每个阶段的 HTTP 响应断言状态码为 2xx 且业务码 code=200
3. WHEN 严格断言模式下某阶段失败时，THE Lifecycle_Sim SHALL 立即停止后续阶段并触发自动清理
4. THE Lifecycle_Sim SHALL 追踪所有已创建资源的 ID，在测试结束时按逆序 DELETE 避免外键冲突
5. THE Lifecycle_Sim SHALL 通过 trap EXIT 确保无论脚本如何退出（正常/异常/信号中断），都执行自动清理逻辑
6. WHEN 自动清理完成后，THE Lifecycle_Sim SHALL 执行兜底 SQL 清理（DELETE WHERE tenant_id=9999）确保无数据残留
7. THE Lifecycle_Sim SHALL 输出结构化 JSON 报告，包含各阶段通过/失败状态、已清理资源数

### Requirement 6: L5 前端 E2E 测试

**User Story:** 作为前端开发者，我希望通过 Playwright 双模式测试（真实模式 + Mock 模式）覆盖用户操作场景，以便同时保障端到端正确性和 UI 回归稳定性。

#### Acceptance Criteria

1. THE E2E_Runner SHALL 支持两种运行模式：真实模式（E2E_MODE=real，打服务器 :18081）和 Mock 模式（E2E_MODE=mock，本地 localhost:3000）
2. WHEN E2E_Runner 以真实模式运行时，THE E2E_Runner SHALL 通过真实登录流程（填写用户名密码 + 获取验证码 + 提交）获取 session
3. WHEN 真实模式登录成功后，THE E2E_Runner SHALL 保存 storageState 到 .auth/storage-state.json，后续测试复用避免重复登录
4. WHEN 真实模式的验证码获取接口（/api/v1/test/captcha-code）不可用时，THE E2E_Runner SHALL 降级到 Mock 模式执行 UI 回归测试，并在报告中标记为「降级运行」
5. THE E2E_Runner SHALL 在 playwright.config.ts 中配置独立 project（e2e-real 和 e2e），分别对应真实模式和 Mock 模式
6. THE E2E_Runner SHALL 支持 workers=4 并行执行以提高效率

### Requirement 7: CI/CD 流水线集成

**User Story:** 作为 DevOps 工程师，我希望 CI 流水线自动执行所有测试层级并在质量门槛不达标时阻止部署，以便持续保障代码质量。

#### Acceptance Criteria

1. THE CI_Pipeline SHALL 在 backend job 中恢复 `mvn test` 执行（移除 -DskipTests）
2. THE CI_Pipeline SHALL 移除 integration-test job 的 `if: false` 条件，恢复集成测试执行
3. WHEN backend job 完成后，THE CI_Pipeline SHALL 触发 integration-test job 通过 SSH 在服务器上执行 L3/L4 测试脚本
4. WHEN JaCoCo 覆盖率低于核心模块 80% 阈值时，THE CI_Pipeline SHALL 在 mvn verify 阶段失败并输出未覆盖方法清单
5. THE CI_Pipeline SHALL 在 integration-test job 中收集测试结果并在有失败时阻止后续部署步骤
6. THE CI_Pipeline SHALL 使用 GitHub Secrets 管理服务器凭证（SERVER_HOST、SSH_KEY），不将敏感信息硬编码到工作流文件

### Requirement 8: 数据隔离与安全

**User Story:** 作为系统管理员，我希望所有测试数据与生产数据严格隔离且测试完成后无残留，以便测试活动不影响生产环境的数据完整性。

#### Acceptance Criteria

1. THE Test_Infrastructure SHALL 确保所有测试层级（L2/L3/L4/L5）创建的数据均带有 tenant_id=9999 标记
2. WHEN 任何测试执行完毕后，THE TestDataCleaner SHALL 确保数据库中 tenant_id=9999 的行数为 0（物理 DELETE）
3. WHEN 任何测试执行完毕后，THE TestDataCleaner SHALL 确保 Redis 中 test:t9999:* 模式匹配的键数量为 0
4. THE Test_Infrastructure SHALL 确保测试端点（/api/v1/test/*）仅在 spring.profiles.active=test 时激活，生产环境自动禁用
5. THE Test_Infrastructure SHALL 确保 TOKEN_FILE 文件权限为 600，测试结束后删除
6. THE Test_Infrastructure SHALL 确保所有日志输出经 mask() 函数处理，不回显明文密码和 token
7. IF 测试过程中发生非预期异常导致清理逻辑未执行，THEN THE TestDataCleaner SHALL 提供手动清理脚本（cleanup-test-data.sh）支持运维人员按需清理残留数据

### Requirement 9: 统一编排与报告

**User Story:** 作为测试负责人，我希望有统一的编排脚本一键执行所有测试层级并生成汇总报告，以便快速了解整体质量状况。

#### Acceptance Criteria

1. THE Test_Infrastructure SHALL 提供 run-all-tests.sh 统一编排脚本，按 L1→L2→L3→L4→L5 顺序依次执行
2. WHEN 某层级测试全部通过后，THE run-all-tests.sh SHALL 继续执行下一层级
3. WHEN 某层级测试存在失败时，THE run-all-tests.sh SHALL 记录失败信息并根据配置决定继续还是停止（--fail-fast 选项）
4. WHEN 所有层级执行完毕后，THE run-all-tests.sh SHALL 输出汇总报告，包含每层级通过数/失败数/跳过数/耗时
5. THE run-all-tests.sh SHALL 支持通过参数选择执行特定层级（如 --layers=L1,L3 仅执行 L1 和 L3）
