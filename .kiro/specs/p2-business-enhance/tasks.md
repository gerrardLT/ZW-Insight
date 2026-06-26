# Implementation Plan: P2 Business Enhance

## Overview

实现 ZW-Insight P2 第二批功能增强，包含四个功能模块：项目看板（独立项目维度）、财务封账与税率配置、移动端快捷入口自定义编辑。后端使用 Java 17 + Spring Boot 3.2.6 + MyBatis-Plus 3.5.5 + Redis 7，PC 前端使用 Vue 3.4 + Element Plus 2.6 + ECharts 5.5 + TypeScript，移动端使用 uni-app。属性测试使用 jqwik 1.9.1。

按功能模块分组，后端先于前端，基础设施（数据库/实体）先于业务逻辑，属性测试随对应功能模块实现。

## Tasks

- [x] 1. 数据库表与实体类创建
  - [x] 1.1 创建 SQL 迁移脚本 — 新增 biz_finance_lock、biz_tax_rate、msg_available_shortcut 三张表
    - 创建 `sql/p2-business-enhance.sql`
    - `biz_finance_lock` 表：id, period(VARCHAR 7), lock_type, status, project_id, lock_by, lock_time, unlock_by, unlock_time, tenant_code, create_time, update_time, deleted；唯一索引 `uk_period_project(period, project_id, tenant_code)`
    - `biz_tax_rate` 表：id, name(VARCHAR 30), rate_value(DECIMAL 5,2), status, tenant_code, create_time, update_time, deleted；唯一索引 `uk_name_tenant(name, tenant_code)`
    - `msg_available_shortcut` 表：id, name, icon, route_path, sort_order, status, create_time
    - `msg_user_shortcut` 表新增 `shortcut_id` BIGINT 字段
    - 插入 msg_available_shortcut 初始数据（系统预定义快捷功能项）
    - _Requirements: 3.1, 5.1, 7.1_

  - [x] 1.2 创建 biz_finance_lock 实体类和 Mapper
    - 在 `zw-finance` 模块创建 `BizFinanceLock.java` 实体（继承 BaseEntity，使用 @TableName 注解）
    - 创建 `BizFinanceLockMapper.java` 继承 BaseMapper
    - 创建 `FinanceLockDTO.java`（id, period, lockType, status, lockBy, lockTime, unlockBy, unlockTime）
    - _Requirements: 3.1, 3.2_

  - [x] 1.3 创建 biz_tax_rate 实体类和 Mapper
    - 在 `zw-finance` 模块创建 `BizTaxRate.java` 实体
    - 创建 `BizTaxRateMapper.java` 继承 BaseMapper
    - 创建 `TaxRateDTO.java`（id, name, rateValue, status, createTime）
    - _Requirements: 5.1_

  - [x] 1.4 创建 msg_available_shortcut 实体类和 Mapper
    - 在 `zw-message` 模块创建 `MsgAvailableShortcut.java` 实体
    - 创建 `MsgAvailableShortcutMapper.java` 继承 BaseMapper
    - 扩展 `MsgUserShortcut.java` 增加 shortcutId 字段
    - 创建 `ShortcutBatchSaveRequest.java`（shortcutIds: List<Long>）和 `ShortcutBatchSaveResponse.java`（savedIds, invalidIds）
    - _Requirements: 7.1, 7.3_

  - [x] 1.5 创建项目看板 DTO 类
    - 在 `zw-dashboard` 模块创建 `ProjectDashboardDTO.java`（聚合四维度）
    - 创建 `BudgetExecutionDTO.java`（totalBudget, usedAmount, usageRate, subjects 列表）
    - 创建 `SubjectDetailDTO.java`（subjectName, budget, paid, ratio）
    - 创建 `ProgressDTO.java`（totalTasks, completedTasks, completionRate）
    - 创建 `ContractReceiptDTO.java`（contractTotal, invoicedAmount, receivedAmount, receiptRate）
    - 创建 `OutputTrendDTO.java`（totalOutput, monthOutput, trend 列表）和 `MonthlyOutputDTO.java`（month, amount）
    - _Requirements: 1.1, 1.2, 1.3, 1.4_

