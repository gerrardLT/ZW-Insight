# Implementation Plan: P1 系统完整度增强

## Overview

本实现计划覆盖 ZW-Insight 工程项目管理系统 P1 优先级的 8 个系统完整度增强功能的全部开发任务。任务按依赖关系分为 5 波次执行，从数据库 Schema 到后端服务到前端页面再到测试验证。

## Task Dependency Graph

```json
{
  "waves": [
    {
      "name": "Wave 1: 基础设施",
      "tasks": [1]
    },
    {
      "name": "Wave 2: 核心后端服务",
      "tasks": [2, 3, 5, 6, 7, 8, 9, 10]
    },
    {
      "name": "Wave 3: API 层",
      "tasks": [4]
    },
    {
      "name": "Wave 4: 前端页面",
      "tasks": [11, 12, 13, 14, 15, 16, 17, 18]
    },
    {
      "name": "Wave 5: 测试验证",
      "tasks": [19, 20]
    }
  ]
}
```

## Tasks

- [ ] 1. 数据库 Schema 迁移
  - Requirements: R1, R3, R4, R5, R6, R7, R8
  - Dependencies: None
  - Description: 创建全部新增数据表和字段变更的 SQL 迁移脚本
  - Sub-tasks:
    1. [ ] 创建迁移脚本 `V2026_07__p1_system_integrity.sql`
    2. [ ] ALTER `sys_role` 表新增 `data_scope` 字段（默认 SELF）
    3. [ ] CREATE TABLE `sys_user_project`（含唯一索引 uk_user_project）
    4. [ ] CREATE TABLE `biz_project_member`（含唯一索引 uk_project_user）
    5. [ ] CREATE TABLE `sys_config`（含唯一索引 uk_config_key）
    6. [ ] CREATE TABLE `sys_config_change_log`
    7. [ ] CREATE TABLE `biz_machine_work_settlement`（含唯一索引 uk_settlement_code）
    8. [ ] CREATE TABLE `biz_machine_work_settlement_detail`
    9. [ ] CREATE TABLE `biz_approval_snapshot`
    10. [ ] CREATE TABLE `biz_approval_rollback_log`
    11. [ ] ALTER `sys_tenant` 表新增 user_type, start_date, end_date, max_users, modules 字段
    12. [ ] INSERT 初始化系统配置数据（安全/审批/文件/通知设置默认参数）
    13. [ ] 在开发环境执行迁移脚本并验证表结构正确

- [ ] 2. 数据权限拦截器
  - Requirements: R1 (AC 1-10)
  - Dependencies: Task 1, Task 3
  - Description: 实现 MyBatis-Plus DataPermissionInnerInterceptor，根据角色数据范围自动追加 SQL 过滤条件
  - Sub-tasks:
    1. [ ] 创建 `DataScopeEnum` 枚举（ALL/DEPT_AND_CHILDREN/DEPT/PROJECT/SELF）含优先级属性
    2. [ ] 在 `zw-common` 模块创建 `@DataPermission` + `@DataColumn` 注解
    3. [ ] 实现 `ZwDataPermissionHandler`（MyBatis-Plus 数据权限处理接口）
    4. [ ] 实现 `getEffectiveScope()`：多角色取最大数据范围
    5. [ ] 实现 `buildSelfCondition()`：WHERE created_by = #{userId}
    6. [ ] 实现 `buildProjectCondition()`：WHERE project_id IN (用户参与项目ID)
    7. [ ] 实现 `buildDeptCondition()`：WHERE dept_id = #{userDeptId}
    8. [ ] 实现 `buildDeptAndChildrenCondition()`：WHERE dept_id IN (部门及子部门)
    9. [ ] 在 `MybatisPlusConfig` 中注册拦截器（TenantLine 之后、Pagination 之前）
    10. [ ] 在关键 Mapper 方法上添加 `@DataPermission` 注解
    11. [ ] 验证数据权限配置变更后实时生效（无缓存）

- [ ] 3. 项目成员管理后端
  - Requirements: R3 (AC 1-9)
  - Dependencies: Task 1
  - Description: 实现项目成员 CRUD、角色分配、唯一性校验和离职联动逻辑
  - Sub-tasks:
    1. [ ] 创建 `ProjectRoleEnum` 枚举（7种项目角色）
    2. [ ] 创建 `BizProjectMember` 实体类（继承 BaseEntity）
    3. [ ] 创建 `BizProjectMemberMapper` 接口 + XML
    4. [ ] 实现 `addMember()`：含唯一性校验（同项目同用户不可重复）
    5. [ ] 实现 `removeMember()`：含唯一项目经理保护校验
    6. [ ] 实现 `updateRoles()`：更新项目角色列表
    7. [ ] 实现 `listMembers()`：分页查询 + 按角色筛选
    8. [ ] 实现 `getUserProjectIds()`：供数据权限模块使用
    9. [ ] 实现 `deactivateByUserId()`：用户停用时标记所有成员为已失效
    10. [ ] 创建 `ProjectMemberController`（REST API）
    11. [ ] 在项目创建流程中自动将创建人添加为项目经理
    12. [ ] 同步维护 `sys_user_project` 表数据

