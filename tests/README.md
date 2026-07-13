# ZW-Insight 测试体系

## 架构概览：5 层测试金字塔

```
┌─────────────────────────────────────────┐
│  L5  前端 E2E (Playwright 双模式)       │  ← 最慢, 覆盖用户操作场景
├─────────────────────────────────────────┤
│  L4  端到端业务流 (lifecycle-sim-v2.sh) │  ← 跨模块全链路验证
├─────────────────────────────────────────┤
│  L3  API 接口测试 (Shell 脚本)          │  ← 验证 REST 契约
├─────────────────────────────────────────┤
│  L2  集成测试 (@SpringBootTest)         │  ← 直连服务器数据库
├─────────────────────────────────────────┤
│  L1  单元测试 (JUnit 5 + Mockito)       │  ← 最快, 纯逻辑验证
└─────────────────────────────────────────┘
```

| 层级 | 框架 | 目标 | 预期耗时 |
|------|------|------|---------|
| L1 | JUnit 5 + Mockito + AssertJ + jqwik | 22 模块 Service 层逻辑，核心模块覆盖率 ≥80% | < 60s |
| L2 | @SpringBootTest + MySQL/Redis 直连 | CRUD 往返、审批流、Flowable 流程 | 3-5min |
| L3 | Shell 脚本 + verify-base.sh | 每模块 REST 端点 CRUD + 审批 + 分页 | 2-3min |
| L4 | lifecycle-sim-v2.sh | 10 阶段业务生命周期（创建→完工→归档） | 5-8min |
| L5 | Playwright 1.61 (真实模式/Mock 模式) | 前端登录、项目操作、审批流 UI | 3-5min |

---

## 各层级执行方式

### L1 单元测试

```bash
# 在后端根目录执行
cd zw-insight-server
mvn test                              # 运行所有单元测试
mvn test -pl zw-project               # 仅运行 project 模块
mvn test -Dtest=ProjectServiceTest    # 运行单个测试类
```

并行配置：Surefire `parallel=classes, threadCount=4, forkCount=1C`

### L2 集成测试

```bash
cd zw-insight-server
mvn verify -Pintegration-test         # 使用独立 profile 执行
```

前置条件：
- 服务器 MySQL（3306）和 Redis（6379）可达
- 配置文件：`src/test/resources/application-integration-test.yml`

### L3 API 接口测试

```bash
# 单个模块
bash keys/test-api-project.sh
bash keys/test-api-contract.sh
bash keys/test-api-finance.sh
bash keys/test-api-purchase.sh
bash keys/test-api-material.sh
bash keys/test-api-machine.sh
bash keys/test-api-labor.sh
bash keys/test-api-subcontract.sh

# 所有 L3 脚本
for script in keys/test-api-*.sh; do bash "$script"; done
```

### L4 端到端业务流

```bash
bash keys/lifecycle-sim-v2.sh
```

输出报告位于：`tests/reports/lifecycle-sim-report.json`

### L5 前端 E2E

```bash
cd zw-insight-web

# 真实模式（打服务器）
npx playwright test --project=e2e-real

# Mock 模式（本地 UI 回归）
npx playwright test --project=e2e
```

### 统一编排

```bash
bash tests/run-all-tests.sh                    # 执行全部 5 层
bash tests/run-all-tests.sh --layers=L1,L3     # 仅执行指定层级
bash tests/run-all-tests.sh --fail-fast        # 首层失败即停止
```

---

## 如何添加新测试

### 添加 L1 单元测试

1. 在对应模块 `src/test/java/` 下创建 `{Module}ServiceTest.java`
2. 使用 `@ExtendWith(MockitoExtension.class)` 注解
3. Mock 所有 Mapper 和外部依赖（`@Mock`）
4. 通过 `@InjectMocks` 注入被测 Service
5. 每个 public 方法至少写 1 个正常路径 + 1 个异常路径测试

```java
@ExtendWith(MockitoExtension.class)
class YourServiceTest {
    @Mock private YourMapper yourMapper;
    @InjectMocks private YourServiceImpl yourService;

    @Test
    @DisplayName("正常路径 - 描述")
    void method_happyPath() {
        // Given - 设置 mock 行为
        when(yourMapper.selectById(1L)).thenReturn(new YourEntity());
        // When - 调用被测方法
        var result = yourService.getById(1L);
        // Then - 断言
        assertThat(result).isNotNull();
    }
}
```

### 添加 L2 集成测试

