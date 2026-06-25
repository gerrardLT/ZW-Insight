# Requirements Document

## Introduction

本文档定义 ZW-Insight 工程项目管理平台两个 P0 级别核心功能的需求：**数据权限隔离（多项目/多部门）** 和 **整改超期催办**。

数据权限隔离解决同一租户内不同项目/部门间的数据可见性问题，确保用户只能访问其权限范围内的数据。整改超期催办解决整改记录超期后无自动通知的问题，确保超期整改被及时跟进处理。

## Glossary

- **Data_Permission_Interceptor**: MyBatis-Plus SQL 拦截器，在查询执行前自动注入数据权限 WHERE 条件
- **Data_Scope**: 数据范围配置，定义角色可访问的数据边界，取值为 ALL（全部）、DEPT_AND_CHILDREN（本部门及下级）、DEPT（本部门）、PROJECT（所属项目）、SELF（仅本人）
- **SysRole**: 系统角色实体，包含 dataScope 字段用于配置该角色的数据可见范围
- **SysOrg**: 机构实体，支持树形结构（parentId + ancestors 路径），类型包括 COMPANY 和 DEPARTMENT
- **ProjectMember**: 项目成员关联，记录用户在特定项目中的角色（ProjectRoleEnum）
- **Rectification_Record**: 整改记录，由质量/安全检查产生，包含 rectificationDeadline（整改期限）和 rectificationStatus（整改状态）
- **Rectification_Reminder_Task**: 整改超期催办定时任务，定期扫描超期未完成的整改记录并发送通知
- **Reminder_Config**: 催办配置实体，定义催办频率、升级阈值等参数
- **Reminder_Log**: 催办日志实体，记录每次催办的发送时间、接收人、级别等信息
- **MessageService**: 站内消息服务，负责向指定用户发送站内信通知
- **SecurityContextHolder**: 安全上下文持有者，提供当前登录用户的 userId、tenantId、orgId 等信息
- **v-permission**: Vue 3 前端权限指令，控制菜单和按钮的显示/隐藏

---

## Requirements

### Requirement 1: 角色数据范围配置

**User Story:** 作为系统管理员，我想为每个角色配置数据可见范围，以便控制不同角色用户能看到的数据边界。

#### Acceptance Criteria

1. THE SysRole SHALL 包含 dataScope 字段，取值限定为 ALL、DEPT_AND_CHILDREN、DEPT、PROJECT、SELF 五种之一
2. WHEN 管理员在角色管理页面修改某角色的 dataScope 值时，THE System SHALL 持久化该配置并立即生效于后续请求
3. WHEN 角色的 dataScope 为 DEPT_AND_CHILDREN 或 DEPT 时，THE System SHALL 要求管理员指定关联的部门 ID 列表（若为空则默认使用用户所属部门）
4. WHEN 角色的 dataScope 为 PROJECT 时，THE System SHALL 根据 ProjectMember 表中该用户参与的项目列表确定可访问范围
5. IF 管理员尝试将 dataScope 设为无效值，THEN THE System SHALL 返回参数校验错误并拒绝保存

---

### Requirement 2: MyBatis-Plus 数据权限拦截器

**User Story:** 作为开发者，我希望查询自动注入数据权限条件，以便无需在每个 SQL 中手动添加权限过滤。

#### Acceptance Criteria

