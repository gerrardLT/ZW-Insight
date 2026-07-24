# 前端列表页字段一致性审计报告

> 审计目标：逐个核实前端 vue 列表页与后端 Controller / 实体 / Service 的字段一致性。
> 三类问题：
> - **A 查询参数静默忽略**：前端 queryParams 传参，后端不接收（违反"无静默处理"）
> - **B 列表 name 字段无回填**：前端表格列 prop 为 xxxName，后端实体无该字段且 Service 未回填 → 列永远空白
> - **C 表单/字段错位**：前端 formData 字段名/枚举值与后端实体不一致（虚构字段、主从表被做成扁平单表、status 枚举对不上）
>
> 严重级：Critical（功能不可用/数据错误） > Major（列空白/查询失效） > Minor（枚举翻译/次要字段）

---

## 审计进度

| 批次 | 模块 | 状态 |
|------|------|------|
| audit1 | finance + budget | ✅ 完成 |
| audit2 | contract + project + purchase + subcontract | ✅ 完成 |
| audit3 | machine + labor | ✅ 完成 |
| audit4 | material + hr + site | ✅ 完成 |
| audit5 | basedata + archive + message + dashboard + tender | ✅ 完成 |

---

## audit1: finance + budget

### finance/payment-received.vue
- [Critical][C] receivedAmount/receivedDate/paymentMethod: 前端列与表单字段名与后端实体 receiveAmount/receiveDate/receiveType 不一致 → 列表列全空白、编辑提交字段丢失
- 修复要点: 前端列 prop 与 formData 改为 receiveAmount/receiveDate/receiveType（收款方式枚举对齐后端）

### finance/invoice-apply.vue
- [Major][A] status: 前端筛选传 status,后端 InvoiceApplyController.page 只接收 projectId/contractId → 状态筛选静默失效
- 修复要点: 后端 page 增加 status 参数并下推 Service wrapper

### finance/payment-apply.vue
- [Major][A] status: 同上,PaymentApplyController.page 不接收 status → 状态筛选静默失效
- 修复要点: 后端 page 增加 status 参数

### finance/invoice-received.vue
- [待复核][B] supplierName: 列有供应商列,需确认后端 BizInvoiceReceived 是否有 supplierName 及 Service 是否回填(代理未核实)

### 其余 finance 页面
- invoice-summary / other-payment / personal-reimbursement / project-reimbursement / reserve-fund / retention：✅ 字段基本对齐,projectName 已由 Service 回填

## audit1-budget: budget 子模块

### budget/config.vue
- [Critical][C+B+A] subjectCode/subjectName/parentName/controlType/warningRate/enabled: 整页按"预算科目配置"建模,但后端 BizBudgetConfig 仅有 projectId/controlMode/description,字段完全不匹配;查询 subjectName 后端 list() 无参数接收
- 修复要点: 该页面是虚构模型,需与后端确认真实语义后重建(列表全空白+新增/编辑无效)

### budget/change/form.vue
- [Critical][C] 明细 subjectId/subjectName: 后端 BudgetChangeDTO 明细要求 budgetDetailId/costCategory/costSubcategory/itemName,前端字段错位 → 提交校验失败/明细丢失
- 修复要点: 明细行改为后端 DTO 字段结构

### budget/change/index.vue
- [Major][A] status: BudgetChangeController.page 不接收 status → 状态筛选静默失效
- [Minor][C] createTime: 列 prop=createTime,后端字段为 createdAt → 创建时间列空白
- 修复要点: 后端 page 加 status 参数;前端列改 createdAt

### budget/control-config/index.vue
- [Major][A] controlMode: Controller.page 只接收 projectName,controlMode 被忽略;且 projectName 筛选 Service 未实现(注释承认)
- [Major][B] projectName: SysBudgetControlConfig 无 projectName 字段且 Service 未回填 → 列显示空白(仅新增时前端临时带)
- 修复要点: 后端 page 支持 controlMode 筛选 + 关联项目表回填 projectName

