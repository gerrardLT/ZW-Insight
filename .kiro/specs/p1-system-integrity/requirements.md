# Requirements Document

## Introduction

基于 ZW-Insight 工程项目管理系统功能表对比差异分析，本需求文档覆盖 P1 优先级的 8 个系统完整度增强功能。这些功能旨在提升系统的权限管控、数据安全、业务统计与运维能力，包括：数据权限隔离、薪资统计、项目成员管理、机械工作量结算、系统设置、引用校验、SaaS 多租户管理、审批数据回滚。

技术栈：Spring Boot 3.2 单体应用 + MyBatis-Plus + Flowable 7.0 + Vue 3 + Element Plus + uni-app + MySQL + Redis 7 + MinIO。现有基础：BaseEntity 含 tenantId 字段、已有 RBAC 角色权限体系、乐观锁并发控制。

## Glossary

- **Data_Permission_Service**：数据权限服务，负责根据用户角色和项目归属动态过滤数据查询范围
- **Salary_Statistics_Service**：薪资统计服务，按班组和个人维度汇总工资并生成月度薪资报表
- **Project_Member_Service**：项目成员管理服务，维护项目团队配置并控制项目范围内的操作权限
- **Machine_Settlement_Service**：机械工作量结算服务，按项目汇总机械台班和费用并生成结算单
- **System_Config_Service**：系统设置服务，提供系统参数的统一配置管理
- **Reference_Check_Service**：引用校验服务，在删除基础数据前检查业务数据引用关系
- **Tenant_Management_Service**：多租户管理服务，提供租户的创建、停用、续期和功能权限配置
- **Approval_Rollback_Service**：审批数据回滚服务，在审批驳回或撤回时自动回滚关联的业务数据变更
- **PC_Frontend**：Vue 3 + TypeScript 构建的 PC 端管理界面
- **Mobile_App**：基于 uni-app 构建的移动端应用
- **Workflow_Engine**：基于 Flowable 7.0 的审批流程引擎
- **RBAC_System**：基于角色的访问控制系统，管理用户角色和功能权限
- **Message_Service**：消息通知服务，支持站内信推送
- **Data_Scope**：数据范围，定义用户可访问数据的边界（全部数据/本部门及下级/本部门/本项目/仅本人）

---

## Requirements

### Requirement 1: 数据权限隔离（多项目/多部门）

**User Story:** 作为系统管理员，我需要为不同角色配置数据权限范围，以便项目经理仅能查看本项目数据、部门领导查看本部门数据、公司管理层查看全部数据，实现多项目多部门的数据隔离。

#### Acceptance Criteria

1. THE Data_Permission_Service SHALL 支持五种数据范围级别：全部数据（ALL）、本部门及下级部门（DEPT_AND_CHILDREN）、本部门（DEPT）、本项目（PROJECT）、仅本人（SELF）
2. WHEN 系统管理员在角色管理页面配置数据范围时，THE PC_Frontend SHALL 提供数据权限配置界面，允许管理员为每个角色选择一种数据范围级别
3. WHEN 用户发起业务数据查询请求时，THE Data_Permission_Service SHALL 根据当前用户所属角色的数据范围配置，通过 MyBatis-Plus 拦截器自动在 SQL 查询中追加数据过滤条件
4. WHILE 用户角色的数据范围为 PROJECT 时，THE Data_Permission_Service SHALL 仅返回该用户作为项目成员参与的项目的业务数据
5. WHILE 用户角色的数据范围为 DEPT 时，THE Data_Permission_Service SHALL 仅返回该用户所属部门创建的业务数据
6. WHILE 用户角色的数据范围为 DEPT_AND_CHILDREN 时，THE Data_Permission_Service SHALL 返回该用户所属部门及其所有下级部门创建的业务数据
7. WHILE 用户角色的数据范围为 SELF 时，THE Data_Permission_Service SHALL 仅返回该用户本人创建的业务数据
8. WHILE 用户角色的数据范围为 ALL 时，THE Data_Permission_Service SHALL 返回当前租户下的全部业务数据
9. IF 用户同时拥有多个角色且各角色配置了不同的数据范围，THEN THE Data_Permission_Service SHALL 取最大数据范围作为该用户的有效数据范围（范围优先级：ALL > DEPT_AND_CHILDREN > DEPT > PROJECT > SELF）
10. WHEN 数据权限配置变更后，THE Data_Permission_Service SHALL 立即对该角色关联的所有用户生效（查询时实时读取配置，不使用缓存）