1. THE Data_Permission_Interceptor SHALL 在标注了 @DataScope 注解的 Mapper 方法执行前，根据当前用户角色的 dataScope 配置自动拼接 WHERE 条件
2. WHEN 用户角色 dataScope 为 ALL 时，THE Data_Permission_Interceptor SHALL 不追加任何数据权限条件
3. WHEN 用户角色 dataScope 为 DEPT_AND_CHILDREN 时，THE Data_Permission_Interceptor SHALL 追加条件限定数据所属部门在用户部门及其所有下级部门范围内（通过 SysOrg.ancestors 路径匹配）
4. WHEN 用户角色 dataScope 为 DEPT 时，THE Data_Permission_Interceptor SHALL 追加条件限定数据所属部门等于用户当前部门
5. WHEN 用户角色 dataScope 为 PROJECT 时，THE Data_Permission_Interceptor SHALL 追加条件限定数据的 project_id 在用户参与的项目 ID 列表内
6. WHEN 用户角色 dataScope 为 SELF 时，THE Data_Permission_Interceptor SHALL 追加条件限定数据的 create_by 等于当前用户 ID
7. WHEN 用户拥有多个角色且各角色 dataScope 不同时，THE Data_Permission_Interceptor SHALL 取所有角色数据范围的并集（最大可见范围）
8. IF 当前请求无法获取用户上下文（SecurityContextHolder 返回 null），THEN THE Data_Permission_Interceptor SHALL 拒绝执行查询并抛出权限异常

---

### Requirement 3: @DataScope 注解与数据表标识

**User Story:** 作为开发者，我希望通过注解声明哪些查询需要数据权限过滤以及过滤的目标列名，以便灵活控制拦截器的作用范围。

#### Acceptance Criteria

1. THE @DataScope 注解 SHALL 支持 deptAlias 参数，指定 SQL 中部门字段所在表的别名
2. THE @DataScope 注解 SHALL 支持 userAlias 参数，指定 SQL 中创建人字段所在表的别名
3. THE @DataScope 注解 SHALL 支持 projectAlias 参数，指定 SQL 中项目 ID 字段所在表的别名
4. WHEN Mapper 方法未标注 @DataScope 注解时，THE Data_Permission_Interceptor SHALL 跳过该方法不做任何处理
5. WHEN @DataScope 注解的 deptAlias 为空且当前 dataScope 为 DEPT 或 DEPT_AND_CHILDREN 时，THE Data_Permission_Interceptor SHALL 使用主表的 org_id 列作为默认过滤字段

---

### Requirement 4: 前端菜单与按钮权限控制

**User Story:** 作为普通用户，我希望界面只展示我有权限访问的菜单和按钮，以便获得清晰的操作界面。

#### Acceptance Criteria

1. WHEN 用户登录成功后，THE System SHALL 返回该用户所有角色关联的权限标识列表（permission codes）
2. THE v-permission 指令 SHALL 根据用户的权限标识列表控制 DOM 元素的渲染：拥有指定权限则渲染，否则移除该元素
3. WHEN 用户无某菜单的访问权限时，THE System SHALL 在侧边导航栏中隐藏该菜单项
4. WHEN 用户直接通过 URL 访问无权限的页面时，THE System SHALL 跳转到 403 无权限提示页面
5. WHEN 用户权限发生变更（角色调整）后重新登录时，THE System SHALL 返回更新后的权限标识列表

---

### Requirement 5: 数据权限与多租户的协同

**User Story:** 作为平台运营者，我希望数据权限在租户隔离基础上叠加生效，以便同一租户内不同角色看到不同范围的数据。

#### Acceptance Criteria