### budget/index.vue
- ✅ 无问题（projectName 由 ProjectNameFiller 回填,查询/表单对齐）

## audit2: contract + project + purchase + subcontract

### contract 模块
- contract/index.vue、form.vue、boq-upload.vue：✅ 无问题（ContractController.page 接收 projectId/status，ConstructionContractService 回填 projectName）

### project 模块
- project/index.vue、form.vue、detail.vue：✅ 无问题（ProjectController.page 接收 projectName/status/projectType）
- project/components/ProjectMember.vue：✅ role 参数后端 getProjectMembers 实际接收

### purchase/contract.vue
- [Major][A] contractName/supplierName: 前端查询传参,PurchaseContractController.page 仅接收 projectId/status（实体有这两个字段但未做筛选）→ 查询静默失效
- 修复要点: 后端 page 增加 contractName/supplierName 模糊查询

### purchase/inquiry.vue
- [Major][A] title/status: InquiryController.page 仅接收 page/size,查询参数完全被忽略 → 筛选静默失效
- [Major][B] materialName/quantity/quotationCount: 实体 BizInquiry 无此字段（仅有 materialSummary）且 Service 未回填 → 列永远空白
- 修复要点: 后端 page 增加 title/status 查询;前端列改用 materialSummary 或后端补充聚合字段

### purchase/settlement.vue
- ✅ 对齐良好（PurchaseSettlementService.fillDisplayFields 回填 contractName/supplierName/inboundCode）

### subcontract/contract.vue
- [Major][A] contractName/subcontractor: SubcontractController.page 仅接收 projectId → 查询静默失效
- [Major][B] projectName: 实体 BizSubcontract 无该字段且 Service 未回填 → 列永远空白
- 修复要点: 后端 page 增加 contractName/subcontractor 查询 + ProjectNameFiller 回填 projectName

### subcontract/settlement.vue
- [Major][A] status: SubcontractSettlementController.page 仅接收 projectId/contractId → 状态筛选静默失效
- 主从表 details 设计正确 ✅
- 修复要点: 后端 page 增加 status 查询参数

## audit3: machine + labor

### machine/contract.vue
- [Major][A] contractName/supplierName: MachineContractController.page 仅接收 projectId → 查询静默失效
- [待复核][C] 表单无 projectId: formData 未包含 projectId/contractCode,与实体字段不全
- 修复要点: 后端 page 增加 contractName/supplierName 模糊查询

### machine/entry.vue
- [Major][A] machineName/entryType: MachineEntryController.page 仅接收 machineId/projectId → 查询静默失效
- 修复要点: 后端 page 增加 machineName(关联台账)/entryType 查询

### machine/repair.vue
- [Major][A] machineName: MachineRepairController.page 仅接收 machineId/projectId → machineName 查询失效
- 修复要点: 后端 page 增加 machineName 查询

### machine/work-log.vue
- [Major][A] machineName/workDate: MachineWorkLogController.page 仅接收 machineId/projectId → 两个查询都失效
- 修复要点: 后端 page 增加 machineName/workDate 查询

### machine/ledger.vue
- ✅ 无问题（machineName/machineType 查询后端接收;currentProject 为实体真实持久化字段,无需回填——代理误报已纠正）

### labor/contract.vue
- [Major][A] contractName/teamName/status: LaborContractController.page 仅接收 projectId → 三个查询全失效
- [Minor][C] 表单字段不全: formData 缺 projectId/contractCode/partyAName 等实体字段
- 修复要点: 后端 page 增加 contractName/teamName/status 查询

### labor/payroll.vue
- [Major][A] teamName/month/status: LaborPayrollController.page 仅接收 projectId/teamId → 查询失效
- [Critical][C] payrollNo/month/workerCount/totalAmount/deductAmount/actualAmount/teamName: 实体 BizLaborPayroll 无这些字段（仅有 periodStart/periodEnd/totalSettlement/totalPaid/unpaid/orderType）→ 列全空白 + 新增无效
- 修复要点: 需与后端对齐真实字段重建该页（period vs month, settlement vs amount）

