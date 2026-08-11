# ZW-Insight 测试体系

## 架构概览�? 层测试金字塔

```
┌─────────────────────────────────────────�?�? L5  前端 E2E (Playwright 双模�?       �? �?最�? 覆盖用户操作场景
├─────────────────────────────────────────�?�? L4  端到端业务流 (lifecycle-sim-v2.sh) �? �?跨模块全链路验证
├─────────────────────────────────────────�?�? L3  API 接口测试 (Shell 脚本)          �? �?验证 REST 契约
├─────────────────────────────────────────�?�? L2  集成测试 (@SpringBootTest)         �? �?直连服务器数据库
├─────────────────────────────────────────�?�? L1  单元测试 (JUnit 5 + Mockito)       �? �?最�? 纯逻辑验证
└─────────────────────────────────────────�?```

| 层级 | 框架 | 目标 | 预期耗时 |
|------|------|------|---------|
| L1 | JUnit 5 + Mockito + AssertJ + jqwik | 22 模块 Service 层逻辑，当前门�?60%（pom jacoco check），80% 为阶段三目标 | < 60s |
| L2 | @SpringBootTest + Testcontainers（MySQL 8 + Redis），备选直连服务器 | CRUD 往返、审批流、Flowable 流程 | 3-5min |
| L3 | Shell 脚本 + verify-base.sh | 每模�?REST 端点 CRUD + 审批 + 分页 | 2-3min |
| L4 | lifecycle-sim-v2.sh | 19 阶段业务生命周期（立项→投标→合同→预算→收支→竣工→关闭） | 5-8min |
| L5 | Playwright 1.61 (真实模式/Mock 模式) | 前端登录、项目操作、审批流 UI | 3-5min |

---

## 各层级执行方�?
### L1 单元测试

```bash
# 在后端根目录执行
cd zw-insight-server
mvn test                              # 运行所有单元测�?mvn test -pl zw-project               # 仅运�?project 模块
mvn test -Dtest=ProjectServiceTest    # 运行单个测试�?```

并行配置：Surefire `parallel=classes, threadCount=4, forkCount=1C`

### L2 集成测试

```bash
cd zw-insight-server
mvn verify -Pintegration-test         # 使用独立 profile 执行
```

两种运行模式�?
1. **Testcontainers 本地模式（首选，hermetic�?*：`zw-app` �?`BaseIntegrationTest` 体系自动启动 MySQL 8 + Redis 容器，无 Docker �?`@EnabledIfDockerAvailable` 自动跳过。执行：`mvn test -Dtest="com.zwinsight.integration.*"`
2. **直连服务器模�?*：配置文�?`src/test/resources/application-integration-test.yml`

直连模式前置条件�?- 服务�?MySQL�?306）和 Redis�?379）可�?
> 受阻提醒：Docker/服务器不可达导致 L2 无法实跑时，按《测试受阻汇报规则》登记（�?AGENTS.md），禁止静默跳过�?
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

# 所�?L3 脚本
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

# һ������ƣ�ǰ��չʾ vs ��������ֶμ��ȶԣ�
npx playwright test --project=consistency-real

# ע��Mock ģʽ���� token + route() ���ؼ����ݣ����� 2026-08-11 �鵵ɾ����UI �ع�ͳһ����ʵģʽ
```

### 统一编排

```bash
bash tests/run-all-tests.sh                    # 执行全部 5 �?bash tests/run-all-tests.sh --layers=L1,L3     # 仅执行指定层�?bash tests/run-all-tests.sh --fail-fast        # 首层失败即停�?```

---

## 如何添加新测�?
### 添加 L1 单元测试

1. 在对应模�?`src/test/java/` 下创�?`{Module}ServiceTest.java`
2. 使用 `@ExtendWith(MockitoExtension.class)` 注解
3. Mock 所�?Mapper 和外部依赖（`@Mock`�?4. 通过 `@InjectMocks` 注入被测 Service
5. 每个 public 方法至少�?1 个正常路�?+ 1 个异常路径测�?
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

1. �?`src/test/java/.../integration/` 下创�?`{Module}IntegrationTest.java`
2. 继承 `IntegrationTestBase`
3. 使用 `@ActiveProfiles("integration-test")` + `@SpringBootTest`
4. 所有数据使�?`tenant_id=9999`
5. `@AfterAll` 中调�?`TestDataCleaner.cleanByTenantId(9999L)`

### 添加 L3 API 测试

1. �?`keys/` 下创�?`test-api-{module}.sh`
2. 文件开�?`source "$(dirname "$0")/verify-base.sh"`
3. 使用 `call` + `assert_http` 模式
4. 测试结束�?DELETE 已创建资�?
### 添加属性测�?(jqwik)

1. 在对应模块创�?`{Module}PropertyTest.java`
2. 使用 `@Property` + `@ForAll` 注解
3. 定义生成器约束输入空�?4. 验证业务不变�?恒等�?
---

## 常见问题排查

### Q: L2 集成测试�?"Connection refused"

服务�?MySQL/Redis 不可达。检查：
1. 服务�?IP 和端口是否正确（默认 129.204.3.200:3306 / 6379�?2. Docker 容器 `zwi-mysql` / `zwi-redis` 是否运行�?3. 防火墙是否放行端�?4. 测试将自动标记为 `@Disabled("Server unreachable")`

