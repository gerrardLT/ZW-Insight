# Implementation Plan: P0 核心功能

## Overview

基于 ZW-Insight 工程项目管理系统 P0 优先级 7 个核心缺失功能的实现计划。实现采用 Spring Boot 3.2 后端 + Vue 3 前端 + uni-app 移动端的技术栈，按功能模块分组，每个模块内按"数据层 → 服务层 → 控制层 → 前端"的顺序递进实现。

## Tasks

- [x] 1. 数据库迁移脚本与基础设施准备
  - [x] 1.1 创建全部新增表的数据库迁移脚本
    - 在 `zw-insight-server/sql/` 目录下创建迁移 SQL 文件
    - 包含表：`biz_boq_item`、`biz_budget_change`、`biz_budget_change_detail`、`biz_project_settlement`、`biz_settlement_contract_detail`、`sys_budget_control_config`、`biz_retention_warning_log`、`biz_inspection_detail`
    - 包含 `biz_inspection` 表的 ALTER 语句（增加 `scheme_snapshot` 字段）
    - 包含 `sys_budget_control_config` 系统默认记录的 INSERT（BLOCK, 80%）
    - _Requirements: 1, 2, 3, 5, 6, 7_

  - [x] 1.2 创建各模块的 MyBatis-Plus Entity 和 Mapper 接口
    - `zw-contract` 模块：`BoqItem` entity + `BoqItemMapper`
    - `zw-budget` 模块：`BudgetChange`、`BudgetChangeDetail` entity + Mapper；`BudgetControlConfig` entity + Mapper
    - `zw-finance` 模块：`ProjectSettlement`、`SettlementContractDetail` entity + Mapper；`RetentionWarningLog` entity + Mapper
    - `zw-site` 模块：`InspectionDetail` entity + Mapper
    - 所有 Entity 须包含 `@TableLogic` 逻辑删除字段和 `tenant_id`
    - _Requirements: 1, 2, 3, 5, 6, 7_

- [ ] 2. 工程量清单上传（BOQ）— 后端 zw-contract
  - [x] 2.1 实现 BoqService 核心逻辑
    - 创建 `BoqService` 类，实现 `uploadBoq(Long contractId, MultipartFile file)` 方法
    - 实现合同状态校验（仅 EFFECTIVE/CHANGING 允许）
    - 实现产值上报引用检查（有引用则拒绝覆盖）
    - 实现文件大小校验（≤20MB）
    - 调用 MinIO FileStorageService 存储原始文件
    - _Requirements: 1.1, 1.5, 1.6, 1.9_

  - [x] 2.2 实现 EasyExcel 解析与层级构建
    - 创建 `BoqExcelRow` DTO（EasyExcel @ExcelProperty 注解映射列）
    - 创建 `BoqReadListener` 实现行校验（必填字段、条目上限 5000、错误最多 100 条）
    - 实现 `buildHierarchy` 方法，按项目编码的 "." 分隔规则构建父子层级（最多 4 级）
    - 实现批量插入（删旧+插新）和合计金额计算回写合同
    - _Requirements: 1.2, 1.3, 1.4, 1.8_

  - [ ]* 2.3 编写属性测试：BOQ 层级一致性（Property P1）
    - **Property P1: BOQ 层级一致性**
    - 生成随机合法编码列表，验证 buildHierarchy 输出中所有 level > 1 的条目其 parent_id 指向存在且 parent.level == current.level - 1
    - **Validates: Requirements 1.4**

  - [~] 2.4 实现 BoqController REST 接口
    - POST `/api/v1/contracts/{contractId}/boq/upload` — 上传并解析 BOQ
    - GET `/api/v1/contracts/{contractId}/boq` — 查询清单树形结构
    - GET `/api/v1/contracts/{contractId}/boq/flat` — 查询清单平铺列表（供产值上报使用）
    - DELETE `/api/v1/contracts/{contractId}/boq` — 清除清单数据
    - _Requirements: 1.1, 1.7_

  - [ ]* 2.5 编写 BoqService 单元测试
    - 测试状态校验拒绝逻辑
    - 测试引用检查拒绝覆盖
    - 测试解析错误返回行号
    - 测试合计金额计算精度
    - _Requirements: 1.1, 1.2, 1.3, 1.5, 1.8, 1.9_