- [ ] 4. 数据权限配置 API
  - Requirements: R1 (AC 2)
  - Dependencies: Task 2
  - Description: 提供角色数据范围配置 REST API，允许管理员为角色设置数据权限级别
  - Sub-tasks:
    1. [ ] 在 `SysRole` 实体类新增 `dataScope` 字段
    2. [ ] 创建 `DataScopeUpdateRequest` DTO
    3. [ ] 在 `SysRoleService` 新增 `updateDataScope()` 方法
    4. [ ] 在 `SysRoleController` 新增 `PUT /api/v1/system/role/{id}/data-scope`
    5. [ ] 添加权限校验：仅系统管理员可配置
    6. [ ] 单元测试：不合法 dataScope 值被拒绝

- [ ] 5. 引用校验注解 + AOP
  - Requirements: R6 (AC 1-9)
  - Dependencies: Task 1
  - Description: 实现 @ReferenceCheck 注解和 AOP 切面，在删除操作前自动校验引用关系
  - Sub-tasks:
    1. [ ] 在 `zw-common` 创建 `@ReferenceCheck` 和 `@ReferenceRelation` 注解
    2. [ ] 创建 `ReferenceInfoVO` 和 `ReferenceExistsException`
    3. [ ] 实现 `ReferenceCheckAspect` AOP 切面（@Before 拦截）
    4. [ ] 实现 `extractEntityId()` 从方法参数提取实体 ID
    5. [ ] 实现引用计数查询（参数化 SQL 防注入）
    6. [ ] 实现引用详情查询（最多前 10 条）
    7. [ ] 异常处理：DB 查询异常时阻止删除并记录 ERROR 日志
    8. [ ] 人员证件删除添加 @ReferenceCheck（投标报名、投标任务）
    9. [ ] 班组删除添加 @ReferenceCheck（花名册、用工单、工资单）
    10. [ ] 机械台账删除添加 @ReferenceCheck（进出场、工作量、合同）
    11. [ ] 公司证件删除添加 @ReferenceCheck（投标报名）
    12. [ ] 供应商删除添加 @ReferenceCheck（采购合同、入库单、询价）
    13. [ ] 材料字典删除添加 @ReferenceCheck（合同明细、入库明细、库存）
    14. [ ] 全局异常处理器注册 ReferenceExistsException 响应

- [ ] 6. 系统设置后端
  - Requirements: R5 (AC 1-10)
  - Dependencies: Task 1
  - Description: 实现系统参数 CRUD、值范围校验、Redis 缓存和变更日志
  - Sub-tasks:
    1. [ ] 创建 `SysConfig` 实体类 + Mapper
    2. [ ] 创建 `SysConfigChangeLog` 实体类 + Mapper
    3. [ ] 实现 `listByGroup()`：按分组查询配置
    4. [ ] 实现 `updateConfig()`：含值范围校验逻辑
    5. [ ] 实现参数值范围校验器：解析 value_range 格式并按 value_type 校验
    6. [ ] 实现 `batchUpdate()`：批量更新
    7. [ ] 实现 `resetToDefault()`：恢复默认值
    8. [ ] 实现 `getConfigValue()`：Redis 缓存读取（key: sys:config:{key}，过期 1h）
    9. [ ] 实现缓存清除：更新后删除对应 Redis key
    10. [ ] 实现变更日志记录：自动写入 sys_config_change_log
    11. [ ] 创建 `SystemConfigController`（REST API）
    12. [ ] 权限校验：仅系统管理员可修改

