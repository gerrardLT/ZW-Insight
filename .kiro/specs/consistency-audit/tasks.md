# Implementation Plan: Consistency Audit Engine

## Overview

基于 Node.js CLI 工具架构，使用 TypeScript + Commander.js + Vitest + fast-check 技术栈，实现中维智营三端代码库的 API 路径匹配、数据结构对齐、功能覆盖完整性审核工具。按模块化方式逐步构建扫描器、比对器、审核器和报告生成器。

## Tasks

- [x] 1. 项目初始化与核心接口定义
  - [x] 1.1 初始化项目结构与依赖配置
    - 创建 `tools/consistency-audit/` 目录结构
    - 初始化 `package.json`，配置 TypeScript 5.x、Commander.js 12.x、Vitest 2.x、fast-check 3.x 依赖
    - 创建 `tsconfig.json` 配置严格模式编译
    - 创建 `vitest.config.ts` 配置测试框架
    - _Requirements: 全部_

  - [x] 1.2 定义核心类型与接口
    - 创建 `src/types.ts`，定义 `BackendApiEntry`、`FrontendApiEntry`、`HttpMethod`、`InconsistencyType`、`Severity`、`InconsistencyItem`、`AuditStats`、`AuditReport`、`ModuleReport`、`AuditConfig` 等接口
    - 创建 `src/interfaces.ts`，定义 `IScanner<T>`、`IComparator`、`IReportGenerator` 接口
    - _Requirements: 1.2, 2.3, 3.3, 4.3, 4.4, 4.5, 7.1, 7.3_

- [x] 2. 后端扫描器实现
  - [x] 2.1 实现 BackendScanner 类
    - 创建 `src/scanners/backend-scanner.ts`
    - 实现类级 `@RequestMapping` 前缀提取正则
    - 实现方法级 `@GetMapping/@PostMapping/@PutMapping/@DeleteMapping/@RequestMapping` 注解解析
    - 实现类级路径与方法级路径拼接逻辑（处理双斜杠问题）
    - 遍历 zw-insight-server 各模块 controller 目录，提取 Java 文件中的 API 声明
    - 产出 `BackendApiEntry[]`，覆盖全部 20 个业务模块
    - _Requirements: 1.1, 1.2, 1.3, 1.4_

  - [ ]* 2.2 编写 Property Test：后端注解解析正确性
    - **Property 1: 后端注解解析正确性**
    - 使用 fast-check 生成合法 Java Controller 源码片段，验证 httpMethod 与注解类型一致且 fullPath 为正确拼接
    - **Validates: Requirements 1.1, 1.3**

  - [ ]* 2.3 编写 Property Test：路径拼接一致性
    - **Property 2: 路径拼接一致性**
    - 使用 fast-check 生成 classPrefix 和 methodPath 组合，验证拼接结果以 classPrefix 开头、不含双斜杠
    - **Validates: Requirements 1.3**

  - [ ]* 2.4 编写单元测试：BackendScanner
    - 在 `tests/unit/backend-scanner.test.ts` 中使用 fixtures 中的 Java 源码片段验证解析结果
    - 验证全部 20 个模块可被正确识别
    - _Requirements: 1.1, 1.2, 1.3, 1.4_

- [x] 3. PC 前端扫描器实现
  - [x] 3.1 实现 PcWebScanner 类
    - 创建 `src/scanners/pc-web-scanner.ts`
    - 使用 TypeScript Compiler API 解析 AST，识别 `request.get/post/put/delete` 调用
    - 实现模板字符串规范化（`${id}` → `{id}`）
    - 提取文件名、函数名、HTTP 方法、请求路径、路径参数、所属模块
    - 扫描 `zw-insight-web/src/api/` 下全部 16 个 TypeScript 文件
    - _Requirements: 2.1, 2.2, 2.3_

  - [ ]* 3.2 编写 Property Test：模板字符串规范化
    - **Property 3: 模板字符串规范化**
    - 使用 fast-check 生成含模板变量的路径字符串，验证 `${xxx}` 全替换为 `{xxx}`，无 `$` 残留，参数列表长度正确
    - **Validates: Requirements 2.2, 3.2**

  - [ ]* 3.3 编写 Property Test：解析器输出完整性
    - **Property 4: 解析器输出完整性**
    - 验证扫描器产出的每个条目均包含非空 module、httpMethod、filePath 和有效路径字段
    - **Validates: Requirements 1.2, 2.3, 3.3**

  - [ ]* 3.4 编写单元测试：PcWebScanner
    - 在 `tests/unit/pc-web-scanner.test.ts` 中使用 TypeScript fixtures 验证解析结果
    - 验证模板字符串、普通字符串路径均可正确提取
    - _Requirements: 2.1, 2.2, 2.3_

