# Requirements Document

## Introduction

本文档定义中维智营（ZW Insight）系统三端（移动端 uni-app、PC Web 端 Vue3、后端 Spring Boot）功能一致性与接口数据一致性审核的需求规范。审核目标是确保前后端 API 路径匹配、数据结构对齐、功能覆盖完整，产出不一致项清单和修复建议。

## Glossary

- **Audit_Engine**: 一致性审核执行引擎，负责扫描前后端代码并比对 API 路径、数据字段
- **API_Path**: 后端 Controller 通过 @RequestMapping 注解声明的 HTTP 路径，格式为 /api/v1/{module}/xxx
- **Frontend_API_Call**: 前端代码中通过 request 工具函数发起的 HTTP 请求定义
- **PC_Web**: 基于 Vue3 的 PC 端管理系统，源码位于 zw-insight-web
- **Mobile_App**: 基于 uni-app 的移动端应用，源码位于 zw-insight-app
- **Backend_Server**: 基于 Spring Boot 的后端服务，源码位于 zw-insight-server
- **Module**: 后端按业务划分的子模块（system、finance、site、material、machine、project、workflow、dashboard、archive、contract、budget、purchase、labor、subcontract、hr、tender、basedata、message、security、file）
- **REQ-031**: 工程项目管理系统功能表文档，定义系统的完整功能需求
- **Inconsistency_Report**: 审核产出物，记录所有不一致项及修复建议的结构化清单
- **Field_Mapping**: 前端表单字段名与后端实体类属性名之间的对应关系

## Requirements

### Requirement 1: API路径扫描与注册

**User Story:** 作为审核执行者，我希望系统能自动扫描后端所有Controller的API路径声明，以便建立完整的后端API注册表作为审核基准。

#### Acceptance Criteria

1. WHEN 审核启动时, THE Audit_Engine SHALL 扫描 zw-insight-server 下所有 Module 的 controller 包中的 Java 文件，提取 @RequestMapping、@GetMapping、@PostMapping、@PutMapping、@DeleteMapping 注解声明的完整路径
2. THE Audit_Engine SHALL 将每个后端 API 路径解析为结构化记录，包含：模块名、Controller 类名、方法名、HTTP 方法、完整路径、请求参数类型、响应类型
3. WHEN 后端 Controller 类级别存在 @RequestMapping 前缀时, THE Audit_Engine SHALL 将类级别路径与方法级别路径拼接为完整 API 路径
4. THE Audit_Engine SHALL 生成后端 API 注册表，覆盖全部 20 个业务模块（system、finance、site、material、machine、project、workflow、dashboard、archive、contract、budget、purchase、labor、subcontract、hr、tender、basedata、message、security、file）

### Requirement 2: PC前端API路径扫描

**User Story:** 作为审核执行者，我希望系统能自动扫描PC前端所有API调用声明，以便与后端API注册表进行比对。

#### Acceptance Criteria

1. THE Audit_Engine SHALL 扫描 zw-insight-web/src/api/ 目录下全部 16 个 TypeScript 文件（system、finance、site、material、machine、project、dashboard、archive、contract、budget、purchase、labor、subcontract、hr、tender、basedata），提取所有 request.get/post/put/delete 调用的路径
2. WHEN PC 前端 API 调用路径中包含模板字符串变量时, THE Audit_Engine SHALL 将变量识别为路径参数占位符（如 `${id}` 对应 `{id}`）
3. THE Audit_Engine SHALL 将每个 PC 前端 API 调用解析为结构化记录，包含：文件名、函数名、HTTP 方法、请求路径、请求参数类型、所属业务模块

### Requirement 3: 移动端API路径扫描

**User Story:** 作为审核执行者，我希望系统能自动扫描移动端所有API调用声明，以便与后端API注册表进行比对。

#### Acceptance Criteria

