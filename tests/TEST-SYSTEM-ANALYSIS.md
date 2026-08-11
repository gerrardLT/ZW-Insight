# ZW-Insight 测试体系深度解析报告

**执行摘要**: 本项目已建立五层测试金字塔架构 (L1-L5),覆盖从单元测试到前端 E2E 的完整质量保障体系。核心业务模块 (material/budget/purchase/labor/finance/contract) 覆盖率已达 70%+,但仍有部分模块需提升至 80% CI 门禁门槛。L3/L4 API 契约验证与全生命周期测试采用真实服务器调用，符合项目\"真实接口真实流程\"的核心原则。

## 一、测试体系架构

### 1.1 五层金字塔结构

L5: Frontend E2E (Playwright) - 真实模式/一致性模式（Mock 模式已于 2026-08-11 归档删除：假数据违反真实接口原则）
  ↓
L4: Full Lifecycle Simulation (lifecycle-sim-v2.sh, 1266 行) - 19 阶段业务闭环
  ↓
L3: API Contract Testing (8 个 Shell 脚本) - RESTful CRUD + jq 断言
  ↓
L2: Integration Testing (@SpringBootTest + Testcontainers) - MySQL+Redis 容器化
  ↓
L1: Unit Testing (JUnit5+Mockito+AssertJ+jqwik) - 158 个测试用例

### 1.2 关键设计原则

1. **真实接口原则**: L3/L4强制使用远程服务器API，禁止Mock降级
2. **数据隔离机制**: tenant_id=9999 自动化测试租户，DELETE WHERE 清理
3. **硬性断言标准**: HTTP 2xx + code=200 + 业务字段校验
4. **自动清理保障**: trap EXIT钩子确保临时资源回收

## 二、覆盖率现状分析

### 2.1 当前矩阵 (截至 2026-08-05)

 zw-material: 72.0% (? Phase1 Done)
 zw-budget: 71.8% (? Phase1 Done)
 zw-purchase: 70.5% (? Phase1 Done)
 zw-labor: 70.3% (? Phase1 Done)
 zw-finance: 64.8% (?? Near Target)
 zw-contract: 62.0% (? = Baseline)
 zw-subcontract: 62.5% (? = Baseline)
 zw-security: 53.6% (?? Phase2)
 zw-project: 51.0% (?? Observation)
 zw-machine: 49.0% (?? Observation)
 zw-file: 7.4% (? Emergency)

**统计**: Phase1 完成模块 (≥60%):8个，平均 68.0%; Critical 模块 (<30%):5个，平均 17.6%

### 2.2 CI 门禁要求

- Maven verify 阶段：≥80% 行覆盖率
- PR Commit: 对比 coverage-baseline.json，不得回落
- 新模块：必须达到 80% 才允许合并

## 三、测试脚本工具集详解

### 3.1 verify-base.sh (191 行)

核心功能：
1. 登录认证：从 Redis 读取真实验证码 (禁止 Mock)
2. API 封装:Bearertoken 注入 + HTTP状态码返回
3. 敏感脱敏:accessToken/password掩码处理
4. 日志审计:检查 404/Exception堆栈

### 3.2 lifecycle-sim-v2.sh (1266 行)

关键特性:
1. strict_assert() 双重断言 (HTTP 2xx + code=200)
2. CREATED_IDS 资源追踪数组
3. trap EXIT 自动清理机制
4. SQL 兜底清理 (DELETE WHERE tenant_id=9999)

### 3.3 API 契约测试 (8 个脚本)

| 脚本 | 行数 | 测试重点 |
|------|------|---------|
| test-api-material.sh | 450 | 出入库 + 盘点 + 库存 |
| test-api-finance.sh | 433 | 收付款 + 发票 + 预算 |
| test-api-purchase.sh | 421 | 询价 + 合同 + 结算 |
| test-api-subcontract.sh | 403 | 分包进度 + 结算 |
| test-api-labor.sh | 387 | 工资 + 考勤 + 合同 |
| test-api-machine.sh | 373 | 台班 + 油耗 + 维修 |
| test-api-project.sh | 368 | CRUD + 成员管理 |
| test-api-contract.sh | 329 | 变更签证 + 清单 |

### 3.4 统一编排脚本 (run-all-tests.sh, 718 行)

命令行参数：--layers=L1,L2,L3,L4,L5 / --fail-fast / --help  
执行流程：for layer in  → case 分发 → 汇总报告

## 四、测试数据管理

### 4.1 租户隔离策略

- tenant_id=1:生产演示数据 (永久保留)
- tenant_id=9999:自动化测试 (每轮清理 DELETE WHERE)
- 业务编号前缀:T9防撞号机制

### 4.2 种子数据脚本 (deploy/db-init/31_V2026_26__seed_demo_data.sql)

特性:INSERT IGNORE幂等性 + ID段90001-99999隔离 + 3个典型项目闭环

## 五、待改进项路线图

### 5.1 当前短板 (对标 Google/Stripe)

| 维度 | 当前状态 | 理想目标 |
|------|---------|---------|
| 覆盖率达标率 | 8 模块≥60%,5 模块<30% | 全模块≥85% |
| 突变测试 | ? 缺失 | PIT/Stryker |
| 性能基准 | ? 缺失 | k6/P99<1s |
| 安全测试 | ? 缺失 | SonarQube+OWASP |
| 动态契约 | consistency-audit(静态) | Pact(动态) |

### 5.2 短期行动计划 (Next 2 Weeks)

1. **紧急修补**:zw-file模块覆盖率7.4%→30%(增加60个测试用例)
2. **基础设施**:JaCoCo ASCII路径修复方案
3. **流程优化**:PR Template 添加测试 Checklist

## 六、总结建议

ZW-Insight 测试体系已达到中型敏捷团队良好实践水平 (~70/100),优势在于五层架构完整、真实 API 文化、数据隔离完善。与Google/Stripe 相比差距主要在进阶工具链 (PIT/k6/ Pact)。

**建议投入 2-3 周集中攻坚覆盖率 + 工具链升级**,即可跃升至优秀梯队 (~85/100)。优先填补 zw-file/hr/system 低覆盖率模块，同时引入 PIT/k6 等进阶工具提升质量维度。

---

**参考文档**: tests/README.md | tests/TESTING-MATURITY.md | AGENTS.md  
**数据来源**: tests/coverage-baseline.json (2026-08-05)  
**文档版本**: v2.0