- [x] 4. 移动端扫描器实现
  - [x] 4.1 实现 MobileScanner 类
    - 创建 `src/scanners/mobile-scanner.ts`
    - 使用 TypeScript Compiler API 解析 `request({ url, method })` 调用模式
    - 处理 method 字段缺省时默认为 GET 的逻辑
    - 扫描 `zw-insight-app/src/api/` 下的 TypeScript 文件
    - _Requirements: 3.1, 3.2, 3.3_

  - [ ]* 4.2 编写单元测试：MobileScanner
    - 在 `tests/unit/mobile-scanner.test.ts` 中使用 fixtures 验证解析结果
    - 验证 method 默认值、模板字符串处理逻辑
    - _Requirements: 3.1, 3.2, 3.3_

- [x] 5. Checkpoint - 确保扫描器测试通过
  - 确保所有扫描器单元测试和 Property 测试通过，如有问题请询问用户。

- [x] 6. API 路径一致性比对引擎实现
  - [x] 6.1 实现 ConsistencyComparator 类
    - 创建 `src/comparators/consistency-comparator.ts`
    - 实现 `normalizePath` 方法：移除 `/api` 前缀差异，统一路径参数格式
    - 实现 `pathsMatch` 方法：支持路径参数通配匹配
    - 实现 `compare` 方法：逐一检测前端多余接口、后端孤立接口、HTTP 方法不匹配
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5, 4.6_

  - [ ]* 6.2 编写 Property Test：路径规范化幂等性
    - **Property 5: 路径规范化幂等性**
    - 验证 `normalizePath("/api" + P) === normalizePath(P)` 和 `normalizePath(normalizePath(P)) === normalizePath(P)`
    - **Validates: Requirements 4.6**

  - [ ]* 6.3 编写 Property Test：比对引擎分类正确性
    - **Property 6: 比对引擎分类正确性**
    - 使用 fast-check 生成前后端 API 条目集合，验证分类逻辑正确且不会出现同一配对被标记为多种冲突类型
    - **Validates: Requirements 4.1, 4.2, 4.3, 4.4, 4.5**

  - [ ]* 6.4 编写单元测试：ConsistencyComparator
    - 在 `tests/unit/comparator.test.ts` 中验证路径规范化、路径匹配、各类不一致项检测
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5, 4.6_

- [x] 7. 功能覆盖率审核器实现
  - [x] 7.1 实现 CoverageAuditor 类
    - 创建 `src/auditors/coverage-auditor.ts`
    - 定义 `FeatureMapping` 配置结构，将 REQ-031 功能表映射为配置数据
    - 实现功能覆盖检测：验证 PC 端"二、业务管理"和"四、平台管理功能"覆盖情况
    - 实现功能覆盖检测：验证移动端"三、手机管理功能"覆盖情况
    - 检测功能缺失和超范围实现
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5_

  - [x] 7.2 实现移动端与 PC 端功能差异标注逻辑
    - 在 CoverageAuditor 中实现平台差异分析
    - 区分 PC 端独有、移动端独有功能列表
    - 根据 REQ-031 判断差异是否合理（如平台管理仅需 PC 端、定位签到仅需移动端）
    - 检测不合理差异并记录为 `MOBILE_FEATURE_MISSING` 或 `PC_FEATURE_MISSING`
    - _Requirements: 9.1, 9.2, 9.3, 9.4_

  - [ ]* 7.3 编写 Property Test：功能覆盖检测完备性
    - **Property 7: 功能覆盖检测完备性**
    - 验证 pcRequired=true 且未匹配时必须产出 FEATURE_MISSING，已匹配时不产出缺失项
    - **Validates: Requirements 5.2, 5.3, 5.4**

  - [ ]* 7.4 编写单元测试：CoverageAuditor
    - 验证功能缺失检测、超范围实现检测、平台差异标注
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5, 9.1, 9.2, 9.3, 9.4_