1. THE Audit_Engine SHALL 扫描 zw-insight-app/src/api/ 目录下全部 TypeScript 文件（auth.ts、common.ts），提取所有 request 调用中 url 字段声明的路径
2. WHEN 移动端 API 调用路径中包含模板字符串变量时, THE Audit_Engine SHALL 将变量识别为路径参数占位符
3. THE Audit_Engine SHALL 将每个移动端 API 调用解析为结构化记录，包含：文件名、函数名、HTTP 方法（通过 method 字段或默认 GET）、请求路径、所属业务模块

### Requirement 4: API路径一致性比对

**User Story:** 作为审核执行者，我希望系统能自动比对前端API调用路径与后端API声明路径，以便发现路径不匹配的问题。

#### Acceptance Criteria

1. WHEN 比对 PC 前端 API 路径与后端 API 注册表时, THE Audit_Engine SHALL 逐一验证每个前端 API 路径在后端是否存在对应的 Controller 方法，且 HTTP 方法一致
2. WHEN 比对移动端 API 路径与后端 API 注册表时, THE Audit_Engine SHALL 逐一验证每个移动端 API 路径在后端是否存在对应的 Controller 方法，且 HTTP 方法一致
3. WHEN 前端 API 路径在后端不存在对应路径时, THE Audit_Engine SHALL 记录为"前端多余接口"类型不一致项
4. WHEN 后端 API 路径在两个前端均不存在调用时, THE Audit_Engine SHALL 记录为"后端孤立接口"类型不一致项
5. WHEN 前后端路径相同但 HTTP 方法不一致时, THE Audit_Engine SHALL 记录为"HTTP方法不匹配"类型不一致项
6. THE Audit_Engine SHALL 在路径比对时忽略 /api 前缀差异（后端可能通过网关统一添加 /api 前缀）

### Requirement 5: 功能覆盖率审核

**User Story:** 作为审核执行者，我希望系统能按照 REQ-031 功能表验证三端的功能覆盖情况，以便发现功能缺失或多余实现。

#### Acceptance Criteria

1. THE Audit_Engine SHALL 将 REQ-031 功能表中定义的每个功能点映射为对应的后端 Module 和前端 API 文件
2. WHEN 审核 PC 前端功能覆盖时, THE Audit_Engine SHALL 验证 REQ-031 中"二、业务管理"和"四、平台管理功能"定义的所有功能在 PC 前端均有对应的 API 调用和页面实现
3. WHEN 审核移动端功能覆盖时, THE Audit_Engine SHALL 验证 REQ-031 中"三、手机管理功能"定义的所有功能在移动端均有对应的 API 调用和页面实现
4. WHEN 功能表中定义的功能在对应前端未发现实现时, THE Audit_Engine SHALL 记录为"功能缺失"类型不一致项，并标注所属模块和功能名称
5. WHEN 前端存在功能表中未定义的功能实现时, THE Audit_Engine SHALL 记录为"超范围实现"类型不一致项

### Requirement 6: 数据字段一致性审核

**User Story:** 作为审核执行者，我希望系统能比对前端表单字段与后端实体字段的对应关系，以便发现字段名不匹配或字段缺失的问题。

#### Acceptance Criteria

1. THE Audit_Engine SHALL 扫描后端 Module 中的 DTO/VO/Entity 类，提取字段名、字段类型、校验注解
2. THE Audit_Engine SHALL 扫描前端 Vue 组件中的表单绑定字段（v-model 绑定的字段名）和 API 请求参数字段
3. WHEN 前端表单字段名与后端 DTO 字段名不一致时（考虑驼峰与下划线转换规则）, THE Audit_Engine SHALL 记录为"字段名不匹配"类型不一致项
4. WHEN 前端提交的字段在后端 DTO 中不存在时, THE Audit_Engine SHALL 记录为"前端多余字段"类型不一致项
5. WHEN 后端 DTO 中标注 @NotNull 或 @NotBlank 的必填字段在前端表单中未体现时, THE Audit_Engine SHALL 记录为"必填字段前端缺失"类型不一致项

### Requirement 7: 不一致项报告生成

**User Story:** 作为审核执行者，我希望系统能生成结构化的不一致项报告，以便开发团队快速定位和修复问题。

#### Acceptance Criteria