- [ ] 7. 薪资统计后端
  - Requirements: R2 (AC 1-9)
  - Dependencies: Task 1
  - Description: 实现劳务薪资按班组/个人维度统计汇总、同比环比计算和 Excel 导出
  - Sub-tasks:
    1. [ ] 创建 VO 类：SalaryStatsSummary, TeamSalaryVO, SalaryDetailVO, SalaryCompareVO
    2. [ ] 创建 `SalaryStatisticsQuery` 查询 DTO
    3. [ ] 实现 `getStatsByTeam()`：按班组分组汇总已审批工资单
    4. [ ] 实现 `getTeamDetail()`：班组内工人明细（出勤、加班、应发、扣款、实发）
    5. [ ] 实现 `generateMonthlyReport()`：汇总报表数据
    6. [ ] 实现 `getCompareData()`：同比/环比变化率（精确小数点后1位）
    7. [ ] 实现 `exportReport()`：EasyExcel 多 Sheet 导出
    8. [ ] 实现自有劳务和零星用工分类统计
    9. [ ] 空数据处理：无审批数据时返回提示
    10. [ ] 金额计算使用 BigDecimal（scale=2, ROUND_HALF_UP）
    11. [ ] 创建 `SalaryStatisticsController`（REST API）
    12. [ ] 从已审批用工单中关联出勤和加班数据

- [ ] 8. 机械工作量结算后端
  - Requirements: R4 (AC 1-9)
  - Dependencies: Task 1
  - Description: 实现机械结算单创建、费用计算、审批流程集成和 Excel 导出
  - Sub-tasks:
    1. [ ] 创建 `BizMachineWorkSettlement` 实体类（继承 BaseEntity）
    2. [ ] 创建 `BizMachineWorkSettlementDetail` 实体类
    3. [ ] 创建 Mapper 接口 + XML
    4. [ ] 创建 VO/DTO：CreateRequest, SettlementVO, SummaryVO
    5. [ ] 实现 `createSettlement()`：周期重叠校验 + 无工作量校验
    6. [ ] 实现周期重叠检测：start1 <= end2 AND start2 <= end1
    7. [ ] 实现费用计算：台班计价 vs 工作量计价（BigDecimal scale=2）
    8. [ ] 实现结算单编号自动生成
    9. [ ] 实现 `submitForApproval()`：启动 Flowable 审批
    10. [ ] 实现 `onApproved()`：审批通过回调，累加合同已结算金额
    11. [ ] 实现 `getProjectSummary()`：项目费用总览
    12. [ ] 实现 `exportSettlement()`：EasyExcel 导出
    13. [ ] 创建 `MachineSettlementController`（REST API）
    14. [ ] 注册 Flowable 机械结算审批流程定义

- [ ] 9. SaaS 多租户管理增强
  - Requirements: R7 (AC 1-10)
  - Dependencies: Task 1
  - Description: 增强租户管理，实现用户类型、续期、停用、功能模块权限和到期自动检查
  - Sub-tasks:
    1. [ ] 扩展 `SysTenant` 实体类新增字段
    2. [ ] 创建 `TenantUserTypeEnum` 和 `TenantStatusEnum`
    3. [ ] 实现 `createTenant()`：自动生成编码 + 初始化管理员 + 设置默认有效期
    4. [ ] 实现 `disableTenant()`：更新状态 + 清除 Redis Token
    5. [ ] 实现 `enableTenant()`：恢复正常状态
    6. [ ] 实现 `renewTenant()`：续期天数校验（1-1095）+ 有效期累加
    7. [ ] 实现 `updateModules()`：配置功能模块权限
    8. [ ] 实现 `checkUserLimit()`：活跃用户数上限校验
    9. [ ] 实现模块权限拦截器：未授权模块 API 返回 403
    10. [ ] 实现定时任务 `checkExpiredTenants()`：每天凌晨检查到期
    11. [ ] 实现定时任务 `sendRenewalReminders()`：到期前 15/7 天提醒
    12. [ ] 登录拦截器增加租户状态校验
    13. [ ] 增强 `TenantController` 新增 API 接口

- [ ] 10. 审批数据回滚
  - Requirements: R8 (AC 1-10)
  - Dependencies: Task 1
  - Description: 实现审批驳回/撤回时自动数据回滚，含快照记录、策略模式和乐观锁重试
  - Sub-tasks:
    1. [ ] 创建 `BizApprovalSnapshot` 实体类 + Mapper
    2. [ ] 创建 `BizApprovalRollbackLog` 实体类 + Mapper
    3. [ ] 定义 `RollbackStrategy` 接口
    4. [ ] 实现 `RollbackStrategyRegistry` 自动注册
    5. [ ] 实现 `saveSnapshot()`：审批提交时记录快照
    6. [ ] 各业务审批提交入口调用 saveSnapshot
    7. [ ] 实现 `executeRollback()`：策略查找 → 冲突检测 → 事务回滚 → 日志
    8. [ ] 实现冲突检测：快照值 vs 当前值不一致标记冲突
    9. [ ] 实现乐观锁重试（最多 3 次）
    10. [ ] 实现 6 种业务回滚策略
    11. [ ] 集成 Flowable TaskListener：驳回/撤回 → Spring Event → 回滚
    12. [ ] 实现超时检测（>5s 标记失败 + 通知管理员）
    13. [ ] 实现回滚记录查询接口
    14. [ ] 实现冲突确认接口
    15. [ ] 创建 `ApprovalRollbackController`（REST API）