#### 数据模型要点

- 角色表增加字段：`data_scope`（枚举：ALL / DEPT_AND_CHILDREN / DEPT / PROJECT / SELF）
- 用户-项目关联表：`sys_user_project`（user_id, project_id）
- MyBatis-Plus DataPermissionInterceptor 实现 SQL 自动拼接

---

### Requirement 2: 薪资统计（劳务）

**User Story:** 作为项目财务人员，我需要按班组和个人维度汇总劳务薪资数据，以便生成月度薪资报表并关联考勤和用工单信息，掌握项目人工成本支出情况。

#### Acceptance Criteria

1. WHEN 用户选择项目和月份后请求薪资统计数据时，THE Salary_Statistics_Service SHALL 汇总该项目该月份所有已审批通过的工资单（LaborPayroll）数据，按班组维度分组计算每个班组的应发工资总额、实发工资总额和人数
2. WHEN 用户查看班组薪资明细时，THE Salary_Statistics_Service SHALL 展示该班组下每位工人的姓名、身份证号后四位、出勤天数、加班工时、应发金额、扣款金额、实发金额
3. THE Salary_Statistics_Service SHALL 从已审批通过的用工单（WorkOrder）中提取出勤天数和加班工时数据，与工资单金额进行关联展示
4. WHEN 用户请求生成月度薪资报表时，THE Salary_Statistics_Service SHALL 生成包含以下汇总信息的报表：项目名称、统计月份、班组列表（每班组含人数、应发总额、实发总额）、项目薪资合计（全部班组应发总额之和、实发总额之和），金额精确到小数点后 2 位
5. THE Salary_Statistics_Service SHALL 支持按以下条件筛选薪资统计数据：项目（必选）、月份（必选）、班组名称（可选）、工人姓名（可选）
6. THE Salary_Statistics_Service SHALL 支持将月度薪资报表导出为 Excel 格式（.xlsx），导出内容包含汇总表和班组明细表两个工作表
7. IF 查询月份不存在已审批通过的工资单数据，THEN THE Salary_Statistics_Service SHALL 返回空结果集并在页面展示提示信息"该月份暂无已审批的薪资数据"
8. WHEN 薪资统计页面加载时，THE Salary_Statistics_Service SHALL 同时展示同比数据（与上一年同月对比）和环比数据（与上一月对比），计算薪资变化率（变化率 =（本期 - 上期）÷ 上期 × 100%，精确到小数点后 1 位）
9. THE Salary_Statistics_Service SHALL 区分自有劳务和零星用工两种类型，分别统计并在报表中独立展示

---

### Requirement 3: 项目成员管理

**User Story:** 作为项目经理，我需要维护项目团队的成员配置，为团队成员分配项目角色（施工员/安全员/财务等），以便控制项目范围内的操作权限并明确各成员职责。

#### Acceptance Criteria

1. THE Project_Member_Service SHALL 提供项目成员的增加、移除和角色变更接口，每个项目成员须关联一个系统用户和至少一个项目角色
2. THE Project_Member_Service SHALL 支持以下项目角色：项目经理（PROJECT_MANAGER）、施工员（CONSTRUCTOR）、安全员（SAFETY_OFFICER）、质量员（QUALITY_OFFICER）、材料员（MATERIAL_OFFICER）、财务人员（FINANCE_OFFICER）、资料员（ARCHIVIST）
3. WHEN 项目经理在项目成员管理页面添加成员时，THE PC_Frontend SHALL 提供用户选择器（从系统用户列表中选择）和项目角色多选组件，同一用户在同一项目中可拥有多个角色
4. IF 用户尝试将同一系统用户重复添加到同一项目中，THEN THE Project_Member_Service SHALL 拒绝操作并返回提示信息"该用户已是本项目成员"
5. WHEN 项目成员被移除时，THE Project_Member_Service SHALL 检查该成员是否为项目中唯一的项目经理角色持有者，IF 该成员为唯一项目经理，THEN THE Project_Member_Service SHALL 拒绝移除操作并返回提示信息"项目至少需要保留一名项目经理"
6. THE Project_Member_Service SHALL 确保每个项目至少配置一名项目经理角色成员，创建项目时自动将项目创建人添加为项目经理
7. WHEN 项目成员配置变更后，THE Data_Permission_Service SHALL 根据最新的项目成员关系进行数据权限过滤（数据范围为 PROJECT 的用户仅能查看其作为成员参与的项目数据）
8. THE PC_Frontend SHALL 在项目详情页面提供"项目团队"标签页，以列表形式展示全部项目成员（姓名、部门、项目角色、加入时间），支持按角色筛选
9. WHEN 系统用户被停用或离职时，THE Project_Member_Service SHALL 自动将该用户从所有项目成员列表中标记为"已失效"状态，该用户的数据权限同步失效