- [ ] 3. 目标成本变更 — 后端 zw-budget
  - [x] 3.1 实现 BudgetChangeService 核心逻辑
    - 创建 `BudgetChangeService`，实现 CRUD 操作
    - 实现 `validateBeforeSubmit` 预算余额校验（调减时：调整后金额 ≥ 已占用预算）
    - 实现 `calculateOccupiedBudget`（已签合同金额 + 已付无合同费用）
    - 创建 `BudgetChangeDTO` 和 `BudgetChangeDetailDTO` 请求对象
    - _Requirements: 2.1, 2.2, 2.3_

  - [x] 3.2 实现审批流程集成与回调
    - 实现 `submit(Long changeId)` 提交审批，调用 Flowable ApprovalService 启动流程
    - 实现 `onApproved(Long changeId)` 审批通过回调：逐科目回写预算明细 + 更新项目预算总额
    - 实现 `onRejected(Long changeId)` 审批驳回回调：更新状态为 REJECTED
    - 实现 `withdraw(Long changeId)` 撤回操作：更新状态为 WITHDRAWN
    - 注册 Flowable 审批回调监听器
    - _Requirements: 2.4, 2.5, 2.6, 2.7, 2.8_

  - [ ]* 3.3 编写属性测试：预算变更金额守恒（Property P2）
    - **Property P2: 预算变更金额守恒**
    - 生成随机变更明细列表，验证 SUM(details.adjustAmount) == change.totalAdjustAmount 且审批通过后各明细回写累加总和等于变更单总调整额
    - **Validates: Requirements 2.2, 2.5**

  - [~] 3.4 实现 BudgetChangeController REST 接口
    - 完整 CRUD + submit + withdraw 接口
    - 变更轨迹查询接口（按项目查询全部变更记录及审批结果）
    - _Requirements: 2.1, 2.9_

  - [ ]* 3.5 编写 BudgetChangeService 单元测试
    - 测试调减时余额不足拒绝
    - 测试审批通过后金额回写正确性
    - 测试撤回/驳回不修改原预算
    - _Requirements: 2.3, 2.5, 2.6, 2.7, 2.8_

- [~] 4. Checkpoint — 确保合同与预算模块编译通过
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 5. 项目最终结算 — 后端 zw-finance
  - [~] 5.1 实现 ProjectSettlementService 数据汇总逻辑
    - 创建 `ProjectSettlementService`
    - 实现项目状态校验（仅 COMPLETED 允许）和重复结算单校验
    - 实现收入汇总：施工合同总额、累计产值、累计收款、累计开票
    - 实现支出汇总：分包/劳务/材料/机械结算总额 + 累计付款
    - 实现利润计算（精确到分）和利润率计算（精确到小数点后 2 位）
    - 生成关联合同明细并标注未结清合同
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.7_

  - [~] 5.2 实现结算审批流程与项目状态流转
    - 实现 `submit` 提交审批，启动 Flowable 结算审批流程
    - 实现 `onApproved` 回调：更新结算单状态 + 项目状态更新为 CLOSED
    - 实现 `onRejected` 回调：结算单状态更新为 REJECTED，项目状态不变
    - _Requirements: 3.5, 3.6_

  - [ ]* 5.3 编写属性测试：结算利润计算正确性（Property P3）
    - **Property P3: 结算利润计算正确性**
    - 生成随机收支数据，验证 profit == totalIncome - totalExpenditure 且 profitRate == profit / totalIncome * 100（totalIncome > 0 时）
    - **Validates: Requirements 3.4**

  - [~] 5.4 实现结算报告 Excel 导出
    - 使用 EasyExcel 导出收支汇总表 + 各合同结算明细
    - 实现 ExcelWriter 多 Sheet 写入（收支汇总 Sheet + 合同明细 Sheet）
    - _Requirements: 3.8_

  - [~] 5.5 实现 ProjectSettlementController REST 接口
    - POST 创建结算单、GET 详情、PUT 编辑、POST 提交审批、POST 导出 Excel
    - GET 未结清合同列表
    - _Requirements: 3.1, 3.5, 3.7, 3.8_

  - [ ]* 5.6 编写 ProjectSettlementService 单元测试
    - 测试非竣工项目拒绝创建
    - 测试重复结算单拒绝
    - 测试审批通过后项目状态变为 CLOSED
    - _Requirements: 3.1, 3.2, 3.5, 3.6_

