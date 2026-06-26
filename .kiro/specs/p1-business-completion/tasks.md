# Implementation Plan: P1 业务完善功能

## Overview

本实现计划覆盖 ZW-Insight 平台 8 项 P1 级别业务完善功能，采用增量式实现策略。每个功能独立成组，按照数据层 → 服务层 → 控制层 → 前端的顺序逐步实现，并在关键节点设置验证检查点。

## Tasks

- [x] 1. 机械工作量结算 - 审批通过状态回写
  - [x] 1.1 补充审批通过事件监听与工作日志状态回写
    - 在 `MachineWorkSettlementService` 中添加 `@EventListener` 方法监听 `WorkflowApprovedEvent`
    - 当 businessType 为 MACHINE_WORK_SETTLEMENT 时，更新结算单状态为已审批
    - 批量更新关联工作日志的 settlement_status 为 "SETTLED"
    - 在 `BizMachineWorkLog` 实体中确认 settlementStatus 字段存在
    - 在 Mapper 中添加 `batchUpdateStatus(List<Long> ids, String status)` 方法
    - _Requirements: 1.6_

  - [x] 1.2 创建结算单时排除已结算工作日志
    - 修改 `createSettlement` 方法中的工作日志查询条件，增加 `.ne(BizMachineWorkLog::getSettlementStatus, "SETTLED")`
    - 如果某日志已被包含在其他结算单中，则排除并在返回结果中标注
    - _Requirements: 1.7_

  - [x]* 1.3 编写属性测试 - 机械结算汇总正确性
    - **Property 1: 机械结算汇总正确性**
    - **Validates: Requirements 1.2, 1.3**
    - 使用 jqwik 随机生成工作日志列表（不同机械ID、不同日期），验证结算明细分组数 == 工作日志按机械ID分组数，每条明细的台班数 == 对应分组工作日志台班数之和

  - [x]* 1.4 编写属性测试 - 机械结算总金额不变量
    - **Property 2: 机械结算总金额不变量**
    - **Validates: Requirements 1.4**
    - 使用 jqwik 随机生成明细行（数量、单价），验证 settlement.totalAmount == sum(detail.subtotal)

  - [x]* 1.5 编写属性测试 - 审批通过后工作日志状态变更
    - **Property 3: 审批通过后工作日志状态变更**
    - **Validates: Requirements 1.6**
    - 使用 jqwik 生成随机结算单和工作日志集合，验证审批通过后所有关联日志 status == "SETTLED"

  - [x]* 1.6 编写属性测试 - 工作日志结算唯一性
    - **Property 4: 工作日志结算唯一性**
    - **Validates: Requirements 1.7**
    - 使用 jqwik 验证不同结算单的工作日志ID集合无交集

- [x] 2. 分包结算明细表
  - [x] 2.1 创建数据库表和实体类
    - 执行 SQL 创建 `biz_subcontract_settlement_detail` 表
    - 创建 `BizSubcontractSettlementDetail` 实体类（含 @TableName、@TableId 注解）
    - 创建 `SubcontractSettlementDetailMapper` 接口（继承 BaseMapper）
    - _Requirements: 2.1, 2.2_

  - [x] 2.2 实现分包结算服务层（含明细管理）
    - 创建 `SubcontractSettlementService`，实现 createSettlement 方法
    - 实现明细行金额计算：amount = quantity × unitPrice（保留2位小数，HALF_UP）
    - 实现总金额计算：totalAmount = sum(detail.amount)
    - 实现 updateSettlement 方法，支持修改明细行后重新计算总金额
    - 创建 `SubcontractSettlementCreateRequest` 和 `SubcontractSettlementDetailDTO` 请求对象
    - _Requirements: 2.3, 2.4, 2.5_

  - [x] 2.3 实现分包结算 Controller 和 Excel 导出
    - 创建 `SubcontractSettlementController`，实现 CRUD + 提交审批接口
    - 实现 `GET /{id}/export` 接口，使用 EasyExcel 导出结算明细
    - _Requirements: 2.3, 2.6_

  - [x]* 2.4 编写属性测试 - 分包结算金额计算不变量
    - **Property 5: 分包结算金额计算不变量**
    - **Validates: Requirements 2.4, 2.5**
    - 使用 jqwik 随机生成明细行（quantity、unitPrice），验证行金额 == quantity × unitPrice，总金额 == sum(行金额)