#### 数据模型要点

- 项目成员表：`biz_project_member`（id, project_id, user_id, project_roles, join_date, status）
- project_roles 字段存储角色列表（JSON 数组格式）

---

### Requirement 4: 工作量结算（机械按项目汇总）

**User Story:** 作为项目经理，我需要按项目维度汇总机械台班和工作量数据并生成机械费用结算单，以便核算项目机械使用成本并作为付款依据。

#### Acceptance Criteria

1. WHEN 用户选择项目和结算周期后发起机械工作量结算时，THE Machine_Settlement_Service SHALL 汇总该项目在结算周期内所有已确认的机械工作量记录（MachineWorkLog），按机械台账维度分组统计每台机械的总台班数、总工作量和费用小计
2. THE Machine_Settlement_Service SHALL 根据机械合同约定的计价方式计算费用：台班计价时，费用 = 台班数 × 台班单价；工作量计价时，费用 = 工作量 × 工作量单价。金额精确到小数点后 2 位
3. THE Machine_Settlement_Service SHALL 生成机械工作量结算单，结算单包含：项目名称、结算周期（起止日期）、机械明细列表（机械名称、规格型号、台班数、工作量、单价、费用小计）、合计费用
4. WHEN 结算单创建完成后，THE Machine_Settlement_Service SHALL 支持提交审批，审批流程由 Workflow_Engine 驱动
5. WHEN 结算单审批通过后，THE Machine_Settlement_Service SHALL 将结算单状态更新为"已审批"，并将结算金额累加至对应机械合同的已结算金额字段
6. IF 用户选择的结算周期与该项目已有结算单的周期存在重叠（起止日期交叉），THEN THE Machine_Settlement_Service SHALL 拒绝创建并返回提示信息，指明与已有结算单的周期冲突
7. IF 结算周期内不存在已确认的机械工作量记录，THEN THE Machine_Settlement_Service SHALL 拒绝创建结算单并返回提示信息"该周期内无可结算的工作量记录"
8. THE Machine_Settlement_Service SHALL 支持结算单导出为 Excel 格式（.xlsx），包含结算汇总和各机械明细
9. WHEN 用户查看项目机械费用总览时，THE Machine_Settlement_Service SHALL 展示该项目全部结算单的累计费用、已付款金额、未付款金额

#### 数据模型要点

- 机械工作量结算单：`biz_machine_work_settlement`（id, project_id, period_start, period_end, total_amount, status, workflow_instance_id）
- 结算明细表：`biz_machine_work_settlement_detail`（id, settlement_id, ledger_id, work_log_ids, shift_count, work_volume, unit_price, subtotal）

---

### Requirement 5: 系统设置页面

**User Story:** 作为系统管理员，我需要通过统一的系统设置页面配置系统运行参数（审批超时时间、密码策略、文件上传限制等），以便灵活调整系统行为而无需修改代码。

#### Acceptance Criteria