- [ ] 11. 项目成员管理前端
  - Requirements: R3 (AC 3, 8)
  - Dependencies: Task 3
  - Description: 实现项目详情页"项目团队"标签页，含成员列表和角色管理操作
  - Sub-tasks:
    1. [ ] 创建 `src/views/project/components/ProjectMember.vue`
    2. [ ] 实现成员列表表格（姓名、部门、角色标签、加入时间、操作列）
    3. [ ] 实现按角色筛选下拉框
    4. [ ] 实现"添加成员"弹窗：用户远程搜索 + 角色多选
    5. [ ] 实现"移除成员"确认弹窗 + 错误提示
    6. [ ] 实现"变更角色"弹窗
    7. [ ] 在项目详情页添加"项目团队"标签页
    8. [ ] API 层封装（src/api/project.ts）
    9. [ ] 错误响应 Toast 提示处理

- [ ] 12. 数据权限配置前端
  - Requirements: R1 (AC 2)
  - Dependencies: Task 4
  - Description: 角色管理页面增加数据权限配置界面
  - Sub-tasks:
    1. [ ] 角色编辑页增加"数据权限"配置区域
    2. [ ] 实现数据范围下拉选择器
    3. [ ] 调用 PUT /api/v1/system/role/{id}/data-scope
    4. [ ] 角色列表展示数据范围标签
    5. [ ] API 层封装

- [ ] 13. 系统设置前端
  - Requirements: R5 (AC 2)
  - Dependencies: Task 6
  - Description: 实现系统设置页面，按分组标签页展示并动态渲染输入控件
  - Sub-tasks:
    1. [ ] 创建 `src/views/system/config/index.vue`
    2. [ ] 实现标签页切换（安全/审批/文件/通知设置）
    3. [ ] 动态表单渲染：按 value_type 渲染控件
    4. [ ] 实现值范围提示
    5. [ ] 实现保存 + 校验失败提示
    6. [ ] 实现"恢复默认值"按钮
    7. [ ] API 层封装（src/api/system.ts）
    8. [ ] 注册路由 + 菜单项

- [ ] 14. 薪资统计前端
  - Requirements: R2 (AC 1-9)
  - Dependencies: Task 7
  - Description: 实现薪资统计页面，含筛选、班组汇总、同比环比和 Excel 导出
  - Sub-tasks:
    1. [ ] 创建 `src/views/labor/salary/stats.vue`
    2. [ ] 实现筛选栏：项目（必选）+ 月份（必选）+ 班组 + 工人姓名
    3. [ ] 实现班组汇总表格
    4. [ ] 实现班组明细展开
    5. [ ] 实现同比环比数据卡片
    6. [ ] 实现自有劳务/零星用工分类 Tab
    7. [ ] 实现 Excel 导出按钮
    8. [ ] 空数据状态展示
    9. [ ] API 层封装（src/api/labor.ts）
    10. [ ] 注册路由 + 菜单项

- [ ] 15. 机械结算前端
  - Requirements: R4 (AC 1-9)
  - Dependencies: Task 8
  - Description: 实现机械结算管理页面，含创建、审批提交、费用总览和导出
  - Sub-tasks:
    1. [ ] 创建 `src/views/machine/settlement/index.vue` 列表页
    2. [ ] 实现结算单列表表格
    3. [ ] 创建 `src/views/machine/settlement/create.vue` 创建页
    4. [ ] 实现创建表单：项目 + 周期选择
    5. [ ] 实现机械明细预览
    6. [ ] 实现提交审批 + 状态展示
    7. [ ] 创建详情页面
    8. [ ] 实现项目费用总览卡片
    9. [ ] 实现 Excel 导出
    10. [ ] API 层封装（src/api/machine.ts）
    11. [ ] 注册路由 + 菜单项

- [ ] 16. 租户管理前端增强
  - Requirements: R7 (AC 9)
  - Dependencies: Task 9
  - Description: 增强租户管理页面，新增停用/启用/续期和功能模块配置
  - Sub-tasks:
    1. [ ] 增强列表表格：用户类型、有效期、使用量列
    2. [ ] 实现状态和类型筛选
    3. [ ] 实现"停用"/"启用"操作按钮
    4. [ ] 实现"续期"弹窗（天数输入 1-1095）
    5. [ ] 实现"功能模块配置"弹窗（模块多选）
    6. [ ] 增强创建表单：用户类型 + 有效期 + 上限
    7. [ ] API 层封装

