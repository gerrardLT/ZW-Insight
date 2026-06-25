# Implementation Plan: P0 数据权限隔离 & 整改超期催办

## Overview

本实现计划将设计文档拆解为可逐步执行的编码任务，分为两大模块：
1. **数据权限隔离**：完善 `DataPermissionInnerInterceptor` + `ZwDataPermissionHandler` 的拦截链，实现 `DataPermissionDataProviderImpl`，新增 `SysRole.dataScope` 字段管理接口，以及前端权限控制（v-permission 指令 + 路由守卫）。
2. **整改超期催办**：数据库迁移 + 实体定义，催办配置管理，去重频率控制服务，定时任务核心逻辑，催办日志查询/统计 API。

技术栈：Java 17 + Spring Boot 3 + MyBatis-Plus + jqwik (属性测试) + JUnit 5 + Vue 3 + TypeScript。

## Tasks

- [x] 1. 数据库迁移与数据模型准备
  - [x] 1.1 创建数据权限相关 DDL 迁移脚本
    - 在 `zw-app/src/main/resources/db/migration/` 下新建 SQL 迁移文件
    - 为 `sys_role` 表新增 `data_scope VARCHAR(30) NOT NULL DEFAULT 'SELF'` 字段
    - 修改 `SysRole` 实体类，添加 `dataScope` 字段和校验注解
    - _Requirements: 1.1_

  - [x] 1.2 创建催办配置表与催办日志表 DDL 迁移脚本
    - 新建 `biz_reminder_config` 表（含 tenant_id, interval_days, escalation_days, long_overdue_days, enabled 等字段）
    - 新建 `biz_reminder_log` 表（含 inspection_id, receiver_id, reminder_level, send_status, overdue_days, sent_at 等字段及索引）
    - _Requirements: 8.1, 8.2, 8.3, 8.4, 10.1, 10.2_

  - [x] 1.3 创建催办相关实体类和 Mapper
    - 在 `zw-site/domain` 下创建 `BizReminderConfig` 实体类（含 @Range 校验）
    - 在 `zw-site/domain` 下创建 `BizReminderLog` 实体类
    - 在 `zw-site/mapper` 下创建 `BizReminderConfigMapper` 和 `BizReminderLogMapper`
    - _Requirements: 8.1, 8.6, 10.2_

- [x] 2. 数据权限拦截器核心实现
  - [x] 2.1 完善 ZwDataPermissionHandler 异常处理与安全上下文校验
    - 当 `SecurityContextHolder.getUserId()` 返回 null 且方法标注了 `@DataPermission` 时，抛出 `DataPermissionException`
    - 添加 `DataPermissionException` 自定义异常类到 `zw-common/exception`
    - 当 `DataPermissionDataProvider` 查询异常时，降级为 SELF 范围并记录 ERROR 日志
    - _Requirements: 2.8, 5.3_

  - [x] 2.2 实现 DataPermissionInnerInterceptor 系统管理模块跳过逻辑
    - 在 `DataPermissionInnerInterceptor` 中识别系统管理模块的 Mapper（`com.zwinsight.system.mapper.*`），对这些 Mapper 不做数据权限过滤
    - 确保 `DataPermissionInnerInterceptor` 在 `TenantLineInnerInterceptor` 之后注册（已在 MybatisPlusConfig 中配置）
    - _Requirements: 5.1, 5.3_

  - [x] 2.3 实现 DataPermissionDataProviderImpl 数据提供者
    - 在 `zw-system/service` 下新建 `DataPermissionDataProviderImpl` 类，实现 `DataPermissionDataProvider` 接口
    - 实现 `getUserDataScopes`：通过 sys_user_role + sys_role 联查获取用户所有角色的 data_scope
    - 实现 `getUserProjectIds`：从 `sys_user_project`（或 `biz_project_member`）表查询用户参与的项目ID
    - 实现 `getUserDeptId`：从 `sys_user` 表获取用户所属部门 ID
    - 实现 `getDeptAndChildIds`：通过 `sys_org.ancestors` 路径进行 LIKE 查询获取部门及子部门
    - _Requirements: 2.1, 2.3, 2.4, 2.5, 2.6, 1.4_

  - [x]* 2.4 编写 ZwDataPermissionHandler 属性测试 — SQL 条件生成正确性
    - **Property 3: SQL 条件生成正确性**
    - 使用 jqwik 对所有 DataScopeEnum 值生成随机输入，验证各范围返回正确的 SQL 表达式
    - **Validates: Requirements 2.1, 2.2, 2.3, 2.4, 2.5, 2.6**

  - [x]* 2.5 编写 ZwDataPermissionHandler 属性测试 — 未注解方法不过滤
    - **Property 4: 未注解方法不过滤**
    - 使用 jqwik 生成随机 mappedStatementId，验证未标注 @DataPermission 的方法返回 null
    - **Validates: Requirements 3.4**

  - [x]* 2.6 编写 DataPermissionDataProviderImpl 单元测试
    - Mock 数据库验证各方法 SQL 查询逻辑正确性
    - 测试 getUserDataScopes 多角色返回、无角色返回
    - 测试 getDeptAndChildIds ancestors 路径匹配
    - _Requirements: 2.3, 2.4, 2.5, 2.6_

