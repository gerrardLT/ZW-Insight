# Implementation Plan: P2 Quick Wins

## Overview

实现 4 项独立的体验增强功能：数据脱敏（后端 Java 全新实现）、甘特图前端接入确认（Vue 微改造）、批量启用/停用用户（Vue 前端改造）、可视化编号规则管理（Vue 前端新建页面）。四项功能无互相依赖，可并行开发。后端使用 Java（Spring Boot 3.2 + Jackson），前端使用 TypeScript + Vue 3 + Element Plus。

## Tasks

- [x] 1. 数据脱敏 — 后端实现
  - [x] 1.1 创建脱敏类型枚举和自定义注解
    - 在 `zw-common/src/main/java/com/zwinsight/common/desensitize/` 下创建 `DesensitizeType.java` 枚举，定义 PHONE、ID_CARD、BANK_CARD、EMAIL、ADDRESS 五种脱敏类型及各类型的 prefixLen、suffixLen、minLen 属性
    - 创建 `Desensitize.java` 注解，标注 `@Target(ElementType.FIELD)`、`@Retention(RUNTIME)`、`@JacksonAnnotationsInside`、`@JsonSerialize(using = DesensitizeSerializer.class)`，参数为 `DesensitizeType type()`
    - _Requirements: 1.1, 1.2_

  - [x] 1.2 实现脱敏工具类 DesensitizeUtil
    - 创建 `DesensitizeUtil.java`，实现 `desensitize(String value, DesensitizeType type)` 纯函数
    - 实现 maskPhone（前3后4，中间4星号）、maskIdCard（前3后4，中间星号）、maskBankCard（前4后4，中间星号）、maskEmail（首字母+星号+@域名）、maskAddress（前6字符+星号）
    - null/空字符串原样返回；长度不足 minLen 时全部替换为等长星号
    - _Requirements: 1.4, 1.5, 1.6, 1.7, 1.8, 1.9, 1.10_

  - [x]* 1.3 属性测试 — 脱敏掩码格式正确性
    - **Property 1: 脱敏掩码格式正确性**
    - 使用 jqwik 生成满足 minLen 要求的随机字符串，验证每种脱敏类型的输出格式严格符合设计规格
    - **Validates: Requirements 1.4, 1.5, 1.6, 1.7, 1.8**

  - [x]* 1.4 属性测试 — 短输入全星号替代
    - **Property 2: 短输入全星号替代**
    - 使用 jqwik 对每种脱敏类型生成长度 < minLen 的非空字符串，验证输出为等长星号字符串
    - **Validates: Requirements 1.10**

  - [x]* 1.5 属性测试 — 空值脱敏恒等性
    - **Property 3: 空值脱敏恒等性**
    - 使用 jqwik 对所有脱敏类型验证 `desensitize(null, type) == null` 且 `desensitize("", type) == ""`
    - **Validates: Requirements 1.9**

  - [x] 1.6 实现 DesensitizeSerializer（Jackson ContextualSerializer）
    - 创建 `DesensitizeSerializer.java`，继承 `StdSerializer<String>` 并实现 `ContextualSerializer`
    - `createContextual` 方法从 BeanProperty 读取 `@Desensitize` 注解获取脱敏类型
    - `serialize` 方法调用 `DesensitizeUtil.desensitize(value, type)` 输出脱敏后的值
    - _Requirements: 1.3, 1.11_

  - [x] 1.7 标注实体字段并验证全链路脱敏
    - 在 `SysUserVO`（或对应 DTO）的 phone 字段标注 `@Desensitize(type = DesensitizeType.PHONE)`
    - 在 email 字段标注 `@Desensitize(type = DesensitizeType.EMAIL)`
    - 在 idCard 字段标注 `@Desensitize(type = DesensitizeType.ID_CARD)`
    - 扫描其他实体中包含敏感字段的 VO/DTO 并标注注解
    - _Requirements: 1.1, 1.3, 1.11_

- [x] 2. Checkpoint — 数据脱敏功能验证
  - Ensure all tests pass, ask the user if questions arise.

- [x] 3. 甘特图前端接入确认
  - [x] 3.1 改造 schedule.vue 甘特图区域
    - 在甘特图卡片 header 中集成 `ProjectSelector` 组件，替换当前自动取第一条数据的逻辑，支持用户主动选择项目
    - 增加空状态判断逻辑：`ganttProjectId && ganttHasData` 时显示甘特图，`ganttProjectId && !ganttHasData` 时显示"暂无进度计划数据"，未选择项目时显示"请先选择项目以查看甘特图"
    - 添加 `ganttHasData` 响应式变量，在 `getSchedulePlanTree` 返回后根据数据是否为空设置该变量
    - _Requirements: 2.1, 2.2, 2.3, 2.6_