- [x] 3. Checkpoint - 确保结算功能通过验证
  - Ensure all tests pass, ask the user if questions arise.

- [x] 4. 供应商黑名单自动拦截
  - [x] 4.1 实现 AOP 注解和拦截切面
    - 创建 `@BlacklistCheck` 注解（@Target(METHOD), @Retention(RUNTIME)）
    - 创建 `SupplierBlacklistAspect` 切面，使用 `@Before("@annotation(blacklistCheck)")` 拦截
    - 从方法参数中提取 supplierId，调用 `SupplierBlacklistService.isBlacklisted()` 校验
    - 拒绝时抛出 BusinessException，消息格式："该供应商已被列入黑名单（原因：{reason}），禁止签约"
    - _Requirements: 3.1, 3.2, 3.3_

  - [x] 4.2 扩展 SupplierBlacklistService 并在合同保存方法上添加注解
    - 在 `SupplierBlacklistService` 中新增 `getBlacklistReason(Long supplierId)` 方法
    - 在采购合同保存方法（PurchaseContractService.create/update）上添加 `@BlacklistCheck`
    - 在分包合同保存方法（SubcontractService.create/update）上添加 `@BlacklistCheck`
    - _Requirements: 3.1, 3.2, 3.5_

  - [x] 4.3 前端合同表单添加供应商黑名单实时校验
    - 在采购合同和分包合同的 Vue 表单中，供应商选择后调用 `GET /api/v1/basedata/supplier/blacklist/check/{supplierId}`
    - 如果返回 blacklisted=true，显示红色提示文字并禁用提交按钮
    - _Requirements: 3.4_

  - [x]* 4.4 编写属性测试 - 黑名单拦截一致性
    - **Property 6: 黑名单拦截一致性**
    - **Validates: Requirements 3.1, 3.2, 3.3, 3.5**
    - 使用 jqwik 随机生成供应商（在/不在黑名单），验证拦截行为一致

- [x] 5. 人事花名册统计
  - [x] 5.1 实现人事统计服务和接口
    - 创建 `HrStatisticsVO`（含 DeptStatItem、PostStatItem、SeniorityStatItem、TrendStatItem 内部类）
    - 创建 `HrStatisticsService`，实现按部门/岗位/工龄段/月度趋势的聚合查询
    - 在 Mapper XML 中编写聚合统计 SQL（按工龄段分组使用 TIMESTAMPDIFF）
    - 创建 `HrStatisticsController`，实现 `GET /api/v1/hr/statistics/overview` 接口
    - _Requirements: 4.1, 4.2, 4.4_

  - [x] 5.2 PC 端人事统计页面
    - 创建 `src/views/hr/statistics.vue` 页面
    - 使用 ECharts 展示部门人数柱状图、岗位饼图、工龄段分布图、入离职趋势折线图
    - 顶部展示汇总卡片（在职总人数、本月入职、本月离职）
    - _Requirements: 4.3_

  - [x]* 5.3 编写属性测试 - 人事统计分区不变量
    - **Property 7: 人事统计分区不变量**
    - **Validates: Requirements 4.1, 4.2**
    - 使用 jqwik 随机生成人员数据集，验证按部门/岗位/工龄段统计之和 == 在职总人数

- [x] 6. Checkpoint - 确保黑名单拦截和人事统计通过验证
  - Ensure all tests pass, ask the user if questions arise.