### Q: L3/L4 脚本�?"登录失败"

1. 确认 `verify-base.sh` 中的用户�?密码正确
2. 确认后端容器 `zwi-backend` 运行�?3. 检�?Redis 验证码是否可读取（`docker exec zwi-redis redis-cli keys "captcha:*"`�?
### Q: JaCoCo 覆盖率不达标导致构建失败

1. 查看 `target/site/jacoco/index.html` 了解哪些方法未覆�?2. 重点补充复杂分支逻辑的测�?3. 当前门槛：行覆盖�?�?0%（pom jacoco check，verify 阶段）；80% 为阶段三目标（见 `.kiro/specs/test-maturity-upgrade/tasks.md` 3.3�?4. CI �?JaCoCo 报告会上传为 artifact，可直接下载查看
5. **Windows 中文路径注意**：本机采集覆盖率需�?`-Djacoco.destFile=<ASCII路径>`（中文路径导�?agent 无法�?exec 文件�?
### Q: 测试数据残留怎么清理

```bash
# 手动清理脚本
bash keys/cleanup-test-data.sh
```

或直接连接数据库执行�?```sql
-- 清理 tenant_id=9999 的所有测试数�?DELETE FROM {table} WHERE tenant_id = 9999;
```

### Q: lifecycle-sim-v2 中途失败后数据没清�?
脚本使用 `trap EXIT` 机制，正常情况下会自动清理。如果容器崩溃等极端情况�?1. 运行 `bash keys/cleanup-test-data.sh` 手动清理
2. 检�?`tests/reports/lifecycle-sim-report.json` 了解失败阶段

---

## 数据隔离说明

### 租户隔离机制

所有测试数据使�?**tenant_id=9999**（自动化测试租户），与生产数据完全隔离：

```
生产数据: tenant_id �?{1, 2, 3, ...}  �?真实租户
测试数据: tenant_id = 9999             �?仅自动化测试使用
```

### 关键常量

| 常量 | �?| 说明 |
|------|---|------|
| TEST_TENANT_ID | 9999 | 测试租户 ID |
| TEST_TENANT_NAME | "自动化测试租�? | 租户名称 |
| REDIS_TEST_PREFIX | "test:t9999:" | Redis 键前缀 |

### 安全护栏

- `TestDataCleaner.cleanByTenantId()` 强制校验 tenantId==9999，非 9999 直接抛异�?- 拓扑逆序删除，避免外键约束冲�?- `trap EXIT` 确保异常退出时仍执行清�?- 兜底 SQL：`DELETE WHERE tenant_id=9999` 清理残留

### 测试端点安全

- `/api/v1/test/*` 接口仅在 `spring.profiles.active=test` 时激�?- 生产环境部署时该 profile 不启用，端点自动禁用
- Token 缓存文件权限 600，测试结束后删除

---

## 覆盖率报�?
### 本地生成

```bash
cd zw-insight-server
mvn test                    # 运行测试（JaCoCo agent 自动收集�?mvn jacoco:report           # 生成 HTML 报告
```

报告路径：`{module}/target/site/jacoco/index.html`

### CI 报告

CI 构建完成后，JaCoCo HTML 报告会上传为 GitHub Actions artifact�?1. 进入 GitHub Actions �?对应 workflow run
2. 下载 `jacoco-coverage-report` artifact
3. 解压后打开 `index.html` 查看详细覆盖�?
### 覆盖率门�?
| 阶段 | 行覆盖率要求 | 强制方式 |
|------|------------|---------|
| 当前（阶段一�?| �?0%（pom jacoco check，BUNDLE LINE�? `tests/coverage-baseline.json` 不回退守护 | verify 阶段门槛 + CI 基线比对 |
| 阶段三目�?| 核心 8 模块 �?0% | 达标�?pom check 调至 0.80，CI �?verify |

各模块实测基线见 `tests/TESTING-MATURITY.md` 附录 A；测试受阻处理规则见 AGENTS.md�?
---

## 相关文档

- 测试成熟度评估：`tests/TESTING-MATURITY.md`
- 升级三阶段任务：`.kiro/specs/test-maturity-upgrade/tasks.md`（含受阻项登记台账）



---

## Performance Testing (k6)

### Overview
Performance baseline testing using Grafana k6 for key business flows.

### Scripts Location
	ests/performance/:
- login.js - Login API performance test
- page-query.js - Page query performance test  
- payment-submit.js - Payment submission performance test

### Execution
**NOTE**: Due to production environment constraints, k6 is configured to run on the remote server during off-peak hours.

#### Server-side Execution (Recommended):
`ash
# Connect to production server
ssh user@server

# Run k6 performance tests
cd /root/zw-insight/tests/performance
bash run-k6.sh
`

#### Constraints:
- Concurrent users: �� 20
- Duration: �� 5 minutes per test
- Target tenant_id: 9999 (test tenant only)
- Schedule: Nightly low-peak window (23:00 UTC recommended)
- **Status**: Script and Docker image are ready on server, cron job disabled until manual execution needed

### Captcha Bridge
Captcha bridge script (keys/captcha-bridge.py) ensures real captcha validation during testing.