- [ ] 6. 验证码登录 — 后端 zw-security
  - [x] 6.1 实现 CaptchaService 图形验证码
    - 使用 Hutool CaptchaUtil 生成 4 位字母数字混合图形验证码
    - Redis 存储：key=`captcha:{uuid}`，value=code，TTL=300s
    - 实现 `generateImageCaptcha()` 返回 Base64 图片 + UUID
    - 实现 `verifyImageCaptcha(uuid, inputCode)` 大小写不敏感比对 + 校验后立即删除
    - _Requirements: 4.1, 4.2, 4.4, 4.5_

  - [x] 6.2 实现 CaptchaService 短信验证码与频率限制
    - 手机号格式校验（`^1[3-9]\\d{9}$`）
    - Redis 频率限制：`sms:freq:{phone}` TTL=60s（60 秒内仅 1 次）
    - Redis 日限额：`sms:daily:{phone}` INCR + 当天剩余秒数 EXPIRE（每日≤10 次）
    - 对接阿里云短信 SDK 2.0.24 发送 6 位数字验证码
    - _Requirements: 4.6, 4.7, 4.9, 4.10_

  - [x] 6.3 实现 IP 锁定机制
    - Redis 计数：`login:ip:fail:{ip}` INCR + EXPIRE 300s（5 分钟窗口）
    - 连续 5 次失败后设置锁定：`login:ip:lock:{ip}` TTL=900s（15 分钟）
    - 实现 `checkIpLock(clientIp)` 和 `recordIpFailure(clientIp)`
    - _Requirements: 4.8_

  - [ ]* 6.4 编写属性测试：验证码一次性使用（Property P4）
    - **Property P4: 验证码一次性使用**
    - 生成随机验证码并存入 Redis，首次校验成功后，使用相同 uuid+code 再次校验必定返回失败
    - **Validates: Requirements 4.5**

  - [~] 6.5 实现 CaptchaController 和登录流程集成
    - GET `/api/v1/captcha/image` — 生成图形验证码
    - POST `/api/v1/captcha/sms` — 发送短信验证码
    - 修改现有 `AuthController` 登录接口，增加验证码校验逻辑和 IP 锁定检查
    - 扩展 `LoginDTO` 增加 `captchaCode`、`captchaUuid`、`phone`、`smsCode`、`loginType` 字段
    - _Requirements: 4.1, 4.2, 4.3, 4.6_

  - [ ]* 6.6 编写 CaptchaService 单元测试
    - 测试验证码过期场景
    - 测试短信频率限制拒绝
    - 测试 IP 锁定触发与解除
    - _Requirements: 4.4, 4.7, 4.8, 4.10_