- [x] 4. 批量启用/停用用户 — 前端改造
  - [x] 4.1 改造 user/index.vue 批量操作逻辑
    - 引入 `useUserStore` 获取当前登录用户 ID
    - 修改 `handleBatchStatus` 方法：过滤排除当前用户 ID，过滤后为空时显示 warning 提示"不能对自己执行此操作"
    - 修改确认对话框文案为 `确认${action}选中的 ${filteredIds.length} 个用户？`（N 为过滤后的数量）
    - 确认后端实际接口路径（`PUT /v1/system/user/status` 或 `/batch-status`）并对齐前端 API 调用
    - _Requirements: 3.4, 3.5, 3.6, 3.7, 3.8, 3.9_

  - [x]* 4.2 属性测试 — 批量操作排除当前用户
    - **Property 4: 批量操作排除当前用户**
    - 使用 Vitest + fast-check 生成包含/不包含当前用户 ID 的随机 ID 数组，验证提交的 ID 列表中不包含当前用户 ID 且长度正确
    - **Validates: Requirements 3.9**

- [x] 5. Checkpoint — 甘特图和批量操作验证
  - Ensure all tests pass, ask the user if questions arise.

- [x] 6. 可视化编号规则管理 — 前端新建页面
  - [x] 6.1 创建编号规则 API 层
    - 新建 `zw-insight-web/src/api/file.ts`，定义 `SerialNumberRule` 接口类型
    - 实现 `getSerialNumberList`、`createSerialNumber`、`updateSerialNumber`、`deleteSerialNumber`、`generateSerialNumber` 五个 API 函数
    - _Requirements: 4.5, 4.6, 4.7, 4.10_

  - [x] 6.2 注册路由和菜单
    - 在 `router/index.ts` 的 `/system` children 中添加 `serial-number` 路由配置，指向 `views/system/serial-number/index.vue`
    - 准备 `data-menu.sql` 中编号规则管理菜单的 INSERT 语句
    - _Requirements: 4.1_

  - [x] 6.3 实现编号规则管理页面组件
    - 新建 `zw-insight-web/src/views/system/serial-number/index.vue`
    - 实现表格展示所有编号规则（列：业务类型、规则前缀、日期格式、序号长度、重置周期、描述、操作）
    - 实现"新增"按钮及表单对话框（含 businessType、rulePrefix、dateFormat 下拉、seqLength 数字、resetPeriod 下拉、description 字段）
    - 实现"编辑"按钮复用表单对话框并回填数据
    - 实现"删除"按钮弹出确认后调用 DELETE 接口
    - 实现"预览"按钮调用 generate 接口并以 ElMessage.success 展示生成的编号
    - 实现前端表单校验规则（businessType 仅字母/数字/下划线≤50字符、rulePrefix≤20字符、seqLength 1-10整数）
    - 操作成功后刷新列表并关闭对话框；失败时显示错误提示，对话框保持打开
    - _Requirements: 4.2, 4.3, 4.4, 4.5, 4.6, 4.7, 4.8, 4.9, 4.10, 4.11_

  - [x]* 6.4 属性测试 — businessType 表单校验
    - **Property 5: 编号规则表单校验 — businessType 合法性**
    - 使用 Vitest + fast-check 生成包含非 `[a-zA-Z0-9_]` 字符或长度超 50 的字符串，验证校验函数拒绝非法输入
    - **Validates: Requirements 4.11**

  - [x]* 6.5 属性测试 — seqLength 范围校验
    - **Property 6: 编号规则表单校验 — seqLength 范围**
    - 使用 Vitest + fast-check 生成 [1,10] 范围外的数值（含非整数），验证校验函数拒绝非法输入
    - **Validates: Requirements 4.11**

- [x] 7. Final Checkpoint — 全功能集成验证
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- 四项功能无互相依赖，可按 Wave 并行推进
- 后端属性测试使用 jqwik（项目已引入），前端属性测试使用 Vitest + fast-check
- 数据脱敏为后端全新实现，需按顺序完成（枚举→工具类→Serializer→注解标注）
- 甘特图/批量操作/编号规则主要是前端改造或新建，后端接口已就绪
- Property tests validate universal correctness properties defined in design
- Unit tests validate specific examples and edge cases
- Checkpoints ensure incremental validation

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1", "3.1", "4.1", "6.1", "6.2"] },
    { "id": 1, "tasks": ["1.2", "6.3"] },
    { "id": 2, "tasks": ["1.3", "1.4", "1.5", "1.6", "4.2", "6.4", "6.5"] },
    { "id": 3, "tasks": ["1.7"] }
  ]
}
```