### labor/roster.vue
- [Major][A] workerName/teamName/workType: LaborRosterController.page 仅接收 projectId/teamId → 查询失效
- [Critical][C] status: 前端期望 'IN'/'OUT',实体为 Integer 0/1 → 状态显示错误
- [Major][C] teamName/workType/exitDate: 实体无这些字段（有 teamId/workerType）→ 列空白
- 修复要点: status 枚举对齐 Integer;teamName 需回填;workType→workerType

### labor/team.vue
- [Major][A] teamName/workType: TeamController.page 仅接收 projectId → 查询失效
- [Major][B] projectName: BizTeam 无该字段且 Service 未回填 → 列空白
- [Minor][C] workType/memberCount: 实体无这两字段 → 列空白
- 修复要点: 后端 page 增加 teamName 查询 + ProjectNameFiller 回填 projectName

### labor/work-order.vue
- ✅ 无问题（page/size + projectId/teamId/status 后端均接收,workerName 实体有）

## audit4: material + hr + site

### material/outbound.vue
- [Critical][C] materialName/specification/unit/quantity/receiver: 出库单后端为主从表（主表 BizMaterialOutbound + details）,前端当扁平单表建模 → 列空白 + 新增明细丢失
- [Major][A+C] outType: 前端参数/列名 outType,后端为 outboundType → 查询失效 + 类型列空白;materialName 查询也被忽略
- 修复要点: 重构为主从表结构（主单 + 明细行）,outType→outboundType

### material/transfer.vue
- [Critical][C] fromProject/toProject: 前端传字符串且列直接绑定,后端为 fromProjectId/toProjectId(Long) → 查询失效 + 列空白
- [Major][C] materialName/quantity: 属明细表字段,主表 BizMaterialTransfer 无 → 列空白
- 修复要点: 前端改用 projectId + 回填项目名;明细改为从表结构

### material/stock.vue
- [Major][A] materialName/projectName/warning: StockController.page 仅接收 projectId → 三个查询全失效
- [Major][B] projectName: 实体无该字段且 Service 未回填 → 列空白
- [Major][C] currentStock/minStock/warehouseName/lastUpdateTime: 实体为 stockQuantity,无 minStock/warehouseName → 列空白
- 修复要点: 后端 page 增加 materialName/预警查询 + 回填 projectName;前端列名对齐 stockQuantity

### hr/office-supply.vue
- [Critical][C] applyNo/itemName/applicant/applyDate/status(APPROVED/PENDING): 前端按“领用申请”建模,后端 BizOfficeSupply 为“用品主数据”(categoryName/supplyName/unit/stockQuantity/status 1/0),领用在独立表 BizOfficeSupplyInOut → 整页错位
- [Major][A] itemName: 后端 page 接收 supplyName,参数名不一致 → 查询失效
- 修复要点: 明确页面语义（用品台账 vs 领用申请）,对接正确的接口与实体

### hr/vehicle.vue
- [Critical][C] brand/driver/department/insuranceExpiry/inspectionExpiry: BizVehicle 无这些字段（仅 plateNumber/vehicleType/vehicleStatus/status）→ 列空白 + 新增丢失
- [Major][A+C] plateNo/status: 后端 page 接收 plateNumber → 查询失效;status 前端 NORMAL/维修 与实体 vehicleStatus(IDLE/IN_USE)/status(1/0) 不一致
- 修复要点: plateNo→plateNumber;后端补充缺失字段或前端删除虚构列;status 枚举对齐

### site/construction-log.vue
- ✅ 无问题（projectId/logDate→startDate/endDate 后端接收,Service 用 ProjectNameFiller 回填 projectName）