1. THE System_Config_Service SHALL 提供系统参数的读取和更新接口，每个参数包含：参数键（唯一标识）、参数值、参数名称、参数分组、值类型（STRING/NUMBER/BOOLEAN/JSON）、默认值、值范围描述
2. THE PC_Frontend SHALL 提供系统设置页面，按参数分组（安全设置、审批设置、文件设置、通知设置）以标签页形式展示配置项，每个配置项根据值类型渲染对应的输入控件（文本框/数字输入/开关/JSON 编辑器）
3. THE System_Config_Service SHALL 支持以下安全设置参数：密码最小长度（默认 8，范围 6-20）、密码复杂度要求（默认需包含大小写字母和数字）、登录失败锁定次数（默认 5，范围 3-10）、账户锁定时长（默认 30 分钟，范围 5-1440 分钟）、验证码开关（默认开启）
4. THE System_Config_Service SHALL 支持以下审批设置参数：审批超时提醒时间（默认 24 小时，范围 1-168 小时）、审批自动催办间隔（默认 48 小时，范围 24-720 小时）、审批超时自动转办开关（默认关闭）
5. THE System_Config_Service SHALL 支持以下文件设置参数：单文件上传大小限制（默认 20MB，范围 1-100MB）、允许上传的文件类型（默认 .doc,.docx,.xls,.xlsx,.pdf,.jpg,.png,.zip）、附件总存储上限（默认 10GB，范围 1-100GB）
6. THE System_Config_Service SHALL 支持以下通知设置参数：站内信保留天数（默认 90 天，范围 30-365 天）、短信通知开关（默认关闭）、企业微信通知开关（默认关闭）
7. WHEN 管理员修改参数值并保存时，THE System_Config_Service SHALL 校验参数值是否在允许范围内，IF 参数值超出允许范围，THEN THE System_Config_Service SHALL 拒绝保存并返回校验失败信息，指明参数名称和允许的值范围
8. WHEN 系统参数更新成功后，THE System_Config_Service SHALL 清除该参数的 Redis 缓存，确保后续业务读取到最新配置值
9. THE System_Config_Service SHALL 提供"恢复默认值"操作，将选中参数恢复为系统预设的默认值
10. THE System_Config_Service SHALL 记录每次参数变更的操作日志，包含修改人、修改时间、参数键、修改前值和修改后值

#### 数据模型要点

- 系统参数表：`sys_config`（id, config_key, config_value, config_name, config_group, value_type, default_value, value_range, remark）
- Redis 缓存 key 格式：`sys:config:{config_key}`

---

### Requirement 6: 引用校验（证件/班组/台账删除时）

**User Story:** 作为系统用户，我需要在删除基础数据时系统自动检查该数据是否被业务数据引用，以避免误删导致业务数据出现引用断链问题。

#### Acceptance Criteria

1. WHEN 用户请求删除人员证件记录时，THE Reference_Check_Service SHALL 检查该证件是否被投标报名（BizTenderRegister）或投标任务（BizTenderTask）引用，IF 存在引用，THEN THE Reference_Check_Service SHALL 拒绝删除并返回引用信息列表（引用类型、引用单据编号、引用时间，最多返回前 10 条）
2. WHEN 用户请求删除班组记录时，THE Reference_Check_Service SHALL 检查该班组是否被劳务花名册（LaborRoster）、用工单（WorkOrder）或工资单（LaborPayroll）引用，IF 存在引用，THEN THE Reference_Check_Service SHALL 拒绝删除并返回引用信息列表
3. WHEN 用户请求删除机械台账记录时，THE Reference_Check_Service SHALL 检查该机械是否存在状态为"在场"的进出场记录（MachineEntry），或被机械工作量记录（MachineWorkLog）、机械合同（MachineContract）引用，IF 存在引用，THEN THE Reference_Check_Service SHALL 拒绝删除并返回引用信息列表
4. WHEN 用户请求删除公司证件记录时，THE Reference_Check_Service SHALL 检查该证件是否被投标报名引用，IF 存在引用，THEN THE Reference_Check_Service SHALL 拒绝删除并返回引用信息列表
5. WHEN 用户请求删除供应商记录时，THE Reference_Check_Service SHALL 检查该供应商是否被采购合同（PurchaseContract）、材料入库单（Inbound）、询价记录（Inquiry）引用，IF 存在引用，THEN THE Reference_Check_Service SHALL 拒绝删除并返回引用信息列表
6. WHEN 用户请求删除材料字典记录时，THE Reference_Check_Service SHALL 检查该材料是否被采购合同明细（PurchaseContractDetail）、入库明细或库存记录引用，IF 存在引用，THEN THE Reference_Check_Service SHALL 拒绝删除并返回引用信息列表
7. THE Reference_Check_Service SHALL 提供统一的引用校验注解（@ReferenceCheck），通过注解配置引用关系（被引用实体、引用字段、引用实体名称），在 Service 层 delete 方法执行前自动进行校验
8. THE PC_Frontend SHALL 在删除确认弹窗中展示引用校验结果，当存在引用时展示引用详情列表并禁用确认删除按钮
9. IF Reference_Check_Service 校验过程中发生数据库查询异常，THEN THE Reference_Check_Service SHALL 阻止删除操作（安全优先），记录异常日志并返回提示信息"引用校验异常，请稍后重试"