- [x] 8. 字段一致性审核器实现
  - [x] 8.1 实现 FieldAuditor 类
    - 创建 `src/auditors/field-auditor.ts`
    - 实现 Java 实体/DTO 字段提取（正则匹配 private 字段和 @NotNull/@NotBlank 注解）
    - 实现 Vue 组件 v-model 绑定字段提取
    - 实现驼峰/下划线命名转换函数（`normalizeFieldName`）
    - 实现字段比对逻辑：字段名不匹配、前端多余字段、必填字段前端缺失
    - _Requirements: 6.1, 6.2, 6.3, 6.4, 6.5_

  - [ ]* 8.2 编写 Property Test：字段名大小写转换 Round-Trip
    - **Property 8: 字段名大小写转换 Round-Trip**
    - 验证 `toCamelCase(toSnakeCase(fieldName)) === fieldName`（对合法驼峰命名）
    - **Validates: Requirements 6.3**

  - [ ]* 8.3 编写 Property Test：字段审核分类正确性
    - **Property 9: 字段审核分类正确性**
    - 验证不一致项数量等于 |F\B| + |{b∈B : b.isRequired ∧ b∉F}|
    - **Validates: Requirements 6.4, 6.5**

  - [ ]* 8.4 编写单元测试：FieldAuditor
    - 在 `tests/unit/field-auditor.test.ts` 中验证字段提取和比对逻辑
    - _Requirements: 6.1, 6.2, 6.3, 6.4, 6.5_

- [x] 9. 路径规范审核器实现
  - [x] 9.1 实现 PathAuditor 类
    - 创建 `src/auditors/path-auditor.ts`
    - 实现路径前缀规范验证：`/api/v1/{module}/` 格式校验
    - 实现 RESTful 命名风格校验：kebab-case 验证
    - 实现前端路径模块名与后端模块名一致性校验
    - _Requirements: 10.1, 10.2, 10.3, 10.4_

  - [ ]* 9.2 编写 Property Test：路径前缀规范验证
    - **Property 12: 路径前缀规范验证**
    - 验证符合规范的路径不产出不一致项，不符合规范的路径产出 PATH_NAMING_VIOLATION
    - **Validates: Requirements 10.1, 10.2**

  - [ ]* 9.3 编写 Property Test：RESTful 命名风格验证
    - **Property 13: RESTful 命名风格验证**
    - 验证 kebab-case 路径不产出不一致项，含大写/下划线的路径产出 RESTFUL_NAMING_VIOLATION
    - **Validates: Requirements 10.4**

  - [ ]* 9.4 编写单元测试：PathAuditor
    - 在 `tests/unit/path-auditor.test.ts` 中验证路径前缀和 RESTful 命名校验逻辑
    - _Requirements: 10.1, 10.2, 10.3, 10.4_

- [x] 10. Checkpoint - 确保审核器测试通过
  - 确保所有审核器和比对引擎的单元测试和 Property 测试通过，如有问题请询问用户。

- [x] 11. 报告生成器实现
  - [x] 11.1 实现 ReportGenerator 类
    - 创建 `src/reporters/report-generator.ts`
    - 实现按模块分组逻辑（`groupByModule`）
    - 实现严重程度分类规则（`classifySeverity`）：Critical（路径不存在）、Major（HTTP方法错误/必填字段缺失）、Minor（命名风格差异/超范围实现）
    - 实现 JSON 格式报告输出
    - 实现 Markdown 格式报告输出（含分类汇总、模块分组、覆盖率统计）
    - _Requirements: 7.1, 7.2, 7.3, 7.4, 7.5_

  - [ ]* 11.2 编写 Property Test：报告统计不变量
    - **Property 10: 报告统计不变量**
    - 验证 totalInconsistencies 等于输入长度，bySeverity 和 byType 各值之和等于 totalInconsistencies
    - **Validates: Requirements 7.1, 7.4, 7.5**

  - [ ]* 11.3 编写 Property Test：严重程度分类确定性
    - **Property 11: 严重程度分类确定性**
    - 验证相同 type 的不一致项始终返回相同严重程度
    - **Validates: Requirements 7.3**

  - [ ]* 11.4 编写单元测试：ReportGenerator
    - 验证分组逻辑、严重程度分类、JSON/Markdown 输出格式
    - _Requirements: 7.1, 7.2, 7.3, 7.4, 7.5_