- [x] 7. 合同到期提醒
  - [x] 7.0 执行 biz_expense_contract 表结构变更
    - 执行 `ALTER TABLE biz_expense_contract ADD COLUMN IF NOT EXISTS end_date DATE COMMENT '合同到期日期'`
    - 执行 `ALTER TABLE biz_expense_contract ADD COLUMN IF NOT EXISTS contract_name VARCHAR(200) COMMENT '合同名称'`
    - 这两个字段是合同到期扫描（功能5）和档案展示（功能8）的前置依赖
    - _Requirements: 5.2, 5.4, 8.4_

  - [x] 7.1 创建到期提醒日志表和实体
    - 执行 SQL 创建 `biz_contract_expiry_log` 表
    - 创建 `BizContractExpiryLog` 实体和对应 Mapper
    - _Requirements: 5.1_

  - [x] 7.2 实现合同到期判断和通知服务
    - 创建 `ContractExpiryService`，实现以下方法：
      - `determineLevel(LocalDate endDate, LocalDate today)` → URGENT/UPCOMING/null
      - `shouldSkip(ContractExpiryDTO contract)` → 跳过 CLOSED/SETTLED/TERMINATED 状态
      - `shouldSendNotification(Long contractId, String level)` → Redis 去重判断
      - `markAsSent(Long contractId, String level)` → Redis 标记已发送（90天过期）
      - `sendExpiryNotification(...)` → 调用 MessageService 发送站内消息
      - `queryExpiringContracts(LocalDate today, LocalDate thirtyDaysLater)` → 联合查询多类型合同
    - 消息内容必须包含：合同编号、合同名称、供应商/分包商名称、到期日期、剩余天数
    - _Requirements: 5.2, 5.3, 5.4, 5.5, 5.6, 5.7_

  - [x] 7.3 实现定时任务
    - 创建 `ContractExpiryTask`，使用 `@Scheduled(cron = "0 0 9 * * ?")` 配置每日09:00执行
    - 使用 Redis 分布式锁确保集群环境只执行一次
    - 遍历到期合同调用 processContract 方法
    - _Requirements: 5.1_

  - [x]* 7.4 编写属性测试 - 合同到期通知级别判断
    - **Property 8: 合同到期通知级别判断**
    - **Validates: Requirements 5.2, 5.3, 5.7**
    - 使用 jqwik 随机生成到期日和当前日期组合，验证级别判断逻辑

  - [x]* 7.5 编写属性测试 - 到期提醒去重幂等性
    - **Property 10: 到期提醒去重幂等性**
    - **Validates: Requirements 5.5**
    - 验证首次发送成功，第二次同级别执行应跳过

  - [x]* 7.6 编写属性测试 - 到期消息完整性
    - **Property 9: 到期提醒消息完整性**
    - **Validates: Requirements 5.4**
    - 使用 jqwik 随机生成合同数据，验证发送的消息内容必须包含：合同编号、合同名称、供应商/分包商名称、到期日期、剩余天数

- [x] 8. 公开报价查询（免登录）
  - [x] 8.1 配置安全放行和创建公开询价 Controller
    - 在 SecurityConfig 中添加 `/api/v1/supplier-portal/public/**` permitAll 配置
    - 创建 `PublicInquiryController`，实现免认证的列表和详情接口
    - 列表仅返回 inviteMode=PUBLIC 且 status ∈ {OPEN, PUBLISHED} 的询价
    - _Requirements: 6.1, 6.2, 6.3_

  - [x] 8.2 实现短信验证码验证和报价提交
    - 在 `SupplierAuthService` 中实现 `verifyCode(phone, smsCode)` 方法
    - 实现 `getOrCreateSupplierByPhone(phone)` 方法（关联或创建临时供应商）
    - 在 `PublicInquiryController` 实现 `POST /{id}/quote` 接口
    - 校验询价是否已截止（超过 deadline 则拒绝）
    - _Requirements: 6.4, 6.5, 6.6, 6.7_

  - [x]* 8.3 编写属性测试 - 公开询价过滤不变量
    - **Property 11: 公开询价过滤不变量**
    - **Validates: Requirements 6.2**
    - 使用 jqwik 随机生成询价数据（不同 inviteMode 和 status），验证过滤结果正确

  - [x]* 8.4 编写属性测试 - 询价截止拦截
    - **Property 12: 询价截止拦截**
    - **Validates: Requirements 6.7**
    - 随机时间点验证截止日之后报价被拒绝

- [x] 9. Checkpoint - 确保到期提醒和公开报价通过验证
  - Ensure all tests pass, ask the user if questions arise.