- [~] 7. Checkpoint — 确保安全模块与财务模块编译通过
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 8. 质保金预警定时任务 — 后端 zw-finance
  - [~] 8.1 实现 RetentionWarningTask 定时任务核心逻辑
    - 创建 `RetentionWarningTask`，使用 `@Scheduled(cron = "0 0 8 * * ?")` 每日 08:00 执行
    - 查询 status='UNRETURNED' 且到期日在未来 30 天内或已过期的质保金记录
    - 实现分级逻辑：30~8 天=UPCOMING，7~1 天=URGENT，已过期=OVERDUE
    - 逾期超 180 天标记 LONG_OVERDUE 并停止催办
    - _Requirements: 5.1, 5.2, 5.4, 5.5_

  - [~] 8.2 实现通知去重与催办机制
    - Redis Set 去重：key=`retention:warned:{retentionId}:{level}`（非逾期同级别只发一次）
    - 逾期催办频率控制：key=`retention:overdue:last:{retentionId}`（每 3 天一次）
    - 调用 MessageService 发送站内信（项目负责人 + 财务人员）
    - 通知内容含：项目名称、合同名称、质保金金额、到期日期、预警级别
    - _Requirements: 5.3, 5.5, 5.6_

  - [~] 8.3 实现失败重试与状态清除
    - 记录 `biz_retention_warning_log` 通知日志
    - 失败记录在下次任务执行时重试（最多 3 次），3 次仍失败标记 PERMANENTLY_FAILED
    - 实现 `onRetentionReturned(Long retentionId)` 质保金退还时清除去重记录
    - _Requirements: 5.7, 5.8_

  - [ ]* 8.4 编写属性测试：质保金通知去重（Property P7）
    - **Property P7: 质保金通知去重**
    - 模拟同一质保金记录连续两次执行预警任务（级别不变），验证第二次不产生新的通知发送调用
    - **Validates: Requirements 5.6**

  - [ ]* 8.5 编写 RetentionWarningTask 单元测试
    - 测试分级逻辑正确性（30天/7天/逾期 边界）
    - 测试逾期超 180 天停止催办
    - 测试重试次数上限
    - _Requirements: 5.1, 5.4, 5.5, 5.8_

- [ ] 9. 预算控制配置页面 — 后端 zw-budget
  - [x] 9.1 实现 BudgetControlConfigService CRUD
    - 创建 `BudgetControlConfigService`，实现配置的增删改查
    - 实现 `getEffectiveConfig(Long projectId)` 优先项目级配置 → 回落系统默认 → 异常时硬编码 BLOCK
    - 创建 `BudgetControlConfigDTO` 含 projectId、controlMode（WARN_ONLY/BLOCK/EXEMPT）、warningThreshold（50-99）
    - 删除项目级配置后回落为默认规则
    - _Requirements: 6.1, 6.4, 6.7, 6.8, 6.9_

  - [x] 9.2 实现预算执行率计算与拦截逻辑
    - 实现 `checkBudget(Long projectId, String costCategory, BigDecimal newAmount)` 方法
    - 计算执行率 = 已发生额 / 预算额 * 100%
    - BLOCK 模式超 100% 抛异常阻止提交；WARN_ONLY 超 100% 返回警告标识；EXEMPT 直接放行
    - 达到预警阈值时通过 MessageService 发送站内信
    - _Requirements: 6.3, 6.5, 6.6_

  - [~] 9.3 改造 BudgetControlAspect 切面
    - 替换现有硬编码"禁止提交"逻辑为配置驱动
    - 创建 `@BudgetCheck` 注解和 `BudgetControlAspect` AOP 切面
    - 实现 `BudgetCheckResult`（PASS/WARN/BLOCK）和 `BudgetWarningContext` 线程变量
    - 在业务单据提交方法上添加 `@BudgetCheck` 注解（采购合同/劳务合同/机械合同/其他付款）
    - _Requirements: 6.5, 6.7_

  - [ ]* 9.4 编写属性测试：预算控制配置单调性（Property P5）
    - **Property P5: 预算控制配置单调性**
    - 创建项目级配置后删除，验证 `getEffectiveConfig` 返回值回落为系统默认（BLOCK, 80%）
    - **Validates: Requirements 6.4, 6.9**

  - [~] 9.5 实现 BudgetControlConfigController REST 接口
    - 完整 CRUD 接口 + 按项目获取生效配置接口
    - 列表支持按项目名称筛选
    - _Requirements: 6.1, 6.2_

  - [ ]* 9.6 编写 BudgetControlConfigService 单元测试
    - 测试三种模式校验行为
    - 测试配置异常回落默认值
    - 测试预警阈值通知触发
    - _Requirements: 6.3, 6.5, 6.6, 6.8_