- [x] 12. CLI 入口与审核引擎整合
  - [x] 12.1 实现 AuditEngine 主引擎类
    - 创建 `src/engine.ts`
    - 实现配置加载逻辑
    - 编排并行扫描（BackendScanner、PcWebScanner、MobileScanner）
    - 编排顺序执行比对和审核（ConsistencyComparator、CoverageAuditor、FieldAuditor、PathAuditor）
    - 汇总不一致项并调用 ReportGenerator 生成报告
    - 实现模块清单校验（验证覆盖全部 20 个模块）和"待前端对接"状态标注
    - _Requirements: 8.1, 8.2, 8.3, 8.4_

  - [x] 12.2 实现 CLI 入口
    - 创建 `src/cli.ts`
    - 使用 Commander.js 定义命令行参数：`--root`（项目根目录）、`--output`（输出目录）、`--format`（输出格式）、`--modules`（指定模块）
    - 实现 CLI 退出码逻辑（0=无 Critical，1=有 Critical，2=配置错误，3=运行时错误）
    - 配置 `package.json` 的 `bin` 字段，支持 `npx zw-audit` 调用
    - _Requirements: 全部_

  - [ ]* 12.3 编写集成测试
    - 在 `tests/integration/full-audit.test.ts` 中使用 fixtures 模拟完整审核流程
    - 验证 CLI 参数解析、扫描→比对→审核→报告全链路
    - 验证退出码和报告文件生成
    - _Requirements: 全部_

- [x] 13. 测试 Fixtures 与功能映射配置
  - [x] 13.1 创建测试 Fixtures
    - 创建 `tests/fixtures/java/` 目录，编写示例 Java Controller 源码片段
    - 创建 `tests/fixtures/typescript/` 目录，编写示例 TypeScript API 调用文件
    - 创建 `tests/fixtures/vue/` 目录，编写示例 Vue 组件（含 v-model 绑定）
    - _Requirements: 全部_

  - [x] 13.2 创建功能映射配置文件
    - 创建 `src/config/feature-mappings.ts`
    - 根据 REQ-031 功能表定义所有功能点与后端模块、前端文件的映射关系
    - 区分 PC 端必须、移动端必须、双端共有的功能标记
    - _Requirements: 5.1, 9.4_

- [x] 14. Final Checkpoint - 确保全部测试通过
  - 确保所有单元测试、Property 测试、集成测试通过，如有问题请询问用户。

## Notes

- 标记 `*` 的子任务为可选任务，可跳过以加速 MVP 交付
- 每个任务引用了具体需求条目，确保可追溯性
- Checkpoint 任务确保增量验证
- Property 测试验证通用正确性属性（共 13 个 Property）
- 单元测试验证具体示例和边界情况
- 实现语言：TypeScript（与 design.md 一致）
- 测试框架：Vitest + fast-check
- CLI 框架：Commander.js

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1"] },
    { "id": 1, "tasks": ["1.2", "13.1"] },
    { "id": 2, "tasks": ["2.1", "3.1", "4.1", "13.2"] },
    { "id": 3, "tasks": ["2.2", "2.3", "2.4", "3.2", "3.3", "3.4", "4.2"] },
    { "id": 4, "tasks": ["6.1", "7.1", "8.1", "9.1"] },
    { "id": 5, "tasks": ["6.2", "6.3", "6.4", "7.2", "7.3", "7.4", "8.2", "8.3", "8.4", "9.2", "9.3", "9.4"] },
    { "id": 6, "tasks": ["11.1"] },
    { "id": 7, "tasks": ["11.2", "11.3", "11.4"] },
    { "id": 8, "tasks": ["12.1"] },
    { "id": 9, "tasks": ["12.2"] },
    { "id": 10, "tasks": ["12.3"] }
  ]
}
```