- [x] 2. 项目看板后端服务
  - [x] 2.1 实现 ProjectDashboardService — 预算执行与进度计算
    - 创建 `ProjectDashboardService.java`，注入跨模块 Mapper
    - 实现 `getBudgetExecution(Long projectId)` — 查询 zw-budget 模块预算数据，按一级成本科目分组聚合，计算使用率（BigDecimal, RoundingMode.HALF_UP, 4位小数，分母为0返回0）
    - 实现 `getProgress(Long projectId)` — 查询 zw-site 模块进度计划数据，统计总任务数/已完成数，计算完成百分比（BigDecimal, 4位小数，分母为0返回0）
    - _Requirements: 1.1, 1.2, 9.1, 9.3_

  - [x] 2.2 实现 ProjectDashboardService — 合同回款与产值趋势
    - 实现 `getContractReceipt(Long projectId)` — 查询 zw-contract 合同数据和 zw-finance 回款数据，计算回款率（BigDecimal, 4位小数，分母为0返回0）
    - 实现 `getOutputTrend(Long projectId)` — 查询产值上报数据，汇总累计产值/本月产值，生成近12月趋势列表（按月份升序排列）
    - 实现 `getProjectOverview(Long projectId)` — 聚合调用上述四个方法
    - _Requirements: 1.3, 1.4, 9.2, 9.4_

  - [x] 2.3 实现 ProjectDashboardController
    - 创建 `ProjectDashboardController.java`，路径前缀 `/api/v1/dashboard/project`
    - 实现 5 个 GET 接口：`/{projectId}/budget`、`/{projectId}/progress`、`/{projectId}/contract`、`/{projectId}/output`、`/{projectId}/overview`
    - 项目不存在返回 404，无权限返回 403（复用现有数据权限拦截器）
    - 某维度无数据时返回零值和空列表，不影响其他维度
    - _Requirements: 1.1–1.8, 2.1_

  - [x]* 2.4 属性测试 — ProjectDashboardPropertyTest（Property 1-5）
    - **Property 1: 预算使用率计算正确** — 生成非负 BigDecimal 对(totalBudget, usedAmount)，验证 usageRate = usedAmount/totalBudget (4位小数 HALF_UP)，totalBudget=0时返回0
    - **Property 2: 回款率计算正确** — 生成非负 BigDecimal 对(contractTotal, receivedAmount)，验证 receiptRate = received/contractTotal (4位小数 HALF_UP)，contractTotal=0时返回0
    - **Property 3: 完成百分比计算正确** — 生成非负 int 对(totalTasks, completedTasks)，验证 completionRate = completed/total (4位小数 HALF_UP)，total=0时返回0
    - **Property 4: 月度产值趋势升序排列** — 生成随机产值记录集合，验证返回的 trend 列表按月份自然升序
    - **Property 5: 比率结果为非负 BigDecimal** — 验证所有输入组合下 usageRate/receiptRate/completionRate 均为 BigDecimal 且 ≥ 0
    - **Validates: Requirements 1.1, 1.2, 1.3, 1.4, 9.1, 9.2, 9.3, 9.4, 9.5**

- [x] 3. Checkpoint — 项目看板后端验证
  - Ensure all tests pass, ask the user if questions arise.