- [ ] 10. 检查方案关联 — 后端 zw-site
  - [~] 10.1 实现 InspectionSchemeService 方案关联逻辑
    - 创建 `InspectionSchemeService`
    - 实现 `listSchemes(String inspectionType, int page, int size)` 按类型筛选已启用方案（每页≤50）
    - 实现 `applyScheme(Long inspectionId, Long schemeId)` 关联方案并生成快照
    - 快照为 JSON 格式含 schemeId、schemeName、items（itemName + checkStandard + checkMethod）
    - 清除旧检查明细 → 填充新方案检查项 → 更新 scheme_id 和 scheme_snapshot
    - _Requirements: 7.1, 7.2, 7.4, 7.7_

  - [~] 10.2 实现检查明细编辑与手动填写
    - 允许编辑已填充的检查项（修改检查标准/删除不适用项，不可新增方案外检查项）
    - 未选择方案时允许手动填写检查项（≤100 条，项目名称≤200 字符，检查标准≤500 字符）
    - _Requirements: 7.3, 7.8_

  - [ ]* 10.3 编写属性测试：方案快照不可变性（Property P6）
    - **Property P6: 方案快照不可变性**
    - 创建检查记录并关联方案，修改原方案源数据后重新读取检查记录，验证 scheme_snapshot JSON 内容与创建时一致
    - **Validates: Requirements 7.7**

  - [~] 10.4 实现 InspectionSchemeController REST 接口
    - GET `/api/v1/inspection-schemes` — 方案列表（按 inspectionType 筛选）
    - GET `/api/v1/inspection-schemes/{id}/items` — 方案检查项列表
    - POST `/api/v1/inspections/{id}/apply-scheme` — 关联方案到检查记录
    - _Requirements: 7.1, 7.2_

  - [ ]* 10.5 编写 InspectionSchemeService 单元测试
    - 测试方案关联后检查明细正确填充
    - 测试重新选择方案清除旧数据
    - 测试手动填写限制校验
    - _Requirements: 7.2, 7.3, 7.4, 7.8_

- [~] 11. Checkpoint — 确保全部后端模块编译通过、单元测试通过
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 12. PC 前端 — 工程量清单上传页面
  - [~] 12.1 实现 BOQ 上传前端组件
    - 创建 `src/api/boq.ts` API 文件（uploadBoq、getBoqTree、deleteBoq）
    - 创建 `views/contract/boq-upload.vue` 页面组件
    - 实现 Excel 文件拖拽/点击上传（Element Plus Upload 组件，限制 .xlsx、20MB）
    - 上传成功后展示清单树形表格（ElTable + 树形展开）
    - 展示合计金额、条目数、层级数
    - _Requirements: 1.1, 1.2, 1.7_

- [ ] 13. PC 前端 — 目标成本变更页面
  - [~] 13.1 实现目标成本变更列表与表单页面
    - 创建 `src/api/budget-change.ts` API 文件
    - 创建 `views/budget/change/index.vue` 变更单列表页（分页+状态筛选）
    - 创建 `views/budget/change/form.vue` 变更单新建/编辑表单
    - 表单含：变更原因（必填）、变更明细表格（动态增行：科目名称、原金额、调整金额、调整后金额自动计算）
    - 提交/撤回操作按钮（根据状态显隐）
    - _Requirements: 2.1, 2.2, 2.9_

- [ ] 14. PC 前端 — 项目最终结算页面
  - [~] 14.1 实现项目最终结算列表与详情页面
    - 创建 `src/api/settlement.ts` API 文件
    - 创建 `views/finance/settlement/index.vue` 结算单列表页
    - 创建 `views/finance/settlement/detail.vue` 结算单详情页
    - 详情页展示：收支汇总卡片、利润/利润率、合同明细表格、未结清合同标注
    - 实现 Excel 导出按钮（Blob 下载）
    - _Requirements: 3.3, 3.4, 3.7, 3.8_