#### 技术实现要点

- 注解驱动：自定义 `@ReferenceCheck` 注解 + AOP 切面拦截
- 引用关系配置化：注解属性指定引用表名、引用字段、显示名称
- 性能：引用校验查询仅 COUNT 判断是否存在，不加载全量数据

---

### Requirement 7: SaaS 多租户管理（用户类型/续期）

**User Story:** 作为平台运营人员，我需要在管理后台对租户进行创建、停用、续期操作，并配置每个租户的用户数上限和功能模块权限，以便管理平台的 SaaS 多租户服务。

#### Acceptance Criteria

1. THE Tenant_Management_Service SHALL 提供租户的创建、查询、编辑、停用和启用接口，每个租户包含：租户名称、联系人、联系电话、用户类型（试用/标准/企业）、有效期起止日期、用户数上限、状态（正常/已停用/已过期）
2. WHEN 平台运营人员创建新租户时，THE Tenant_Management_Service SHALL 自动生成唯一的租户编码，初始化租户管理员账号（用户名为联系人手机号），并根据用户类型设置默认有效期（试用：30 天，标准：365 天，企业：自定义）
3. THE Tenant_Management_Service SHALL 为每个租户配置功能模块权限，可选模块包括：投标管理、预算管理、采购管理、劳务管理、材料管理、机械管理、分包管理、现场管理、财务管理、行政人事、三方比价、看板分析，未授权模块的菜单和 API 对该租户用户不可见且不可访问
4. WHEN 平台运营人员执行租户续期操作时，THE Tenant_Management_Service SHALL 将租户有效期延长指定天数（续期天数须为正整数且不超过 1095 天），续期从当前有效期结束日期开始累加
5. WHEN 租户有效期到期时，THE Tenant_Management_Service SHALL 自动将租户状态更新为"已过期"，该租户下所有用户的登录请求被拒绝，返回提示信息"租户服务已到期，请联系管理员续期"
6. WHEN 平台运营人员停用租户时，THE Tenant_Management_Service SHALL 将租户状态更新为"已停用"，该租户下所有在线用户立即失效（清除 Redis 中的 Token），后续登录请求被拒绝
7. IF 租户下的活跃用户数已达到用户数上限，THEN THE Tenant_Management_Service SHALL 拒绝该租户新增用户操作并返回提示信息"用户数已达上限（当前 N/M），请联系管理员扩容"
8. THE Tenant_Management_Service SHALL 在租户到期前 15 天和 7 天分别通过 Message_Service 向平台运营人员发送续期提醒通知
9. THE PC_Frontend SHALL 提供租户管理列表页面，支持按租户名称、状态、用户类型筛选，展示每个租户的基本信息、有效期、用户使用量（当前活跃用户数/上限）
10. THE Tenant_Management_Service SHALL 确保不同租户之间的数据完全隔离，所有业务数据查询自动追加 tenant_id 过滤条件（基于 BaseEntity 中已有的 tenantId 字段和 MyBatis-Plus TenantLineInnerInterceptor 实现）

#### 数据模型要点

- 租户表：`sys_tenant`（id, tenant_code, tenant_name, contact_name, contact_phone, user_type, start_date, end_date, max_users, modules, status）
- modules 字段存储已授权模块列表（JSON 数组）
- 租户拦截器：MyBatis-Plus TenantLineInnerInterceptor 自动追加 tenant_id

---

### Requirement 8: 审批数据回滚

**User Story:** 作为业务操作人员，我需要在审批驳回或撤回时系统自动回滚已生效的业务数据变更（如合同状态、预算金额等），以便确保业务数据与审批状态保持一致。

#### Acceptance Criteria