- [x] 3. 角色数据范围配置管理
  - [x] 3.1 扩展 SysRoleController 添加 dataScope 配置接口
    - 新增 `PUT /api/v1/system/roles/{roleId}/data-scope` 接口
    - 创建 `DataScopeUpdateRequest` DTO，含 dataScope 字段（校验必须为 DataScopeEnum 有效值）
    - 在 Service 层实现 dataScope 更新逻辑并持久化
    - _Requirements: 1.2, 1.5_

  - [x]* 3.2 编写 DataScope 枚举值校验属性测试
    - **Property 1: DataScope 枚举值校验**
    - 使用 jqwik 生成随机字符串，验证仅 {ALL, DEPT_AND_CHILDREN, DEPT, PROJECT, SELF} 保存成功，其余返回校验错误
    - **Validates: Requirements 1.1, 1.5**

  - [x]* 3.3 编写 DataScope 配置立即生效属性测试
    - **Property 2: DataScope 配置立即生效**
    - 使用 jqwik 验证配置更新后查询立即返回新值
    - **Validates: Requirements 1.2**

- [x] 4. Checkpoint — 数据权限模块验证
  - Ensure all tests pass, ask the user if questions arise.

- [x] 5. 整改催办配置管理服务
  - [x] 5.1 实现 ReminderConfigService 接口和实现类
    - 在 `zw-site/service` 下创建 `ReminderConfigService` 接口和 `ReminderConfigServiceImpl`
    - 实现 `getConfig(Long tenantId)`：查询配置，不存在时返回默认值
    - 实现 `updateConfig(Long tenantId, ReminderConfigUpdateRequest request)`：校验并更新配置
    - 创建 `ReminderConfigUpdateRequest` DTO（含 @Range 参数校验）
    - _Requirements: 8.1, 8.2, 8.3, 8.4, 8.5, 8.6_

  - [x] 5.2 实现 ReminderConfigController REST 接口
    - 在 `zw-site/controller` 下创建 `ReminderConfigController`
    - 提供 `GET /api/v1/site/reminder-config` 查询催办配置
    - 提供 `PUT /api/v1/site/reminder-config` 更新催办配置
    - _Requirements: 8.5_

  - [x]* 5.3 编写 intervalDays 参数校验属性测试
    - **Property 11: intervalDays 参数校验**
    - 使用 jqwik 生成整数范围测试，验证 1-30 通过，超出范围返回错误
    - **Validates: Requirements 8.1, 8.6**

- [x] 6. 催办去重与频率控制服务
  - [x] 6.1 实现 ReminderDeduplicationService 接口和实现类
    - 在 `zw-site/service` 下创建 `ReminderDeduplicationService` 接口
    - 创建 `ReminderDeduplicationServiceImpl` 实现，使用 Redis `rectification:reminder:last:{inspectionId}` Key 记录上次催办时间
    - 实现 `shouldSend(Long inspectionId, LocalDate today, int intervalDays)`：比较 today 与 lastSentDate 的间隔
    - 实现 `markSent(Long inspectionId, LocalDate today)`：写入 Redis（TTL 90天）
    - 实现 `clearMarks(Long inspectionId)`：删除 Redis key（整改完成时调用）
    - 实现 Redis 不可用时降级为查询 `biz_reminder_log` 表最新记录时间
    - _Requirements: 9.1, 9.2, 9.3, 9.5_

  - [x]* 6.2 编写催办频率控制属性测试
    - **Property 12: 催办频率控制**
    - 使用 jqwik 生成随机 lastSentDate 和 intervalDays，验证 shouldSend 返回正确布尔值
    - **Validates: Requirements 9.2**

  - [x]* 6.3 编写 ReminderDeduplicationService 单元测试
    - 测试 Redis 正常场景：shouldSend 判断、markSent 写入、clearMarks 删除
    - 测试 Redis 不可用降级为 DB 查询场景
    - _Requirements: 9.1, 9.2, 9.5_