1. THE Data_Permission_Interceptor SHALL 在现有 TenantLineInnerInterceptor 之后执行，确保 tenant_id 条件优先注入
2. WHEN 数据权限条件与租户条件同时存在时，THE System SHALL 使用 AND 逻辑连接两个条件
3. THE Data_Permission_Interceptor SHALL 对系统管理模块（/api/v1/system/**）的查询不做数据权限过滤

---

### Requirement 6: 整改超期扫描

**User Story:** 作为项目经理，我希望系统自动识别超期未完成的整改记录，以便及时安排跟进。

#### Acceptance Criteria

1. THE Rectification_Reminder_Task SHALL 每日固定时间（08:00）执行一次超期扫描
2. WHEN 扫描执行时，THE Rectification_Reminder_Task SHALL 查询所有 rectificationStatus 为 PENDING 且 rectificationDeadline 早于当前日期的检查记录
3. THE Rectification_Reminder_Task SHALL 计算每条超期记录的超期天数（当前日期减去 rectificationDeadline）
4. IF 扫描过程中单条记录处理异常，THEN THE Rectification_Reminder_Task SHALL 记录错误日志并继续处理剩余记录

---

### Requirement 7: 整改超期催办通知

**User Story:** 作为整改责任人，我希望收到系统自动发送的超期催办消息，以便及时处理超期整改任务。

#### Acceptance Criteria

1. WHEN 整改记录超期时，THE Rectification_Reminder_Task SHALL 通过 MessageService 向 responsiblePersonId 对应的用户发送催办站内消息
2. THE 催办站内消息 SHALL 包含以下信息：项目名称、检查类型（质量/安全）、问题描述、整改期限、已超期天数
3. WHEN 整改记录超期天数达到升级阈值（默认7天）时，THE Rectification_Reminder_Task SHALL 额外通知该项目的项目经理（ProjectRoleEnum.PROJECT_MANAGER）
4. WHEN 整改记录超期天数超过长期阈值（默认30天）时，THE Rectification_Reminder_Task SHALL 停止对该记录发送催办通知并记录 LONG_OVERDUE 状态

---

### Requirement 8: 催办频率配置

**User Story:** 作为系统管理员，我希望可以配置催办的发送频率和升级规则，以便根据项目实际情况灵活调整催办策略。

#### Acceptance Criteria

1. THE Reminder_Config SHALL 支持配置催办间隔天数（intervalDays），取值范围为 1 至 30 的整数
2. THE Reminder_Config SHALL 支持配置升级通知阈值天数（escalationDays），表示超期多少天后通知项目经理
3. THE Reminder_Config SHALL 支持配置长期超期停止催办天数（longOverdueDays），超过此天数停止发送催办
4. THE Reminder_Config SHALL 支持配置是否启用自动催办（enabled），为 false 时定时任务跳过催办逻辑
5. WHEN 管理员修改催办配置后，THE System SHALL 在下一次定时任务执行时使用新配置
6. IF 催办配置中 intervalDays 小于 1 或大于 30，THEN THE System SHALL 返回参数校验错误并拒绝保存

---

### Requirement 9: 催办去重与频率控制

**User Story:** 作为整改责任人，我希望不被重复的催办消息打扰，以便只在必要时收到通知。

#### Acceptance Criteria

1. THE Rectification_Reminder_Task SHALL 使用 Redis 记录每条整改记录的上次催办发送时间
2. WHEN 距离上次催办发送时间未达到 intervalDays 天数时，THE Rectification_Reminder_Task SHALL 跳过该记录不发送催办
3. WHEN 整改记录状态变为 APPROVED（整改通过）时，THE System SHALL 清除该记录相关的所有 Redis 催办标记
4. WHEN 整改记录状态变为 SUBMITTED（已提交整改）时，THE Rectification_Reminder_Task SHALL 暂停对该记录的催办直到审核结果出来
5. IF Redis 不可用，THEN THE Rectification_Reminder_Task SHALL 降级为通过数据库 Reminder_Log 判断上次发送时间

---

### Requirement 10: 催办记录追溯

**User Story:** 作为项目经理，我希望查看整改记录的催办历史，以便了解催办执行情况和整改推进状态。

#### Acceptance Criteria

1. THE Rectification_Reminder_Task SHALL 在每次催办发送后将催办记录写入 Reminder_Log 表
2. THE Reminder_Log SHALL 包含以下字段：整改记录 ID、接收人 ID、催办级别（NORMAL/ESCALATED）、发送状态（SENT/FAILED）、发送时间
3. WHEN 用户查询某整改记录的催办历史时，THE System SHALL 返回该记录按时间倒序排列的催办日志列表
4. THE System SHALL 提供催办统计接口，返回指定项目下的超期整改总数、已催办次数、已升级通知次数