- [x] 4. 财务封账后端服务
  - [x] 4.1 实现 FinanceLockService
    - 创建 `FinanceLockService.java`
    - 实现 `createLock(period, lockType)` — 校验 period ≤ 当前会计期间，检查是否已存在 LOCKED 状态记录（已存在则拒绝），季度封账展开为3个自然月分别创建记录，写入 MySQL 并刷新 Redis 缓存（key: `finance:lock:{YYYY-MM}`，value: LOCKED，TTL 24h）
    - 实现 `unlock(id)` — 更新状态为 UNLOCKED，记录解封人和时间，刷新 Redis 缓存为 UNLOCKED
    - 实现 `getPage(pageNum, pageSize)` — 分页查询封账记录，按 period 倒序排列，默认20条/页，最大100条
    - 实现 `getStatus(period)` — 查询指定年月封账状态
    - 角色校验：非财务管理员角色拒绝操作返回 403
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7_

  - [x] 4.2 实现 @FinanceLockCheck 注解与 FinanceLockAspect
    - 创建 `@FinanceLockCheck` 注解（@Target METHOD, @Retention RUNTIME），属性 `dateField` 默认 "applyDate"
    - 创建 `FinanceLockAspect.java`（@Aspect, @Component）
    - @Before 拦截：通过反射从方法参数对象提取 dateField 对应的日期值
    - 日期为空时抛 BusinessException("业务日期不可为空")
    - 优先从 Redis 查询封账状态（`finance:lock:{YYYY-MM}`）
    - Redis 不可用时降级查询 biz_finance_lock 表
    - Redis 和 DB 均不可用时抛 BusinessException("系统暂时无法校验封账状态，请稍后重试")
    - 命中 LOCKED 状态时抛 BusinessException("期间{period}已封账，禁止{operation}")
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5, 4.6, 4.7, 4.8_

  - [x] 4.3 在财务模块 Controller 方法标注 @FinanceLockCheck
    - 在开票申请（InvoiceApplyController）的新增/编辑/删除方法标注 `@FinanceLockCheck(dateField="applyDate")`
    - 在回款登记（PaymentReceivedController）的新增/编辑/删除方法标注 `@FinanceLockCheck(dateField="receivedDate")`
    - 在收票登记（InvoiceReceivedController）的新增/编辑/删除方法标注
    - 在付款申请（PaymentApplyController）的新增/编辑/删除方法标注
    - 在其他费用付款（OtherPaymentController）的新增/编辑/删除方法标注
    - 在项目报销（ReimbursementController）的新增/编辑/删除方法标注
    - 在个人报销（PersonalReimbursementController）的新增/编辑/删除方法标注
    - _Requirements: 4.4_

  - [x] 4.4 实现 FinanceLockController
    - 创建 `FinanceLockController.java`，路径前缀 `/api/v1/finance/lock`
    - 实现 POST `/` 创建封账、DELETE `/{id}/unlock` 解封、GET `/page` 分页查询、GET `/status` 查询状态
    - 统一使用 R<T> 响应包装
    - _Requirements: 3.1, 3.2, 3.3, 3.5_

  - [x]* 4.5 属性测试 — FinanceLockPropertyTest（Property 6-11, 21）
    - **Property 6: 封账期匹配时拦截所有写操作** — 生成 LOCKED 期间和匹配业务日期的单据，验证拦截器返回失败
    - **Property 7: 非封账期正常放行** — 生成不匹配任何 LOCKED 期间的业务日期，验证放行
    - **Property 8: 解封后等同未封账** — 对已封账再解封的期间，验证拦截器放行
    - **Property 9: 同期间封账幂等性** — 对同一期间多次封账操作，验证仅一条 LOCKED 记录
    - **Property 10: 无效业务日期拦截** — 生成 null/空串/非日期字符串，验证拦截器返回失败
    - **Property 11: 封账-解封状态往返** — 执行封账→解封→再封账序列，验证最终状态为 LOCKED
    - **Property 21: 封账记录查询按期间倒序** — 插入多条不同期间记录，验证查询结果按 period 降序排列
    - **Validates: Requirements 3.3, 3.4, 3.5, 4.1–4.8, 10.1–10.5**