- [ ] 15. PC 前端 — 验证码登录改造
  - [~] 15.1 改造登录页增加验证码
    - 创建 `src/api/captcha.ts` API 文件
    - 修改 `views/login/index.vue`，增加图形验证码输入行（输入框 + 验证码图片，点击刷新）
    - 登录表单增加 captchaCode 和 captchaUuid 字段
    - 验证码校验失败时自动刷新图片
    - _Requirements: 4.1, 4.2, 4.3_

- [ ] 16. PC 前端 — 预算控制配置页面
  - [~] 16.1 实现预算控制配置 CRUD 页面
    - 创建 `src/api/budget-control-config.ts` API 文件
    - 创建 `views/budget/control-config/index.vue` 配置列表页（表格+筛选+弹窗表单）
    - 表单字段：项目选择器、控制模式下拉（仅提醒/禁止提交/免控）、预警阈值滑块（50-99%）
    - 列表展示：项目名称、控制模式、预警阈值、操作按钮
    - _Requirements: 6.1, 6.2, 6.3_

- [ ] 17. PC 前端 — 检查方案关联
  - [~] 17.1 改造检查表单增加方案选择功能
    - 修改 `views/site/inspection/form.vue` 检查表单页面
    - 增加方案选择下拉/弹窗组件（按检查类型筛选已启用方案）
    - 选择方案后自动填充检查明细表格
    - 支持编辑检查项（修改标准/删除，不可新增方案外项）
    - 检查详情页从 scheme_snapshot 展示方案内容
    - _Requirements: 7.1, 7.2, 7.3, 7.5_

- [ ] 18. 移动端 — 验证码登录与检查方案
  - [~] 18.1 实现移动端短信验证码登录
    - 修改 `zw-insight-app/src/pages/login/index.vue` 增加"短信验证码登录"Tab
    - 实现手机号输入 + 发送验证码按钮（60s 倒计时）+ 验证码输入
    - 调用 `/api/v1/captcha/sms` 和登录接口（loginType=SMS）
    - _Requirements: 4.6, 4.7_

  - [~] 18.2 实现移动端检查方案展示与结果标记
    - 修改移动端检查页面，展示方案快照中的检查项逐项列表
    - 每项支持标记检查结果（合格/不合格/未检查）单选
    - _Requirements: 7.6_

- [ ] 19. 路由与菜单配置
  - [~] 19.1 配置前端路由和后端菜单数据
    - PC 端 `router/index.ts` 增加新页面路由（目标成本变更、项目最终结算、预算控制配置）
    - 修改 `data-menu.sql` 插入对应菜单记录和权限标识
    - _Requirements: 2.1, 3.1, 6.2_

- [~] 20. Final Checkpoint — 全部功能集成验证
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation
- Property tests validate universal correctness properties from design document Section 10
- Unit tests validate specific examples and edge cases
- 后端实现按模块分组：zw-contract → zw-budget → zw-finance → zw-security → zw-site
- 前端实现在全部后端接口完成后进行，确保接口可联调
- Flowable 审批流程定义（BPMN XML）由现有审批框架自动生成，无需单独任务

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1", "1.2"] },
    { "id": 1, "tasks": ["2.1", "3.1", "6.1", "9.1"] },
    { "id": 2, "tasks": ["2.2", "3.2", "6.2", "6.3", "9.2"] },
    { "id": 3, "tasks": ["2.3", "2.4", "3.3", "3.4", "6.4", "6.5", "9.3"] },
    { "id": 4, "tasks": ["2.5", "3.5", "5.1", "6.6", "9.4", "9.5"] },
    { "id": 5, "tasks": ["5.2", "5.4", "8.1", "9.6", "10.1"] },
    { "id": 6, "tasks": ["5.3", "5.5", "5.6", "8.2", "8.3", "10.2", "10.4"] },
    { "id": 7, "tasks": ["8.4", "8.5", "10.3", "10.5"] },
    { "id": 8, "tasks": ["12.1", "13.1", "14.1", "15.1", "16.1", "17.1"] },
    { "id": 9, "tasks": ["18.1", "18.2", "19.1"] }
  ]
}
```