- [x] 10. 退货退款关联
  - [x] 10.1 创建退款数据模型
    - 执行 SQL 创建 `biz_material_refund` 和 `biz_material_refund_detail` 表
    - 创建 `BizMaterialRefund` 实体和 `BizMaterialRefundDetail` 实体
    - 创建对应 Mapper 接口
    - _Requirements: 7.1, 7.2_

  - [x] 10.2 实现退货退款事件驱动逻辑
    - 创建 `MaterialReturnCreatedEvent` 事件类
    - 在退货出库服务（MaterialOutboundService）的创建逻辑中，当 outboundType=RETURN 且 contractId != null 时发布事件
    - 创建 `MaterialReturnRefundEventListener`，监听事件并调用 refundService.createRefundFromReturn()
    - _Requirements: 7.1, 7.6_

  - [x] 10.3 实现退款服务（含审批通过回写）
    - 创建 `MaterialRefundService`，实现 createRefundFromReturn 方法
    - 按入库单价计算退款金额：sum(detail.quantity × detail.inboundUnitPrice)
    - 自动提交 Flowable 审批流程
    - 监听 WorkflowApprovedEvent，审批通过后扣减合同累计付款金额
    - 创建 `MaterialRefundController`，实现 `GET /api/v1/material/refund`（分页+筛选）和 `GET /api/v1/material/refund/{id}`（详情）
    - _Requirements: 7.2, 7.3, 7.4, 7.5_

  - [x]* 10.4 编写属性测试 - 退款申请条件触发与完整性
    - **Property 13: 退款申请条件触发与完整性**
    - **Validates: Requirements 7.1, 7.2, 7.6**
    - 随机出库单（有/无合同关联），验证退款申请生成条件

  - [x]* 10.5 编写属性测试 - 退款后合同金额扣减
    - **Property 14: 退款后合同金额扣减**
    - **Validates: Requirements 7.4**
    - 随机退款金额和合同原有金额，验证扣减逻辑正确

  - [x]* 10.6 编写属性测试 - 退款筛选正确性
    - **Property 15: 退款记录筛选正确性**
    - **Validates: Requirements 7.5**
    - 使用 jqwik 随机生成筛选条件（合同ID、时间范围），验证返回的每条记录满足所有筛选条件

- [x] 11. 档案补全（其它合同 + 办公用品）
  - [x] 11.1 创建办公用品库存表（如不存在）和实体类
    - 执行 SQL 创建 `biz_office_supply` 表（IF NOT EXISTS）
    - 创建 `BizOfficeSupply` 实体和对应 Mapper
    - 创建 `OtherContractArchiveVO` 和 `OfficeSupplyArchiveVO` 视图对象
    - _Requirements: 8.4, 8.5_

  - [x] 11.2 扩展 ArchiveService 和 ArchiveController
    - 在 `ArchiveService` 中新增 `pageOtherContractArchive(String type, int page, int size, String keyword)` 方法
    - 在 `ArchiveService` 中新增 `pageOfficeSupplyArchive(int page, int size, String keyword)` 方法
    - 查询支持分页和关键字搜索（合同编号/名称/用品名称模糊匹配）
    - 在 `ArchiveController` 中新增三个 GET 接口：
      - `/v1/archive/other-income-contract`
      - `/v1/archive/other-expense-contract`
      - `/v1/archive/office-supply`
    - _Requirements: 8.1, 8.2, 8.3, 8.6_

  - [x]* 11.3 编写属性测试 - 档案接口搜索过滤正确性
    - **Property 16: 档案接口搜索过滤正确性**
    - **Validates: Requirements 8.6**
    - 随机关键字和数据集，验证返回记录都包含搜索关键字

- [x] 12. Final Checkpoint - 全部功能验证
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation
- Property tests validate universal correctness properties (using jqwik framework)
- Unit tests validate specific examples and edge cases
- 本批功能完全复用现有系统架构（Spring Boot + MyBatis-Plus + Flowable + Redis）
- 所有接口遵循已有的 `/api/v1/` 路径规范和 `R<T>` 统一响应格式

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1", "2.1", "7.0", "7.1", "10.1", "11.1"] },
    { "id": 1, "tasks": ["1.2", "2.2", "4.1", "5.1", "7.2", "10.2"] },
    { "id": 2, "tasks": ["1.3", "1.4", "1.5", "1.6", "2.3", "4.2", "5.2", "7.3", "8.1", "10.3", "11.2"] },
    { "id": 3, "tasks": ["2.4", "4.3", "4.4", "5.3", "7.4", "7.5", "7.6", "8.2", "10.4", "10.5", "10.6", "11.3"] },
    { "id": 4, "tasks": ["8.3", "8.4"] }
  ]
}
```