- [x] 7. 整改超期催办定时任务核心逻辑
  - [x] 7.1 实现 RectificationReminderTask 定时任务类
    - 在 `zw-site` 模块下新建 `task` 包，创建 `RectificationReminderTask` 类
    - 使用 `@Scheduled(cron = "0 0 8 * * ?")` 注解配置每日 08:00 执行
    - 实现 Redis 分布式锁（`rectification:reminder:lock`，TTL 30分钟）防止多实例并发执行
    - 加载催办配置，enabled=false 时跳过
    - 查询超期记录（`rectificationStatus = PENDING AND rectificationDeadline < today`）
    - 逐条处理：计算超期天数 → 长期超期检查 → 频率控制检查 → 发送通知 → 记录日志
    - 单条异常不中断循环，记录 ERROR 日志继续处理
    - _Requirements: 6.1, 6.2, 6.3, 6.4, 7.1, 7.3, 7.4, 9.4_

  - [x] 7.2 实现催办通知内容构建与发送
    - 构建催办消息内容：项目名称、检查类型（质量/安全）、问题描述、整改期限、已超期天数
    - 调用已有 `MessageService` 发送站内消息给 responsiblePersonId
    - 当超期天数 >= escalationDays 时，额外查询项目经理并发送升级通知
    - 当超期天数 > longOverdueDays 时，跳过该记录不发送通知
    - _Requirements: 7.1, 7.2, 7.3, 7.4_

  - [x]* 7.3 编写超期扫描过滤属性测试
    - **Property 6: 超期扫描只返回符合条件的记录**
    - 使用 jqwik 生成随机检查记录集合（各种状态和日期），验证仅 PENDING + 超期 的记录被选出
    - **Validates: Requirements 6.2, 9.4**

  - [x]* 7.4 编写超期天数计算属性测试
    - **Property 7: 超期天数计算正确性**
    - 使用 jqwik 生成随机 deadline 日期（早于 today），验证 `ChronoUnit.DAYS.between(deadline, today)` 结果
    - **Validates: Requirements 6.3**

  - [x]* 7.5 编写催办通知内容完整性属性测试
    - **Property 8: 催办通知内容完整性**
    - 使用 jqwik 生成随机项目名称、检查类型、问题描述等，验证生成的消息包含全部五项信息
    - **Validates: Requirements 7.2**

  - [x]* 7.6 编写升级通知阈值属性测试
    - **Property 9: 升级通知阈值正确触发**
    - 使用 jqwik 生成随机 overdueDays 和 escalationDays，验证升级逻辑触发正确
    - **Validates: Requirements 7.3**

  - [x]* 7.7 编写长期超期停止催办属性测试
    - **Property 10: 长期超期停止催办**
    - 使用 jqwik 生成随机 overdueDays 和 longOverdueDays，验证超过阈值时不发送通知
    - **Validates: Requirements 7.4**

- [x] 8. Checkpoint — 催办核心逻辑验证
  - Ensure all tests pass, ask the user if questions arise.

- [x] 9. 催办日志与统计 API
  - [x] 9.1 实现 ReminderLogService 接口和实现类
    - 在 `zw-site/service` 下创建 `ReminderLogService` 接口和 `ReminderLogServiceImpl`
    - 实现 `saveLog`：保存催办日志（inspectionId, receiverId, reminderLevel, sendStatus, overdueDays, sentAt）
    - 实现 `getLogsByInspectionId`：按 sentAt 降序查询某记录的催办历史
    - 实现 `getStatsByProjectId`：统计项目下超期整改总数、已催办次数、已升级通知次数
    - _Requirements: 10.1, 10.2, 10.3, 10.4_

  - [x] 9.2 实现 ReminderLogController REST 接口
    - 在 `zw-site/controller` 下创建 `ReminderLogController`
    - 提供 `GET /api/v1/site/reminder-logs/{inspectionId}` 查询催办历史（按时间倒序）
    - 提供 `GET /api/v1/site/reminder-stats/{projectId}` 查询催办统计
    - _Requirements: 10.3, 10.4_

  - [x]* 9.3 编写催办日志完整性属性测试
    - **Property 13: 催办日志完整性**
    - 使用 jqwik 验证每次催办发送后日志记录的必填字段均非空
    - **Validates: Requirements 10.1, 10.2**

  - [x]* 9.4 编写催办日志时间排序属性测试
    - **Property 14: 催办日志时间排序**
    - 使用 jqwik 生成多条日志记录，验证查询结果按 sentAt 降序排列
    - **Validates: Requirements 10.3**