1. 在 `src/test/java/.../integration/` 下创建 `{Module}IntegrationTest.java`
2. 继承 `IntegrationTestBase`
3. 使用 `@ActiveProfiles("integration-test")` + `@SpringBootTest`
4. 所有数据使用 `tenant_id=9999`
5. `@AfterAll` 中调用 `TestDataCleaner.cleanByTenantId(9999L)`

### 添加 L3 API 测试

1. 在 `keys/` 下创建 `test-api-{module}.sh`
2. 文件开头 `source "$(dirname "$0")/verify-base.sh"`
3. 使用 `call` + `assert_http` 模式
4. 测试结束前 DELETE 已创建资源

### 添加属性测试 (jqwik)

1. 在对应模块创建 `{Module}PropertyTest.java`
2. 使用 `@Property` + `@ForAll` 注解
3. 定义生成器约束输入空间
4. 验证业务不变量/恒等式

---

## 常见问题排查

### Q: L2 集成测试报 "Connection refused"

服务器 MySQL/Redis 不可达。检查：
1. 服务器 IP 和端口是否正确（默认 129.204.3.200:3306 / 6379）
2. Docker 容器 `zwi-mysql` / `zwi-redis` 是否运行中
3. 防火墙是否放行端口
4. 测试将自动标记为 `@Disabled("Server unreachable")`

### Q: L3/L4 脚本报 "登录失败"

1. 确认 `verify-base.sh` 中的用户名/密码正确
2. 确认后端容器 `zwi-backend` 运行中
3. 检查 Redis 验证码是否可读取（`docker exec zwi-redis redis-cli keys "captcha:*"`）

### Q: JaCoCo 覆盖率不达标导致构建失败

1. 查看 `target/site/jacoco/index.html` 了解哪些方法未覆盖
2. 重点补充复杂分支逻辑的测试
3. 核心模块阈值 ≥80%，非核心模块 ≥50%
4. CI 中 JaCoCo 报告会上传为 artifact，可直接下载查看

### Q: 测试数据残留怎么清理

```bash
# 手动清理脚本
bash keys/cleanup-test-data.sh
```

或直接连接数据库执行：
```sql
-- 清理 tenant_id=9999 的所有测试数据
DELETE FROM {table} WHERE tenant_id = 9999;
```

### Q: lifecycle-sim-v2 中途失败后数据没清理

脚本使用 `trap EXIT` 机制，正常情况下会自动清理。如果容器崩溃等极端情况：
1. 运行 `bash keys/cleanup-test-data.sh` 手动清理
2. 检查 `tests/reports/lifecycle-sim-report.json` 了解失败阶段

---

## 数据隔离说明

### 租户隔离机制

所有测试数据使用 **tenant_id=9999**（自动化测试租户），与生产数据完全隔离：

```
生产数据: tenant_id ∈ {1, 2, 3, ...}  ← 真实租户
测试数据: tenant_id = 9999             ← 仅自动化测试使用
```

### 关键常量

| 常量 | 值 | 说明 |
|------|---|------|
| TEST_TENANT_ID | 9999 | 测试租户 ID |
| TEST_TENANT_NAME | "自动化测试租户" | 租户名称 |
| REDIS_TEST_PREFIX | "test:t9999:" | Redis 键前缀 |

### 安全护栏

- `TestDataCleaner.cleanByTenantId()` 强制校验 tenantId==9999，非 9999 直接抛异常
- 拓扑逆序删除，避免外键约束冲突
- `trap EXIT` 确保异常退出时仍执行清理
- 兜底 SQL：`DELETE WHERE tenant_id=9999` 清理残留

### 测试端点安全

- `/api/v1/test/*` 接口仅在 `spring.profiles.active=test` 时激活
- 生产环境部署时该 profile 不启用，端点自动禁用
- Token 缓存文件权限 600，测试结束后删除

---

## 覆盖率报告

### 本地生成

```bash
cd zw-insight-server
mvn test                    # 运行测试（JaCoCo agent 自动收集）
mvn jacoco:report           # 生成 HTML 报告
```

报告路径：`{module}/target/site/jacoco/index.html`

### CI 报告

CI 构建完成后，JaCoCo HTML 报告会上传为 GitHub Actions artifact：
1. 进入 GitHub Actions → 对应 workflow run
2. 下载 `jacoco-coverage-report` artifact
3. 解压后打开 `index.html` 查看详细覆盖率

### 覆盖率门槛

| 模块类型 | 行覆盖率要求 |
|---------|------------|
| 核心模块 (project, contract, budget, finance, material, machine, labor, subcontract) | ≥ 80% |
| 非核心模块 (system, common 等) | ≥ 50% |