- [x] 5. 税率管理后端服务
  - [x] 5.1 实现 TaxRateService
    - 创建 `TaxRateService.java`
    - 实现 `create(name, rateValue)` — 校验名称（非空 1-30字符）和数值（0.01-99.99 且≤2位小数），检查名称唯一性（含停用状态），保存并返回完整记录
    - 实现 `update(id, name, rateValue)` — 同上校验 + 排除自身的名称唯一性检查，更新并返回
    - 实现 `delete(id)` — 逻辑删除（status → DISABLED）
    - 实现 `listEnabled()` — 查询 ENABLED 状态记录，按 createTime 升序排列
    - 实现 `listAll()` — 查询全部记录（含 DISABLED），按 createTime 升序
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5, 5.6, 5.7_

  - [x] 5.2 实现 TaxRateController
    - 创建 `TaxRateController.java`，路径前缀 `/api/v1/finance/tax-rate`
    - 实现 POST `/` 新增、PUT `/{id}` 修改、DELETE `/{id}` 停用、GET `/list` 查询启用列表、GET `/all` 查询全部
    - _Requirements: 5.1, 5.2, 5.3, 5.4_

  - [x]* 5.3 属性测试 — TaxRatePropertyTest（Property 12-15, 22）
    - **Property 12: 税率保存-查询往返一致** — 生成合法名称和数值，新增后查询验证一致，修改后查询验证更新
    - **Property 13: 税率逻辑删除保留数据** — 对已启用记录执行删除，验证状态变 DISABLED 且全量查询可见
    - **Property 14: 税率名称唯一性约束** — 生成已存在名称，验证重复创建/修改被拒绝
    - **Property 15: 税率数值与名称校验** — 生成越界数值和非法名称，验证被拒绝
    - **Property 22: 税率列表按创建时间升序** — 插入多条记录，验证列表查询按 createTime 升序
    - **Validates: Requirements 5.1–5.7**

- [x] 6. 快捷入口后端服务扩展
  - [x] 6.1 扩展 UserShortcutService — 批量保存逻辑
    - 在现有 `UserShortcutService.java` 中实现 `batchSave(userId, shortcutIds)` 方法
    - 校验 shortcutIds 数量 1-8 个（超出返回错误）
    - 过滤无效 ID（不存在于 msg_available_shortcut 启用列表中的）
    - 对重复 ID 去重，保留首次出现的顺序位置
    - 过滤后为空时拒绝保存并返回错误
    - 以整体替换方式持久化（先删除该用户所有记录，再按顺序插入）
    - 返回 ShortcutBatchSaveResponse（savedIds + invalidIds）
    - 实现 `getAvailableList()` — 查询所有启用的 Available_Shortcut，上限50项
    - 实现 `getUserConfig(userId)` — 查询该用户已选配置，按 sort_order 升序；未配置过返回默认列表（不超过4项）
    - _Requirements: 7.1, 7.2, 7.3, 7.4, 7.5, 7.6, 7.7, 7.8_

  - [x] 6.2 扩展 UserShortcutController — 新增接口
    - 在现有 `UserShortcutController.java` 中新增：
    - GET `/api/v1/message/shortcut/available` — 获取全部可选功能列表
    - POST `/api/v1/message/shortcut/batch` — 批量保存用户配置（整体替换）
    - 修改现有 GET `/api/v1/message/shortcut` — 使用新的 getUserConfig 逻辑
    - _Requirements: 7.1, 7.2, 7.3_

  - [x]* 6.3 属性测试 — ShortcutConfigPropertyTest（Property 16-20）
    - **Property 16: 快捷入口保存-查询往返一致** — 生成有效 ID 列表(1-20个，不重复，均在可选集合中)，保存后查询验证顺序一致
    - **Property 17: 用户配置隔离** — 对用户A保存配置，验证用户B配置不变
    - **Property 18: 无效ID过滤逻辑** — 生成含有效和无效 ID 的混合列表，验证仅有效 ID 被保存
    - **Property 19: 重复ID去重保留首次位置** — 生成含重复 ID 的列表，验证去重后保留首次位置
    - **Property 20: 空列表清空配置** — 保存空列表，验证后续查询返回空
    - **Validates: Requirements 7.3, 7.5, 7.6, 11.1–11.5**

- [x] 7. Checkpoint — 全部后端服务验证
  - Ensure all tests pass, ask the user if questions arise.