- [ ] 17. 审批回滚前端
  - Requirements: R8 (AC 9, 10)
  - Dependencies: Task 10
  - Description: 实现回滚记录查询页面和审批详情页回滚状态展示
  - Sub-tasks:
    1. [ ] 创建 `src/views/workflow/rollback/index.vue`
    2. [ ] 实现回滚记录表格
    3. [ ] 实现筛选栏：项目 + 业务类型 + 时间范围 + 状态
    4. [ ] 实现冲突确认操作弹窗
    5. [ ] 审批详情页增加回滚信息区域
    6. [ ] 冲突处理指引文案
    7. [ ] API 层封装（src/api/workflow.ts）
    8. [ ] 注册路由 + 菜单项

- [ ] 18. 引用校验前端集成
  - Requirements: R6 (AC 8)
  - Dependencies: Task 5
  - Description: 在删除确认弹窗中集成引用校验结果，有引用时禁用确认按钮
  - Sub-tasks:
    1. [ ] 创建通用组件 `src/components/ReferenceCheckDialog.vue`
    2. [ ] 实现引用校验结果展示列表
    3. [ ] 有引用时禁用"确认删除"按钮
    4. [ ] 无引用时正常删除流程
    5. [ ] 在证件/班组/台账/供应商/材料字典删除中集成
    6. [ ] 统一解析 ReferenceExistsException 响应

- [ ] 19. Property-Based Tests
  - Requirements: R1-R8（正确性属性 1-20）
  - Dependencies: Task 2, Task 3, Task 5, Task 6, Task 7, Task 8, Task 9, Task 10
  - Description: 使用 jqwik 框架编写属性测试，验证核心业务逻辑的正确性属性
  - Sub-tasks:
    1. [ ] 添加 jqwik 依赖到 pom.xml（net.jqwik:jqwik:1.8.x）
    2. [ ] Property 1：DataPermissionHandler SQL 过滤条件正确性
    3. [ ] Property 2：多角色数据范围优先级计算
    4. [ ] Property 3：薪资汇总聚合精度
    5. [ ] Property 5：同比环比变化率公式
    6. [ ] Property 9：机械费用计算公式正确性
    7. [ ] Property 10：结算周期重叠检测
    8. [ ] Property 12：系统参数值范围校验
    9. [ ] Property 14：引用校验决策逻辑
    10. [ ] Property 15：租户续期日期计算
    11. [ ] Property 18：快照-回滚 Round Trip
    12. [ ] Property 19：回滚冲突检测
    13. [ ] Property 20：乐观锁重试机制
    14. [ ] 确保每个属性测试至少 100 次迭代 + @Tag 标注

- [ ] 20. 集成测试
  - Requirements: R1-R8
  - Dependencies: Task 19
  - Description: Spring Boot Test 集成测试，验证服务间协作和端到端业务流程
  - Sub-tasks:
    1. [ ] 搭建集成测试基础设施（@SpringBootTest + Testcontainers）
    2. [ ] 数据权限拦截器 SQL 拼接 + 查询结果验证
    3. [ ] 项目成员添加 → 数据权限 PROJECT 范围联动
    4. [ ] 系统配置更新 → Redis 缓存清除 → 读取最新值
    5. [ ] 机械结算 → Flowable 审批 → 回调 → 金额累加
    6. [ ] 审批快照 → 驳回事件 → 回滚 → 数据恢复
    7. [ ] 回滚乐观锁冲突 → 重试机制
    8. [ ] 租户停用 → Token 清除 → 登录拒绝
    9. [ ] 租户到期 → 状态更新 → 登录拒绝
    10. [ ] 引用校验阻止删除 + 无引用正常删除
    11. [ ] 薪资统计汇总 + Excel 导出验证
    12. [ ] 运行全部测试并确保通过

## Notes

- Wave 2 中的 Task 2 依赖 Task 3（项目成员管理提供 getUserProjectIds 给数据权限的 PROJECT 范围），其余 Wave 2 任务仅依赖 Task 1
- Wave 3 的 Task 4 依赖 Task 2（数据权限拦截器就绪后再提供配置 API）
- 前端任务（Wave 4）各自独立，仅依赖对应的后端任务
- 测试任务（Wave 5）依赖全部后端任务完成
- 所有金额字段使用 BigDecimal，禁止 float/double
- Redis key 统一前缀：`sys:config:`, `token:tenant:`