- [x] 10. 整改状态变更时清除催办标记
  - [x] 10.1 在 RectificationService 中集成催办标记清除逻辑
    - 当整改状态变为 APPROVED 时，调用 `ReminderDeduplicationService.clearMarks(inspectionId)`
    - 当整改状态变为 SUBMITTED 时，设置暂停催办标记（或在 shouldSend 判断中增加状态检查）
    - _Requirements: 9.3, 9.4_

- [x] 11. 前端权限控制实现
  - [x] 11.1 实现 v-permission 自定义指令
    - 在 `zw-insight-web/src/utils/` 下创建 `permission.ts`，实现 Vue 3 自定义指令 `v-permission`
    - 指令逻辑：从用户 store 获取权限标识列表，判断元素所需权限是否在列表中，不在则移除 DOM 元素
    - 在 `main.ts` 中注册全局指令
    - _Requirements: 4.2_

  - [x] 11.2 实现动态路由守卫与 403 页面
    - 修改 `zw-insight-web/src/router/index.ts`，添加路由前置守卫
    - 用户登录后根据权限标识列表动态生成可访问路由
    - 无权限页面 URL 直接访问时跳转 403 页面
    - 创建 `views/error/403.vue` 无权限提示页面
    - _Requirements: 4.3, 4.4_

  - [x] 11.3 用户登录接口返回权限标识列表
    - 修改登录/获取用户信息接口，确保返回 `permissions: string[]` 字段
    - 前端 store 存储权限列表，供 v-permission 指令和路由守卫使用
    - 角色变更后重新登录时返回更新后的权限列表
    - _Requirements: 4.1, 4.5_

- [x] 12. 数据权限与租户协同验证
  - [x] 12.1 确保 DataPermissionInnerInterceptor 与 TenantLineInnerInterceptor 正确叠加
    - 验证 MybatisPlusConfig 中拦截器注册顺序（Tenant → DataPermission → Pagination → OptimisticLocker）
    - 验证最终 SQL 中 tenant_id 和数据权限条件通过 AND 连接
    - _Requirements: 5.1, 5.2_

  - [x]* 12.2 编写租户与数据权限 AND 组合属性测试
    - **Property 5: 租户条件与数据权限 AND 组合**
    - 使用 jqwik 验证同时触发两种过滤时最终 SQL 条件通过 AND 逻辑连接
    - **Validates: Requirements 5.2**

- [x] 13. 集成测试与端到端验证
  - [x]* 13.1 编写数据权限集成测试
    - 使用 @SpringBootTest + 内存数据库验证拦截器链（TenantLine + DataPermission）的真实 SQL 输出
    - 覆盖 ALL、SELF、DEPT、DEPT_AND_CHILDREN、PROJECT 五种范围
    - _Requirements: 2.1-2.7, 5.1, 5.2_

  - [x]* 13.2 编写催办定时任务集成测试
    - 使用 @SpringBootTest 验证 RectificationReminderTask 全流程：配置加载 → 超期扫描 → 频率控制 → 消息发送 → 日志记录
    - Mock MessageService 验证消息内容和接收人正确性
    - _Requirements: 6.1-6.4, 7.1-7.4, 9.1-9.5_

- [x] 14. Final Checkpoint — 全功能验证
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional (all completed)
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation
- Property tests validate universal correctness properties (使用 jqwik 库，项目已引入)
- Unit tests validate specific examples and edge cases
- 数据权限模块基于已有骨架代码完善（`ZwDataPermissionHandler`、`DataPermission` 注解等已存在）
- 催办模块参考现有 `RetentionWarningTask` 模式保持架构一致性
- Redis Key 设计：`rectification:reminder:last:{inspectionId}` (TTL 90天)，`rectification:reminder:lock` (TTL 30分钟)

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1", "1.2"] },
    { "id": 1, "tasks": ["1.3", "2.1", "2.2"] },
    { "id": 2, "tasks": ["2.3", "3.1"] },
    { "id": 3, "tasks": ["2.4", "2.5", "2.6", "3.2", "3.3"] },
    { "id": 4, "tasks": ["5.1", "6.1", "11.1", "11.3"] },
    { "id": 5, "tasks": ["5.2", "5.3", "6.2", "6.3", "11.2"] },
    { "id": 6, "tasks": ["7.1"] },
    { "id": 7, "tasks": ["7.2", "9.1"] },
    { "id": 8, "tasks": ["7.3", "7.4", "7.5", "7.6", "7.7", "9.2", "10.1"] },
    { "id": 9, "tasks": ["9.3", "9.4", "12.1"] },
    { "id": 10, "tasks": ["12.2", "13.1", "13.2"] }
  ]
}
```