- [x] 8. PC 前端 — 项目看板页面
  - [x] 8.1 创建项目看板 API 层
    - 新建 `zw-insight-web/src/api/dashboard.ts`
    - 定义 TypeScript 接口类型：ProjectDashboardDTO、BudgetExecutionDTO、ProgressDTO、ContractReceiptDTO、OutputTrendDTO、MonthlyOutputDTO
    - 实现 API 函数：`getProjectBudget`、`getProjectProgress`、`getProjectContract`、`getProjectOutput`、`getProjectOverview`
    - _Requirements: 2.1_

  - [x] 8.2 实现项目看板页面 — 布局与项目选择器
    - 新建 `zw-insight-web/src/views/dashboard/project-dashboard.vue`
    - 页面顶部集成 `ProjectSelector` 组件，用户选择项目后触发数据加载
    - 四宫格布局（el-row + el-col）：预算执行、项目进度、合同回款、月度产值
    - 实现加载状态（v-loading）、错误状态（单维度失败不影响其他）、空状态（"暂无数据"）
    - 切换项目时清除当前数据、重新显示加载状态并请求新数据
    - _Requirements: 2.1, 2.6, 2.7, 2.8_

  - [x] 8.3 实现项目看板页面 — ECharts 图表渲染
    - 预算执行：ECharts 饼图或柱状图，展示已执行金额与剩余预算占比，金额精度小数点后两位
    - 项目进度：环形图/进度条展示完成率，数值范围 0%–100%，精度整数
    - 合同回款：柱状图双柱并列（合同金额 vs 回款金额），Y 轴单位万元
    - 月度产值：折线图，X 轴月份，Y 轴产值金额（万元）
    - 监听 window.resize 事件，300ms 内完成图表自适应重绘（debounce）
    - _Requirements: 2.2, 2.3, 2.4, 2.5, 2.9_

  - [x] 8.4 注册路由和菜单
    - 在路由配置中添加项目看板路由，指向 `project-dashboard.vue`
    - 准备 `data-menu.sql` 中菜单 INSERT 语句
    - _Requirements: 2.1_

- [x] 9. PC 前端 — 财务封账管理页面
  - [x] 9.1 创建封账管理 API 层
    - 新建 `zw-insight-web/src/api/finance-lock.ts`
    - 定义 TypeScript 接口：FinanceLockDTO、FinanceLockCreateRequest
    - 实现 API 函数：`createLock`、`unlockPeriod`、`getLockPage`、`getLockStatus`
    - _Requirements: 3.1, 3.3, 3.5_

  - [x] 9.2 实现封账管理页面
    - 新建 `zw-insight-web/src/views/finance/finance-lock/index.vue`
    - 表格展示封账记录列表（列：期间、封账类型、状态、操作人、操作时间、操作按钮），支持分页
    - 实现"新增封账"按钮及对话框（选择年月 + 封账类型：月度/季度）
    - 实现"解封"按钮（确认对话框后调用解封接口）
    - 仅财务管理员角色可见操作按钮
    - _Requirements: 3.1, 3.2, 3.3, 3.5, 3.6_

- [x] 10. PC 前端 — 税率管理与表单集成
  - [x] 10.1 创建税率管理 API 层和页面
    - 新建 `zw-insight-web/src/api/tax-rate.ts`
    - 定义 TypeScript 接口：TaxRateDTO
    - 实现 API 函数：`createTaxRate`、`updateTaxRate`、`deleteTaxRate`、`getTaxRateList`、`getAllTaxRates`
    - 新建 `zw-insight-web/src/views/finance/tax-rate/index.vue`
    - 表格展示税率列表（列：名称、税率数值、状态、创建时间、操作）
    - 实现新增/编辑对话框（名称≤30字符校验、数值0.01-99.99校验）
    - 实现停用（逻辑删除）确认对话框
    - _Requirements: 5.1, 5.2, 5.3, 5.4_

  - [x] 10.2 集成税率选择器到开票申请和收票登记表单
    - 创建通用税率选择器组件 `TaxRateSelector.vue`（el-select filterable，选项格式"税率名称（数值%）"）
    - 在开票申请表单（invoice-apply 相关 .vue）中集成 TaxRateSelector，选择后自动填入税率字段（精度2位小数）
    - 在收票登记表单（invoice-received 相关 .vue）中集成 TaxRateSelector
    - 保留手动输入能力（0.00-100.00），手动修改时清除下拉选中状态
    - 税率列表加载失败时显示提示，仍允许手动输入
    - _Requirements: 6.1, 6.2, 6.3, 6.4, 6.5, 6.6_