1. THE Audit_Engine SHALL 生成 Inconsistency_Report，包含以下分类汇总：API路径不一致数、功能覆盖缺失数、字段不匹配数、总不一致项数
2. THE Audit_Engine SHALL 在 Inconsistency_Report 中对每个不一致项记录：不一致类型、涉及模块、前端文件路径、后端文件路径、具体差异描述、建议修复方案
3. THE Audit_Engine SHALL 对不一致项按严重程度分级：Critical（路径完全不存在导致功能不可用）、Major（HTTP方法错误或必填字段缺失）、Minor（字段命名风格差异或超范围实现）
4. THE Audit_Engine SHALL 按模块分组输出不一致项，每个模块独立展示其不一致项列表
5. WHEN 审核完成后, THE Audit_Engine SHALL 输出审核覆盖率统计，包含：已审核模块数/总模块数、已审核API数/总API数、一致率百分比

### Requirement 8: 审核范围与模块清单

**User Story:** 作为审核执行者，我希望明确审核覆盖的模块范围，以便确保审核的完整性。

#### Acceptance Criteria

1. THE Audit_Engine SHALL 对以下后端业务模块进行完整审核：system（系统管理）、project（项目管理）、finance（财务管理）、site（现场管理）、material（材料库存）、machine（机械管理）、contract（合同管理）、budget（预算管理）、purchase（采购管理）、labor（劳务管理）、subcontract（分包管理）、hr（行政人事）、tender（投标管理）、archive（档案管理）、dashboard（看板）、workflow（流程管理）、message（消息管理）、basedata（基础数据）、security（认证安全）、file（文件管理）
2. THE Audit_Engine SHALL 对 PC 前端以下 API 文件进行完整审核：system.ts、project.ts、finance.ts、site.ts、material.ts、machine.ts、contract.ts、budget.ts、purchase.ts、labor.ts、subcontract.ts、hr.ts、tender.ts、archive.ts、dashboard.ts、basedata.ts
3. THE Audit_Engine SHALL 对移动端以下 API 文件进行完整审核：auth.ts、common.ts
4. WHEN 发现后端存在已实现但前端均未调用的模块时, THE Audit_Engine SHALL 在报告中标注该模块为"待前端对接"状态

### Requirement 9: 移动端与PC端功能差异标注

**User Story:** 作为审核执行者，我希望系统能标注移动端与PC端之间的功能差异，以便明确哪些功能仅在某一端提供是合理的设计决策。

#### Acceptance Criteria

1. THE Audit_Engine SHALL 将 PC 端独有功能与移动端独有功能分别列表展示
2. WHEN 某功能仅在 PC 端实现但 REQ-031 中"三、手机管理功能"也要求移动端实现时, THE Audit_Engine SHALL 记录为"移动端功能缺失"类型不一致项
3. WHEN 某功能仅在移动端实现但 REQ-031 中"二、业务管理"或"四、平台管理功能"也要求 PC 端实现时, THE Audit_Engine SHALL 记录为"PC端功能缺失"类型不一致项
4. THE Audit_Engine SHALL 按 REQ-031 的功能表区分合理差异（如平台管理功能仅需 PC 端、定位签到仅需移动端）和不合理差异

### Requirement 10: 路径前缀规范性校验

**User Story:** 作为审核执行者，我希望系统能校验所有API路径是否符合统一的命名规范，以便发现命名不规范的接口。

#### Acceptance Criteria

1. THE Audit_Engine SHALL 验证后端所有 API 路径均以 /v1/{module}/ 为前缀
2. WHEN 后端 API 路径不符合 /v1/{module}/ 前缀规范时, THE Audit_Engine SHALL 记录为"路径命名不规范"类型不一致项
3. THE Audit_Engine SHALL 验证前端 API 调用路径中的模块名与后端 Module 名称一致（如前端 /v1/site/xxx 对应后端 zw-site 模块）
4. WHEN 路径中的资源命名不符合 RESTful 风格（使用小写字母和连字符分隔）时, THE Audit_Engine SHALL 记录为"RESTful命名不规范"类型不一致项