1. WHEN 审批流程被驳回时，THE Approval_Rollback_Service SHALL 根据业务类型自动回滚该审批单据关联的业务数据变更，将数据恢复到审批提交前的状态
2. WHEN 审批流程被发起人撤回时，THE Approval_Rollback_Service SHALL 根据业务类型自动回滚该审批单据关联的业务数据变更，将数据恢复到审批提交前的状态
3. THE Approval_Rollback_Service SHALL 支持以下业务类型的数据回滚：目标成本变更（回滚预算科目金额和项目总预算）、施工合同变更签证（回滚合同累计变更金额）、劳务产值上报（回滚累计产值）、材料采购结算（回滚已结算金额）、机械合同结算（回滚已结算金额）、分包结算（回滚已结算金额）
4. THE Approval_Rollback_Service SHALL 在审批提交时记录业务数据快照（变更前数据），包含：业务类型、业务单据ID、快照字段名、快照字段值（JSON 格式），作为回滚依据
5. WHEN 执行数据回滚时，THE Approval_Rollback_Service SHALL 使用数据库事务确保回滚操作的原子性，回滚涉及的所有数据变更在同一事务中完成
6. WHEN 执行数据回滚时，THE Approval_Rollback_Service SHALL 使用乐观锁（BaseEntity 的 version 字段）防止并发冲突，IF 乐观锁冲突导致回滚失败，THEN THE Approval_Rollback_Service SHALL 自动重试回滚操作（最多 3 次），3 次仍失败则标记为"回滚失败"并通过 Message_Service 通知系统管理员
7. WHEN 数据回滚成功后，THE Approval_Rollback_Service SHALL 将审批单据状态更新为"已驳回"或"已撤回"，并记录回滚操作日志（回滚时间、回滚字段、回滚前值、回滚后值）
8. IF 审批单据对应的业务数据已被后续操作修改（快照值与当前值不一致），THEN THE Approval_Rollback_Service SHALL 标记该回滚为"冲突待确认"状态，通过 Message_Service 通知业务操作人员和系统管理员手动处理
9. THE Approval_Rollback_Service SHALL 提供回滚记录查询接口，支持按项目、业务类型、时间范围查询全部回滚操作记录及其执行结果（成功/失败/冲突待确认）
10. THE PC_Frontend SHALL 在审批详情页面展示回滚状态信息，包括回滚是否成功、回滚的字段和值变化，以及冲突时的处理指引

#### 数据模型要点

- 数据快照表：`biz_approval_snapshot`（id, workflow_instance_id, biz_type, biz_id, field_name, original_value, created_at）
- 回滚记录表：`biz_approval_rollback_log`（id, workflow_instance_id, biz_type, biz_id, rollback_fields, rollback_status, retry_count, error_msg, created_at）

---

## 依赖关系说明

| 功能 | 上游依赖 | 下游影响 |
|------|---------|---------|
| 数据权限隔离 | RBAC_System、项目成员管理 | 全部业务数据查询 |
| 薪资统计 | 劳务模块（工资单/用工单） | 报表导出 |
| 项目成员管理 | 系统用户管理 | 数据权限隔离 |
| 工作量结算（机械） | 机械工作量记录、机械合同 | 付款申请 |
| 系统设置页面 | 无 | 安全策略/审批/文件上传等全局行为 |
| 引用校验 | 全部基础数据模块 | 删除操作安全保障 |
| SaaS 多租户管理 | BaseEntity.tenantId | 全部数据隔离 |
| 审批数据回滚 | Workflow_Engine、全部审批业务 | 业务数据一致性 |

## 非功能性约束

1. THE Data_Permission_Service SHALL 确保数据权限过滤对查询性能的影响不超过 50ms（95 分位），通过索引优化和 SQL 拼接方式实现
2. THE Salary_Statistics_Service SHALL 在 5 秒内完成单项目单月份的薪资统计汇总计算
3. THE Reference_Check_Service SHALL 在 500ms 内完成单条记录的引用校验
4. THE Tenant_Management_Service SHALL 确保租户停用操作在 3 秒内完成（包括 Token 清除）
5. THE Approval_Rollback_Service SHALL 确保数据回滚操作在 5 秒内完成，超时则标记为失败并触发重试
6. THE System_Config_Service SHALL 通过 Redis 缓存配置参数，读取延迟不超过 10ms，缓存过期时间为 1 小时