- [x] 11. Checkpoint — PC 前端验证
  - Ensure all tests pass, ask the user if questions arise.

- [x] 12. 移动端 — 快捷入口编辑页面
  - [x] 12.1 创建移动端快捷入口 API 层
    - 新建 `zw-insight-app/src/api/shortcut.ts`
    - 定义 TypeScript 接口：AvailableShortcut、UserShortcutConfig、ShortcutBatchSaveRequest/Response
    - 实现 API 函数：`getAvailableShortcuts`、`getUserShortcuts`、`batchSaveShortcuts`
    - _Requirements: 7.1, 7.2, 7.3_

  - [x] 12.2 实现快捷入口编辑页面（shortcut-edit.vue）
    - 新建 `zw-insight-app/src/pages/home/shortcut-edit.vue`
    - 页面分两个区域：已选功能区（movable-area/movable-view 实现拖拽排序）和可选功能区
    - 已选功能区：展示当前已选入口（1-8个），每项带移除按钮，支持长按拖拽排序（300ms 内视觉反馈）
    - 可选功能区：展示未被选中的可用功能，点击添加到已选区末尾
    - 数量限制：已选达8个时点击添加显示提示"已达到最大可选数量"；仅剩1个时点击移除显示提示"至少保留1个快捷入口"
    - 保存按钮：调用 batchSave 接口，成功显示提示并1.5秒后返回首页；失败/超时（10秒）显示失败提示并保留编辑状态
    - _Requirements: 8.1, 8.2, 8.3, 8.4, 8.5, 8.6, 8.7, 8.8, 8.9, 8.10, 8.11_

  - [x] 12.3 集成首页快捷入口展示逻辑
    - 修改首页组件，调用 `getUserShortcuts` 获取用户个性化配置渲染快捷入口
    - 未配置过的用户展示系统默认入口（≤4项）
    - 添加"编辑"入口按钮跳转 shortcut-edit 页面
    - _Requirements: 7.2, 7.4_

- [x] 13. Final Checkpoint — 全功能集成验证
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- 后端使用 Java 17 + Spring Boot 3.2.6 + MyBatis-Plus 3.5.5 + Redis 7
- PC 前端使用 Vue 3.4 + Element Plus 2.6 + ECharts 5.5 + TypeScript + Vite 5.2
- 移动端使用 uni-app，拖拽基于 movable-area/movable-view 原生组件
- 属性测试使用 jqwik 1.9.1（项目已集成），每个属性最少100次迭代
- 封账拦截采用 @FinanceLockCheck AOP 注解模式，与现有 @BudgetCheck 风格一致
- Redis 缓存 key 格式：`finance:lock:{YYYY-MM}`，TTL 24h
- 封账/解封操作主动刷新 Redis 缓存
- 所有 BigDecimal 计算使用 RoundingMode.HALF_UP
- 税率选择器为通用组件，可复用于其他需要税率选择的表单
- Property tests validate universal correctness properties defined in design
- Checkpoints ensure incremental validation

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1"] },
    { "id": 1, "tasks": ["1.2", "1.3", "1.4", "1.5"] },
    { "id": 2, "tasks": ["2.1", "4.1", "5.1", "6.1"] },
    { "id": 3, "tasks": ["2.2", "4.2", "5.2", "6.2"] },
    { "id": 4, "tasks": ["2.3", "4.3", "4.4", "2.4", "5.3", "6.3"] },
    { "id": 5, "tasks": ["4.5", "8.1", "9.1", "10.1", "12.1"] },
    { "id": 6, "tasks": ["8.2", "8.3", "8.4", "9.2", "10.2", "12.2"] },
    { "id": 7, "tasks": ["12.3"] }
  ]
}
```