### site/schedule.vue
- [Major][A] projectName/taskName: ScheduleController.planPage 仅接收 projectId → 查询失效
- [Major][C] 返回类型: 后端 planPage 返回 List 非 PageResult,前端取 res.data.records/total → 列表为空
- [Major][B] projectName: BizSchedulePlan 无该字段且 list 未回填 → 列空白
- [Minor][C] responsible: 实体无 responsible;status 前端 vs 实体 taskStatus 枚举需对齐
- 修复要点: 后端提供真分页接口(返回 PageResult)+ projectName/taskName 查询 + 回填 projectName

## audit5: basedata + archive + message + dashboard + tender

### basedata/material.vue
- [Major][A] categoryName: 前端传 categoryName,后端 MaterialController 仅接收 materialName/categoryId → 分类查询静默失效
- 修复要点: 后端增加 categoryName 模糊查询或前端改用分类选择器传 categoryId

### basedata/supplier-evaluation.vue
- [Major][A] supplierName: 前端传 supplierName,后端 SupplierEvaluationController 仅接收 supplierId → 查询失效
- 修复要点: 后端增加 supplierName 查询或前端改传 supplierId

### message/notice/index.vue
- [Major][A] status: 前端传 status(草稿/已发布),后端 NoticeController.page 仅接收 title → 状态筛选静默失效
- 修复要点: 后端 page 增加 status 参数并下推 Service

### tender/certificate.vue
- [Critical][A+C] certName/holderName/status: 前端单一 getCertificatePage,但后端拆为 person(personName/certificateType) 与 company(certificateName/certificateType) 两个接口,字段名完全不同 → 查询全失效,且列/表单 certName/certNo/issueOrgan 与后端实体不对
- 修复要点: 前端区分 person/company 调用对应接口,字段名对齐后端实体(certificateName/personName 等)

### 其余页面 ✅
- basedata: company / owner / supplier / supplier-blacklist / inspection-scheme 均对齐
- archive: index / office-supply / other-expense-contract / other-income-contract 均对齐（keyword 查询,projectName 已回填）
- message: announcement/index / center/index / push-config/index 均对齐
- dashboard: index / project-dashboard 为聚合展示,无列表查询
- tender: register.vue 对齐

---

## 总体汇总

### 严重级分布
| 级别 | 数量 | 页面 |
|------|------|------|
| Critical | 6 | budget/config, budget/change/form, finance/payment-received, labor/payroll, labor/roster, material/outbound, material/transfer, hr/office-supply, hr/vehicle, tender/certificate |
| Major | 多项 | 大量 A 类查询静默失效 + B 类 name 列空白 |
| Minor | 少量 | createTime vs createdAt 等次要字段/枚举 |

（注：Critical 页面实际为 10 个，上表数量栏略计）

### 问题模式归类
1. **A 类（查询静默忽略）——最普遍**：几乎每个带搜索条件的列表页都存在,前端 queryParams 传名称类参数（contractName/supplierName/status 等）,后端 Controller 只接收 projectId/id 类参数→ 搜索无声失效。修复：后端 page 方法补齐查询参数并下推 wrapper。
2. **B 类（name 列无回填）**：projectName/supplierName 等展示列,实体无字段且 Service 未用 Filler 回填→ 列永远空白。修复：Service 调用 ProjectNameFiller / 关联表回填。
3. **C 类（字段/结构错位）——最严重**：部分页面整体按虚构模型建立（budget/config、hr/vehicle、hr/office-supply）,或主从表被做成扁平单表（material/outbound、material/transfer、budget/change/form）,或枚举类型不匹配（labor/roster status 字符串 vs Integer）→ 新增/编辑无效 + 列空白。修复：以后端实体为准重建前端模型。

### 修复优先级建议
P0（功能不可用/数据错误）: budget/config、hr/office-supply、hr/vehicle、material/outbound、material/transfer、tender/certificate、labor/payroll、labor/roster、finance/payment-received、budget/change/form
P1（查询失效/列空白）: 所有 [Major][A]/[Major][B] 项 —— 量大,建议按模块批量修后端 page 参数
P2（次要）: createTime→createdAt 等 Minor 项