# 竞品逆向 · 中经优采（天工工程云）· 附录 A 接口与组件全量清单

> 生成日期：2026-10-10 ｜ 证据：入口 bundle + 118 个业务分片静态抽取（`E`）
> 用途：可检索的资产清单，供 04/05 卷引用与后续增量对比。

---

## A.1 后端接口路径（203 条）

> 抽取方式：bundle 中 `url:"..."` 字面量与 `/模块/...` 模板字符串；**业务接口前缀**为 `/jeecgboot`（本节路径省略该前缀）。
> 分类规则：**含 ≥2 段路径者视为接口**，单段路径归入 A.2。
> 边界：本清单为**前端引用到的**接口，不等于后端全部端点（后端不可见）。

### `/collection` （1）

- `/collection/cancel`

### `/common` （5）

- `/common/approval/cancel`
- `/common/type`
- `/common/type/${e}`
- `/common/type/edit`
- `/common/type/list`

### `/construction` （1）

- `/construction/log/ys/cancel`

### `/contract` （3）

- `/contract/execution/cancel`
- `/contract/settlement/cancel`
- `/contract/visa/cancel`

### `/doc` （81）

- `/doc/archive`
- `/doc/delete?id=${encodeURIComponent(U)}`
- `/doc/download`
- `/doc/file/chunk/cancel/${encodeURIComponent(U)}`
- `/doc/file/chunk/init`
- `/doc/file/chunk/merge?uploadId=${encodeURIComponent(U)}`
- `/doc/file/chunk/presigned-callback?uploadId=${encodeURIComponent(U)}`
- `/doc/file/chunk/presigned-url`
- `/doc/file/chunk/report-etag`
- `/doc/folder/asset`
- `/doc/folder/asset-borrow`
- `/doc/folder/asset-borrow-apply`
- `/doc/folder/asset-inbound`
- `/doc/folder/asset-receive`
- `/doc/folder/asset-receive-apply`
- `/doc/folder/asset-return`
- `/doc/folder/asset-return-apply`
- `/doc/folder/bid-record`
- `/doc/folder/business-trip`
- `/doc/folder/card-reissue`
- `/doc/folder/construction-log`
- `/doc/folder/contract-expense`
- `/doc/folder/contract-expense-execution`
- `/doc/folder/contract-expense-settlement`
- `/doc/folder/contract-expense-visa`
- `/doc/folder/contract-income`
- `/doc/folder/contract-income-execution`
- `/doc/folder/contract-income-settlement`
- `/doc/folder/contract-income-visa`
- `/doc/folder/create`
- `/doc/folder/delete?id=${encodeURIComponent(U)}`
- `/doc/folder/expenses-claim`
- `/doc/folder/formal-employee`
- `/doc/folder/fun-account`
- `/doc/folder/fun-allot`
- `/doc/folder/fun-balance-modify`
- `/doc/folder/fun-collection`
- `/doc/folder/fun-payment`
- `/doc/folder/gate-pass`
- `/doc/folder/hospitality-apply`
- `/doc/folder/inbound-order`
- `/doc/folder/inquiry-task`
- `/doc/folder/inquiry-task-bid`
- `/doc/folder/insurance`
- `/doc/folder/list`
- `/doc/folder/material-outbound`
- `/doc/folder/material-plan`
- `/doc/folder/material-settlement`
- `/doc/folder/material-transfer`
- `/doc/folder/offline-procurement`
- `/doc/folder/outbound-order`
- `/doc/folder/output-log`
- `/doc/folder/outward-apply`
- `/doc/folder/permission`
- `/doc/folder/product-inbound`
- `/doc/folder/project-budget`
- `/doc/folder/project-budget-cost`
- `/doc/folder/project-roster`
- `/doc/folder/purchase-order`
- `/doc/folder/purchase-request`
- `/doc/folder/resignation`
- `/doc/folder/return-order`
- `/doc/folder/root`
- `/doc/folder/roster`
- `/doc/folder/sales-order`
- `/doc/folder/sales-out`
- `/doc/folder/sales-outbound`
- `/doc/folder/schedule`
- `/doc/folder/seal-apply`
- `/doc/folder/send-letter`
- `/doc/folder/stock-in`
- `/doc/folder/stock-in-return`
- `/doc/folder/supplier`
- `/doc/folder/take-leave`
- `/doc/folder/update`
- `/doc/folder/work-overtime`
- `/doc/get`
- `/doc/oss/config`
- `/doc/oss/config/test`
- `/doc/page`
- `/doc/search`

### `/feeManage` （1）

- `/feeManage/expensesClaim/cancel`

### `/found` （2）

- `/found/record/cancel`
- `/found/return/cancel`

### `/fun` （1）

- `/fun/payment/business/${U}/${H}`

### `/invoice` （2）

- `/invoice/in/cancel`
- `/invoice/out/cancel`

### `/multi` （1）

- `/multi/pay/apply/cancel`

### `/oa` （3）

- `/oa/org/tree`
- `/oa/org/tree/user/search`
- `/oa/org/user/${W.getUserInfo.id}/dept`

### `/oauth2-app` （1）

- `/oauth2-app/login`

### `/other` （1）

- `/other/expense/cancel`

### `/pay` （2）

- `/pay/apply/cancel`
- `/pay/record/cancel`

### `/point` （1）

- `/point/work/cancel`

### `/project` （35）

- `/project/${U}`
- `/project/${U}/archive`
- `/project/${U}/cancel-archive`
- `/project/${U}/frequently`
- `/project/${U}/frequently/cancel`
- `/project/budget`
- `/project/budget/${U}`
- `/project/budget/cost/cancel`
- `/project/budget/edit`
- `/project/budget/import/item`
- `/project/budget/list`
- `/project/budget/revise`
- `/project/budget/reviseList`
- `/project/budget/revision/${U}`
- `/project/budget/selectBudgetTotal`
- `/project/list`
- `/project/output/cancel`
- `/project/output/total/list`
- `/project/overview/actual-cost`
- `/project/overview/construction`
- `/project/overview/expense`
- `/project/overview/expense-contract`
- `/project/overview/fund`
- `/project/overview/income-contract`
- `/project/overview/invoice`
- `/project/overview/labor`
- `/project/overview/modules`
- `/project/overview/modules/config`
- `/project/overview/paid-cost`
- `/project/overview/procurement`
- `/project/overview/quality`
- `/project/overview/safety`
- `/project/user-template`
- `/project/user-template/${U}`
- `/project/user-template/list`

### `/projectkanban` （15）

- `/projectkanban/byjsy`
- `/projectkanban/clcb`
- `/projectkanban/detail`
- `/projectkanban/fbcb`
- `/projectkanban/fkdj`
- `/projectkanban/fybx`
- `/projectkanban/fycb`
- `/projectkanban/qtcb`
- `/projectkanban/qtzc`
- `/projectkanban/rgcb`
- `/projectkanban/sbcb`
- `/projectkanban/skdj`
- `/projectkanban/whsjk`
- `/projectkanban/xxjd`
- `/projectkanban/xzff`

### `/purchase` （1）

- `/purchase/offline/cancel`

### `/sales` （2）

- `/sales/order/cancel`
- `/sales/out/cancel`

### `/seal` （1）

- `/seal/apply/cancel`

### `/sys` （26）

- `/sys/cas/client/validateLogin`
- `/sys/category/loadDictItem/`
- `/sys/category/loadTreeData`
- `/sys/dict/getDictItems/`
- `/sys/getLoginQrcode`
- `/sys/getLoginUser/${W}`
- `/sys/getQrcodeToken`
- `/sys/login`
- `/sys/logout`
- `/sys/permission/getPermCode`
- `/sys/permission/getUserPermissionByToken`
- `/sys/phoneLogin`
- `/sys/position/list`
- `/sys/randomImage/${U}`
- `/sys/role/list`
- `/sys/sms`
- `/sys/switchVue3Menu`
- `/sys/sysDepart/queryDepartTreeSync`
- `/sys/sysDepart/queryTreeList`
- `/sys/user/checkOnlyUser`
- `/sys/user/getUserInfo`
- `/sys/user/list`
- `/sys/user/passwordChange`
- `/sys/user/phoneVerification`
- `/sys/user/queryUserComponentData`
- `/sys/user/register`

### `/wage` （1）

- `/wage/pay/cancel`

### `/wflow` （15）

- `/wflow/business/instance/${U}`
- `/wflow/business/instance/${U}/${H}`
- `/wflow/business/usable/${K.businessId}`
- `/wflow/model/detail/${U}`
- `/wflow/model/detail/${le}`
- `/wflow/process/progress/${U}/${H}`
- `/wflow/process/step/deptLeader`
- `/wflow/process/step/depts/belong/depts`
- `/wflow/process/step/leader/level`
- `/wflow/process/step/leader/to/level`
- `/wflow/process/step/userByRoles`
- `/wflow/process/step/users/belong/depts`
- `/wflow/process/task/handler`
- `/wflow/process/task/recall/nodes`
- `/wflow/process/task/urge/${U}`

### `/wms` （1）

- `/wms/salesOut/cancel`

---

## A.2 平台前台路由（21 条）

> 单段路径，属前端路由（非接口）。

- `/aiChat`
- `/bid`
- `/biddetail`
- `/bidernotice`
- `/bidlist`
- `/bidlistpurchase`
- `/bidpreviewdetail`
- `/bidpurchase`
- `/project`
- `/projectbiddetail`
- `/projectdetailpurchase`
- `/projectpurchase`
- `/purchasebiddetail`
- `/purchasebidpreviewdetail`
- `/purchaseinquirydetail`
- `/purchasequotedPrice`
- `/purchasequotedPrice_labor`
- `/purchasequotedPrice_lease`
- `/purchasequotedPrice_team`
- `/purchaserecruitdetail`
- `/purchasewinbid`

---

## A.3 前端懒加载组件全量（612 个唯一基础名）

> 抽取方式：入口 bundle 的 `import("./Xxx.hash.js")` 动态导入表（原始 953 条，去 hash 后去重）。
> 后缀语义：`postform`=编辑表单页 / `_drawer`=抽屉 / `Table`=表格子组件 / `Modal`=弹窗 / `_exportPdf`=PDF 导出模板 / `index`=列表页。

```text
AcceptanceData
AccountBind
AccountManagementDrawer
AllocateAndTransfer
AllocateAndTransfer_drawer
AllocateAndTransfer_exportPdf
AllocateAndTransfer_table
AmountInput
ArchiveForm
AsBuiltDrawings
AssetRequisitionApplicationTable
AssetRequisitionApplication_drawer
AssetsBorrowApplicationTable
AssetsBorrowApplication_drawer
AssetsBorrowTable
AssetsBorrow_drawer
AssetsInboundTable
AssetsInbound_drawer
AssetsListTable
AssetsList_drawer
AssetsReturnApplicationTable
AssetsReturnApplication_drawer
AssetsReturnTable
AssetsReturn_drawer
BaseSetting
BdcTabCard
BidRecordsDrawer
BidRecordsTable
BidRecords_exportPdf
Bidding
Bidding_total
BudgetType
BudgetTypeForm
CategoryModal
ChartGroupCard
CommonApprovalTable
CommonApproval_drawer
CompletionData
ConstructionLogTable
ConstructionLog_drawer
ContractRegistration
DailyTable
Daily_drawer
DataLogCompareModal
DataLogModal
DataRuleList
DataRuleModal
DataSourceModal
DateTime
DateTimeRange
DayWorkTable
DayWork_drawer
DemoModal
DepartBaseInfoTab
DepartDataRuleDrawer
DepartFormModal
DepartFormTab
DepartLeftTree
DepartRoleAuthDrawer
DepartRoleDataRuleDrawer
DepartRoleInfoTab
DepartRoleModal
DepartRoleUserAuthDrawer
DepartRuleTab
DepartTree
DepartUserInfoTab
DepartmentalPositiveEvaluation
DesignSurvey
DestructiveTesting
DetailModal
DictItemList
DictItemModal
DictModal
DictRecycleBinModal
DiskInfo
DrawingDeepening
DynamicInfo
DynamicNotice
EPCcost
EarlyWarningManagement_Modal
ElectricityAccess
ElectricityAccessAudit
Exception
ExitPermit_drawer
ExpenseReimbursementTable
ExpenseReimbursement_drawer
FeasibilityStudyReport
FileUpload
FinancialPreparationTable
FinancialPreparation_drawer
FolderForm
FolderPermissionForm
ForgetPasswordForm
Form
FormDesignRender
FormRender
FrameBlank
GrowCard
ImageUpload
ImprestTable
Imprest_drawer
IndexBdc
IndexChart
IndexDef
IndexTask
InitialInvestmentEvaluationReview
InvoiceCheck
InvoiceCheckItem
InvoiceDetail
InvoicingApplication_table
LoadProof
LoanBillTable
LoanBill_drawer
LockPage
Login
LoginForm
LoginFormTitle
LoginSelect
ManageDrawer
MaterialSelectModal
Materialinventory
MechanicalTable
Mechanical_drawer
MemberTemplateBar
MenuDrawer
MobileForm
MonthlyReportTable
MonthlyReport_drawer
MsgNotify
MultipleSelect
NetworkErrorException
NoticeModal
NumberInput
OnSiteRetesting
OperationAndMaintenance
OssConfig
OtherExpenseTable
OtherExpense_drawer
Outbound
Outbound_drawer
Outbound_exportPdf
Outbound_table
OwnerConfirmationForm
PasswordModal
PositionModal
PositiveReviewMeeting
PriceAddModal
PriceBoard
PriceTable
PriceTrendChart
PriceTrendItem
Pricecomparison
ProjectCard
ProjectCategoryDrawer
ProjectCompanyRegistration
ProjectKickoffMeeting
ProjectOutputTable
ProjectOutputTotalTable
ProjectOutput_drawer
ProjectRegistration_drawer
QrCodeForm
QuartzModal
QuickNav
RefundOfPreparationFundsTable
RefundOfPreparationFunds_drawer
RegisterForm
RepaymentBillTable
RepaymentBill_drawer
RequisitionTable
Requisition_drawer
RoleDataRuleDrawer
RoleDesc
RoleDrawer
RoleIndexModal
RolePermissionDrawer
RoleUserTable
SafetyrectificationTable
Safetyrectification_drawer
SaleRadar
SaleTabCard
SalesOrderExecute
SalesOrderTable
SalesOrder_drawer
SalesOutTable
SalesOut_drawer
SalesOutbound
SalesOutbound_drawer
SalesOutbound_table
SalesProductPie
SecureSetting
SelectInput
SessionTimeoutLogin
SiteAnalysis
StepAcceptanceData
StepAsBuiltDrawings
StepBeiAn
StepBlueprintRefinement
StepContractSign
StepDestructiveTesting
StepDrawingComparison
StepElectricPreAssessment
StepEpcCost
StepEstimatedCostPreAssessment
StepFeasibilityAnalysisReport
StepFieldResurvey
StepInvestmentDecision
StepInvestmentDeptPreAssessmentDecision
StepInvestmentPreAssessment
StepInvestmentPreAssessmentDecision
StepLoadCertificate
StepOnSiteDueDiligence
StepOperationAndMaintenance
StepOwnerConfirmation
StepPM
StepPowerAccess
StepPowerAccessAudit
StepProjectCompanyReg
StepROICalculation
StepRegister
StepResurvey
StepStructurePreAssessment
StepTestRun
StepWrapper
SupplierResponse
SysMessageList
SysMessageModal
TemplateModal
TemplateTestModal
TenantModal
TextInput
TextareaInput
TimePicker
TokenLoginPage
UseSelectModal
UserAgentModal
UserDrawer
UserPicker
UserRecycleBinModal
VisitAnalysis
VisitAnalysisBar
VisitRadar
VisitSource
WagePayTable
WagePay_drawer
WeeklyTable
Weekly_drawer
WorkbenchHeader
add
addAllocateAndTransfer
addAssetRequisitionApplication
addAssetRequisitionApplicationIssue
addAssets
addAssetsBorrow
addAssetsBorrowApplication
addAssetsBorrowApplicationIssue
addAssetsInbound
addAssetsReturn
addAssetsReturnApplication
addAssetsReturnApplicationIssue
addRequisition
addSalesOrder
addSalesOutbound
add_labor
add_lease
add_team
addbank
addbusinessTrip
addcustion
adddepotsettlement
addexpensecarryout
addexpensecontract
addexpensesettlement
addexpensevisa
addformalemployee
addgoOut
addhospitality
addincomecarryout
addincomecontract
addincomesettlement
addincomevisa
addinnercompany
addmaterialplan
addofflineprocurement
addorder
addothercontract
addoutbound
addprojectbudget
addreplacementapply
addresignation
addreturnorder
addsendletter
addstorage
addsupplier
addtakeLeave
addworkovertime
aiChat
aqfx
attendanceListTable
attendanceList_drawer
attendanceRule_drawer
audit
award
awardofcontract
balanceDrawer
balanceadjustment_exportPdf
bank
baseinfo
baseinfoform_ALL
baseinfoform_CLSB
baseinfoform_GCSG
bidbackresponse
bidchart
bidclarify
biddingdocuments
biddinginfo
biddingmanagement
biddingnotice
biddingplan
biddingprocurement
bidevaluation
bidinquiry
bidnotice
bidprepare
bidprepare2
bindDrawer
bindproject
bindroster
blacksupplier
bond
bondsheet
businessTripTable
businessTrip_drawer
byjsy
cardReplacementTable
cardReplacement_drawer
cardtitle
categorize
cbfx
cc
cgfx
chartCbxtj
chartDdrkqx
chartDgjeqst
chartFkjd
chartHkjd
chartJctj
chartJczgqxt
chartJefx
chartKpftj
chartLwxztj
chartRytj
chartYdcb
chartYdcz
chartYdsz
chartYdszyy
chartYdzxje
chartZgtj
chartfpje
chartfpse
chartydse
chartzzse
checkqualityTable
checkquality_drawer
clcb
confirmPM
costcenter_table
custom
custom_draw
data
depotsettlement
depotsettlement_drawer
depotsettlement_exportPdf
depotsettlement_table
deptselect
designCreate
designList
detail
detail2
detailedinformation
discloseTable
disclose_drawer
doubleoffer
downloadfile
earlywarning
editaccountDrawer
en
entrylist
expect
expensecarryout
expensecarryoutTable
expensecontract
expensecontract_drawer
expensecontract_exportPdf
expensecontract_table
expensesettlement
expensesettlementTable
expensevisa
expensevisaTable
exportFdfDrawer
exportPdf
exportPdfDrawer
fbcb
finish
finishmodal
fkdj
formalemployeeTable
formalemployee_drawer
fpfx
fundallocationDrawer
fundallocation_exportPdf
fundpayment_exportPdf
fundreceipt_exportPdf
fybx
fycb
gauge
goOutTable
goOut_drawer
hospitalityTable
hospitality_drawer
ido
incomecarryout
incomecarryout_drawer
incomecarryout_exportPdf
incomecarryout_table
incomecontract
incomecontract_drawer
incomecontract_exportPdf
incomecontract_table
incomesettlement
incomesettlement_drawer
incomesettlement_exportPdf
incomesettlement_table
incomevisa
incomevisa_drawer
incomevisa_exportPdf
incomevisa_table
index
index2
index_Inquiry
index_purchase
index_setting
innercompany
inquirypurchase
insuranceDrawer
insuranceExportPdf
insurancePostform
insuranceTable
jdfx
keepOnRecord
kindmanage
laborRegister
list
looktask
lwfx
m_datepicker
manager
materialOutboundDetails
materialOutboundDrawer
materialOutboundTable
materialplan
materialplan_drawer
materialplan_table
message
multipayapplyTable
multipayapply_drawer
mySubmited
myindex
newEnergy
notificationofaward
offer
offer_agent
offer_labor
offer_labor_agent
offer_lease
offer_lease_agent
offer_team
offer_team_agent
offeritem
offeritem_labor
offeritem_lease
offlineprocurement
offlineprocurement_drawer
offlineprocurement_exportPdf
offlineprocurement_table
onSiteDueDiligence
onlinebid
order
orderTable
order_drawer
order_exportPdf
othercontract
othercontract_drawer
othercontract_exportPdf
othercontract_table
paymentDraw
perfect
postform
postformCustom
postformDynamic
postformInnerCompany
postformPayment
postformSupplier
postformTask
postformTaskItemRemind
postformTaskRemind
postform_address
postform_setting
postformadjustment
postformcomparison
postformelectric
postformestimatedcost
postforminvestment
postforminvestmentaudit
postformmeeting
postformrevenue
postformstructure
postformtypemeeting
prebidclarification
problemsolving
productInboundDrawer
productInboundTable
progressFeedbackModal
projectBudgetYSDrawer
projectDetailComponent
projectJL
projectbid
projectbudget
projectbudget_drawer
projectbudget_exportPdf
projectbudget_table
projectforecast
projectmanage
purchaserequisition
purchaserequisition_drawer
purchaserequisition_exportPdf
purchaserequisition_table
qtcb
qtzc
qualityPostform
questionsananswer
quotedPrice
quotedPrice_labor
quotedPrice_lease
quotedPrice_team
receiptDraw
rectifyqualityTable
rectifyquality_drawer
releaseresult
resignationTable
resignation_drawer
responsetobid
resultnotice
resultspublicity
returnOrder
returnOrder_table
returnorder_drawer
returnorder_exportPdf
review
rgcb
rightlistcomponents
rosterdetail
sbcb
sealapplication_drawer
sealapplication_table
securitycheckTable
securitycheck_drawer
select_depotsettlement
selectcontract
selectreturnordermaterial
sendletterTable
sendletter_drawer
signuplist
skdj
srht
step1
step2
step3
stockInDetails
stockInDrawer
stockInReturnDrawer
stockInReturnTable
stockInStatistics
stockInTable
storage
storage_drawer
storage_exportPdf
storage_table
summary
supplierbidding
supplierbidding_material
supplierbidding_winbid
supplierdetail
supplierinvoiceinfo
takeLeaveTable
takeLeave_drawer
taskItemRemind
taskRemind
template1
testRun
ticketCollectionDetails
todo
userDetails
userselect
whsjk
workovertimeTable
workovertime_drawer
xxjd
xzff
zcht
zh_CN
zjfx
zlfx
```

---

## A.4 全站菜单树（309 个节点）

> 抽取方式：登录后 `GET /sys/permission/getUserPermissionByToken` 的 `menu` 树（`E`，本租户实测）。
> 含"编辑 XX"等**隐藏路由页**（不出现在左侧菜单，但注册为路由）。

- 首页  `/dashboard`  → `layouts/default/index`
  - 工作台  `/purchasehome`  → `PurchaseHome/index`
  - 看板  `/dashboard/projectkanban`  → `dashboard/projectkanban/index`
  - 看板详情  `/dashboard/projectkanbandetail`  → `dashboard/projectkanban/detail`
  - 待我处理  `/purchasehome/todo`  → `PurchaseHome/todo`
  - 我发起的  `/purchasehome/mySubmited`  → `PurchaseHome/mySubmited`
  - 抄送我的  `/purchasehome/cc`  → `PurchaseHome/cc`
  - 我已审批  `/purchasehome/ido`  → `PurchaseHome/ido`
  - 预警  `/PurchaseHome/earlywarning`  → `PurchaseHome/earlywarning`
- 新能源管理  `/NewEnergyManagement`  → `layouts/RouteView`
  - 项目开发  `/ProjectRegistration`  → `ProjectRegistration/index`
  - 编辑项目开发  `/ProjectRegistration/postform`  → `ProjectRegistration/postform`
  - 项目验收  `/ProjectAcceptance`  → `ProjectRegistration/index2`
  - 项目全览  `/ProjectRegistrationDetail`  → `ProjectRegistration/components/detail`
  - 项目全览  `/ProjectRegistrationDetailNew`  → `ProjectRegistration/components/detail2`
  - 项目团队  `/ProjectRegistrationUser`  → `ProjectRegistration/components/userselect`
  - 项目部门  `/ProjectRegistrationDept`  → `ProjectRegistration/components/deptselect`
  - 项目开发  `/ProjectRegistrationNew`  → `ProjectRegistrationNew/index`
  - 编辑项目开发  `/ProjectRegistrationNew/postform`  → `ProjectRegistrationNew/postform`
- 项目管理  `/projectmanage`  → `layouts/RouteView`
  - 项目  `/projects`  → `projectmanage/project/index`
  - 新增项目  `/projects/add`  → `projectmanage/project/add`
  - 项目预算  `/projectbudget`  → `projectmanage/project/projectbudget`
  - 编辑项目预算  `/addprojectbudget`  → `projectmanage/project/addprojectbudget`
  - 项目预算  `/projectbudgetYS`  → `projectmanage/projectBudget/index`
  - 编辑项目预算  `/addprojectbudgetYS`  → `projectmanage/projectBudget/postform`
  - 成本中心  `/projectmanage/costcenter`  → `projectmanage/costcenter/index`
- 合同管理  `/projectcontract`  → `layouts/RouteView`
  - 收入合同  `/incomecontract`  → `projectmanage/projectcontract/incomecontract`
  - 编辑收入合同  `/addincomecontract`  → `projectmanage/projectcontract/addincomecontract`
  - 产值登记  `/ProjectOutput/ProjectOutputList`  → `ProjectOutput/ProjectOutputList/index`
  - 编辑产值登记  `/ProjectOutput/ProjectOutputList/postform`  → `ProjectOutput/ProjectOutputList/postform`
  - 收入合同签证  `/incomevisa`  → `projectmanage/projectcontract/incomevisa`
  - 编辑收入合同签证  `/addincomevisa`  → `projectmanage/projectcontract/addincomevisa`
  - 收入合同执行  `/incomecarryout`  → `projectmanage/projectcontract/incomecarryout`
  - 编辑收入合同执行  `/addincomecarryout`  → `projectmanage/projectcontract/addincomecarryout`
  - 收入合同结算  `/incomesettlement`  → `projectmanage/projectcontract/incomesettlement`
  - 编辑收入合同结算  `/addincomesettlement`  → `projectmanage/projectcontract/addincomesettlement`
  - 支出合同  `/expensecontract`  → `projectmanage/projectcontract/expensecontract`
  - 编辑支出合同  `/addexpensecontract`  → `projectmanage/projectcontract/addexpensecontract`
  - 支出合同签证  `/expensevisa`  → `projectmanage/projectcontract/expensevisa`
  - 编辑支出合同签证  `/addexpensevisa`  → `projectmanage/projectcontract/addexpensevisa`
  - 支出合同执行  `/expensecarryout`  → `projectmanage/projectcontract/expensecarryout`
  - 编辑支出合同执行  `/addexpensecarryout`  → `projectmanage/projectcontract/addexpensecarryout`
  - 支出合同结算  `/expensesettlement`  → `projectmanage/projectcontract/expensesettlement`
  - 编辑支出合同结算  `/addexpensesettlement`  → `projectmanage/projectcontract/addexpensesettlement`
  - 其他合同  `/othercontract`  → `projectmanage/projectcontract/othercontract`
  - 编辑其他合同  `/addothercontract`  → `projectmanage/projectcontract/addothercontract`
- 采购管理  `/purchasemanage`  → `layouts/RouteView`
  - 采购申请  `/purchaserequisition/purchaserequisition`  → `purchaserequisition/purchaserequisition`
  - 编辑采购申请  `/purchaserequisition/add`  → `purchaserequisition/add`
  - 招标管理  `/purchasemanage/biddingmanagement`  → `purchasemanage/biddingmanagement`
  - 便捷采购  `/convenientprocurement`  → `convenientprocurement/index`
  - 询价管理  `/convenientprocurement/index_Inquiry`  → `convenientprocurement/index_Inquiry`
  - 竞价拍卖  `/purchasemanage/biddingprocurement`  → `purchasemanage/biddingprocurement`
  - 线下采购  `/purchasemanage/offlineprocurement`  → `purchasemanage/offlineprocurement`
  - 编辑线下采购  `/purchasemanage/addofflineprocurement`  → `purchasemanage/addofflineprocurement`
  - 采购订单  `/purchaserequisition/order`  → `purchaserequisition/order`
  - 编辑采购订单  `/purchaserequisition/addorder`  → `purchaserequisition/addorder`
  - 招标流程  `/purchasemanage/biddingmanagementdetail`  → `purchasemanage/biddingmanagementdetail/index`
  - 询价流程  `/purchasemanage/inquirypurchasedetail`  → `purchasemanage/inquirypurchasedetail/index`
  - 编辑物资询价  `/convenientprocurement/add`  → `convenientprocurement/add`
  - 材料结算单  `/purchaserequisition/depotsettlement`  → `purchaserequisition/depotsettlement`
  - 编辑材料结算单  `/purchaserequisition/adddepotsettlement`  → `purchaserequisition/adddepotsettlement`
  - 保证金管理  `/convenientprocurement/bond`  → `convenientprocurement/bond`
  - 编辑租赁询价  `/convenientprocurement/add_lease`  → `convenientprocurement/add_lease`
  - 编辑劳务询价  `/convenientprocurement/add_labor`  → `convenientprocurement/add_labor`
  - 编辑班组询价  `/convenientprocurement/add_team`  → `convenientprocurement/add_team`
  - 编辑招标竞价  `/addbiddingprocurementdetail`  → `purchasemanage/biddingprocurementdetail/add`
  - 竞价详情  `/biddingprocurement/detail`  → `purchasemanage/biddingprocurementdetail/detail`
  - 竞价大厅  `/biddingprocurement/Bidding`  → `purchasemanage/biddingprocurementdetail/Bidding`
  - 授标  `/award`  → `purchasemanage/biddingprocurementdetail/award`
  - 发布结果  `/releaseresult`  → `purchasemanage/biddingprocurementdetail/releaseresult`
  - 竞价结束  `/bidfinish`  → `purchasemanage/biddingprocurementdetail/finish`
  - 详细信息  `/detailedinformation`  → `purchasemanage/biddingprocurementdetail/detailedinformation`
- 物料库管理  `/purchaserequisition`  → `layouts/RouteView`
  - 材料计划  `/purchaserequisition/materialplan`  → `purchaserequisition/materialplan`
  - 编辑材料计划  `/addmaterialplan`  → `purchaserequisition/addmaterialplan`
  - 入库单  `/purchaserequisition/storage`  → `purchaserequisition/storage`
  - 编辑入库单  `/purchaserequisition/addstorage`  → `purchaserequisition/addstorage`
  - 退货单  `/purchaserequisition/returnOrder`  → `purchaserequisition/returnOrder`
  - 编辑退货单  `/purchaserequisition/addreturnorder`  → `purchaserequisition/addreturnorder`
  - 出库单  `/purchaserequisition/Outbound`  → `purchaserequisition/Outbound`
  - 编辑出库单  `/purchaserequisition/addoutbound`  → `purchaserequisition/addoutbound`
  - 销售出库单  `/purchaserequisition/SalesOutbound`  → `purchaserequisition/SalesOutbound`
  - 编辑销售出库单  `/purchaserequisition/addSalesOutbound`  → `purchaserequisition/addSalesOutbound`
  - 项目库存  `/purchaserequisition/Materialinventory`  → `purchaserequisition/Materialinventory`
  - 调拨单  `/purchaserequisition/AllocateAndTransfer`  → `purchaserequisition/AllocateAndTransfer`
  - 编辑调拨单  `/purchaserequisition/addAllocateAndTransfer`  → `purchaserequisition/addAllocateAndTransfer`
- 投标管理  `/biddingManagement`  → `layouts/RouteView`
  - 投标记录  `/biddingManagement/BidRecords`  → `biddingManagement/BidRecords/index`
  - 编辑投标记录  `/biddingManagement/BidRecords/postform`  → `biddingManagement/BidRecords/postform`
- 文档管理  `/doc`  → `layouts/RouteView`
  - 公司文档  `/doc/company`  → `doc/index`
  - 项目文档  `/doc/project`  → `doc/index`
  - OSS存储配置  `/doc/ossConfig`  → `doc/OssConfig`
- 供应商管理  `/purchasesupplier`  → `purchasesupplier/index`
- 供应商资料  `/purchasesupplier/perfect`  → `purchasesupplier/perfect`
- 分类管理  `/categorize`  → `purchasesupplier/categorize`
- 费用管理  `/costManage`  → `layouts/RouteView`
  - 费用报销  `/costManage/ExpenseReimbursement`  → `ExpenseReimbursement/index`
  - 编辑费用报销  `/costManage/ExpenseReimbursement/postform`  → `ExpenseReimbursement/postform`
  - 备用金  `/costManage/Imprest`  → `Imprest/index`
  - 编辑备用金  `/costManage/Imprest/postform`  → `Imprest/postform`
  - 借款单  `/LoanBill`  → `LoanBill/index`
  - 编辑借款单  `/LoanBill/postform`  → `LoanBill/postform`
  - 还款单  `/RepaymentBill`  → `RepaymentBill/index`
  - 编辑还款单  `/RepaymentBill/postform`  → `RepaymentBill/postform`
  - 资金筹备  `/FinancialAccounts/FinancialPreparation`  → `FinancialAccounts/FinancialPreparation/index`
  - 编辑资金筹备  `/FinancialAccounts/FinancialPreparation/postform`  → `FinancialAccounts/FinancialPreparation/postform`
  - 筹备金退回  `/FinancialAccounts/RefundOfPreparationFunds`  → `FinancialAccounts/RefundOfPreparationFunds/index`
  - 编辑筹备金退回  `/FinancialAccounts/RefundOfPreparationFunds/postform`  → `FinancialAccounts/RefundOfPreparationFunds/postform`
- 财务账款  `/FinancialAccounts`  → `layouts/RouteView`
  - 收款登记  `/FinancialAccounts/Receiptregister`  → `FinancialAccounts/Receiptregister/index`
  - 编辑收款登记  `/FinancialAccounts/Receiptregister/postform`  → `FinancialAccounts/Receiptregister/postform`
  - 付款申请  `/FinancialAccounts/payapply`  → `FinancialAccounts/payapply/index`
  - 编辑付款申请  `/FinancialAccounts/payapply/postform`  → `FinancialAccounts/payapply/postform`
  - 付款登记  `/FinancialAccounts/payregister`  → `FinancialAccounts/payregister/index`
  - 编辑付款登记  `/FinancialAccounts/payregister/postform`  → `FinancialAccounts/payregister/postform`
  - 薪资发放  `/FinancialAccounts/WagePay`  → `FinancialAccounts/WagePay/index`
  - 编辑薪资发放  `/FinancialAccounts/WagePay/postform`  → `FinancialAccounts/WagePay/postform`
  - 其他支出  `/FinancialAccounts/OtherExpense`  → `FinancialAccounts/OtherExpense/index`
  - 编辑其他支出  `/FinancialAccounts/OtherExpense/postform`  → `FinancialAccounts/OtherExpense/postform`
  - 开票申请  `/FinancialAccounts/InvoicingApplication`  → `FinancialAccounts/InvoicingApplication/index`
  - 编辑开票申请  `/FinancialAccounts/InvoicingApplication/postform`  → `FinancialAccounts/InvoicingApplication/postform`
  - 进项发票  `/FinancialAccounts/InputInvoice`  → `FinancialAccounts/InputInvoice/index`
  - 编辑进项发票  `/FinancialAccounts/InputInvoice/postform`  → `FinancialAccounts/InputInvoice/postform`
  - 销项发票  `/FinancialAccounts/OutputInvoice`  → `FinancialAccounts/OutputInvoice/index`
  - 编辑销项发票  `/FinancialAccounts/OutputInvoice/postform`  → `FinancialAccounts/OutputInvoice/postform`
- 专家库  `/PurchaseExpert`  → `PurchaseExpert/index`
- 发票管理  `/InoviceManage`  → `layouts/RouteView`
  - 发票夹  `/InoviceManage/InvoiceHolder`  → `InoviceManage/InvoiceHolder/index`
- 基础资料  `/baseinformation`  → `layouts/RouteView`
  - 客户  `/baseinformation/custom`  → `baseinformation/custom`
  - 编辑客户  `/baseinformation/addcustion`  → `baseinformation/addcustion`
  - 内部单位  `/baseinformation/innercompany`  → `baseinformation/innercompany`
  - 编辑内部单位  `/baseinformation/addinnercompany`  → `baseinformation/addinnercompany`
  - 质量检查项  `/baseinformation/checkquality`  → `baseinformation/checkquality/index`
  - 安全检查项  `/baseinformation/securitycheck`  → `baseinformation/securitycheck/index`
  - 银行信息  `/mybank`  → `mybank/index`
  - 预算类型  `/baseinformation/budgetType`  → `baseinformation/BudgetType`
  - 基础资料  `/basic`  → `baseinformation/baseinformation/index`
- 施工管理  `/ConstructionManagement`  → `layouts/RouteView`
  - 进度管理  `/schedule`  → `schedule/index`
  - 进度管理详情  `/schedule/detail`  → `schedule/detail`
  - 编辑进度  `/schedule/postform`  → `schedule/postform`
  - 编辑动态  `/schedule/postformDynamic`  → `schedule/postformDynamic`
  - 任务管理  `/taskManagement`  → `taskManagement/index`
  - 花名册  `/ConstructionManagement/roster`  → `ConstructionManagement/roster/index`
  - 花名册详情  `/ConstructionManagement/roster/detail`  → `ConstructionManagement/roster/detail`
  - 编辑花名册  `/ConstructionManagement/roster/postform`  → `ConstructionManagement/roster/postform`
  - 项目花名册  `/ConstructionManagement/projectRoster`  → `ConstructionManagement/projectRoster/index`
  - 项目花名册详情  `/ConstructionManagement/projectRoster/detail`  → `ConstructionManagement/projectRoster/detail`
  - 编辑项目花名册  `/ConstructionManagement/projectRoster/postform`  → `ConstructionManagement/projectRoster/postform`
  - 项目花名册人员详情  `/ConstructionManagement/projectRoster/rosterdetail`  → `ConstructionManagement/projectRoster/rosterdetail`
  - 班组  `/ConstructionManagement/teamGroup`  → `ConstructionManagement/teamGroup/index`
  - 班组详情  `/ConstructionManagement/teamGroup/detail`  → `ConstructionManagement/teamGroup/detail`
  - 编辑班组  `/ConstructionManagement/teamGroup/postform`  → `ConstructionManagement/teamGroup/postform`
  - 点工单  `/ConstructionManagement/DayWork`  → `ConstructionManagement/DayWork/index`
  - 编辑点工单  `/ConstructionManagement/DayWork/postform`  → `ConstructionManagement/DayWork/postform`
  - 施工日志  `/ConstructionManagement/ConstructionLog`  → `ConstructionManagement/ConstructionLog/index`
  - 编辑施工日志  `/ConstructionManagement/ConstructionLog/postform`  → `ConstructionManagement/ConstructionLog/postform`
  - 施工日志  `/ConstructionManagement/ConstructionLogYs`  → `ConstructionManagement/ConstructionLogYS/index`
  - 编辑施工日志  `/ConstructionManagement/ConstructionLogYS/postform`  → `ConstructionManagement/ConstructionLogYS/postform`
  - 日报  `/ConstructionManagement/Daily`  → `ConstructionManagement/Daily/index`
  - 编辑日报  `/ConstructionManagement/Daily/postform`  → `ConstructionManagement/Daily/postform`
  - 周报  `/ConstructionManagement/Weekly`  → `ConstructionManagement/Weekly/index`
  - 编辑周报  `/ConstructionManagement/Weekly/postform`  → `ConstructionManagement/Weekly/postform`
  - 月报  `/ConstructionManagement/MonthlyReport`  → `ConstructionManagement/MonthlyReport/index`
  - 编辑月报  `/ConstructionManagement/MonthlyReport/postform`  → `ConstructionManagement/MonthlyReport/postform`
  - 质量检查  `/ConstructionManagement/checkquality`  → `ConstructionManagement/checkquality/index`
  - 编辑质量检查  `/ConstructionManagement/checkquality/postform`  → `ConstructionManagement/checkquality/postform`
  - 质量整改单  `/ConstructionManagement/rectifyquality`  → `ConstructionManagement/rectifyquality/index`
  - 编辑质量整改单  `/ConstructionManagement/rectifyquality/postform`  → `ConstructionManagement/rectifyquality/postform`
  - 安全检查  `/ConstructionManagement/securitycheck`  → `ConstructionManagement/securitycheck/index`
  - 编辑安全检查  `/ConstructionManagement/securitycheck/postform`  → `ConstructionManagement/securitycheck/postform`
  - 安全整改单  `/ConstructionManagement/Safetyrectification`  → `ConstructionManagement/Safetyrectification/index`
  - 编辑安全整改单  `/ConstructionManagement/Safetyrectification/postform`  → `ConstructionManagement/Safetyrectification/postform`
  - 交底管理  `/ConstructionManagement/disclose`  → `ConstructionManagement/disclose/index`
  - 添加交底  `/ConstructionManagement/disclose/postform`  → `ConstructionManagement/disclose/postform`
- 绩效考核  `/Assessment`  → `layouts/RouteView`
  - 日报  `/Assessment/Daily`  → `ConstructionManagement/DailyAssessment/index`
  - 编辑日报  `/Assessment/Daily/postform`  → `ConstructionManagement/DailyAssessment/postform`
  - 周报  `/Assessment/Weekly`  → `ConstructionManagement/WeeklyAssessment/index`
  - 编辑周报  `/Assessment/Weekly/postform`  → `ConstructionManagement/WeeklyAssessment/postform`
  - 月报  `/Assessment/MonthlyReport`  → `ConstructionManagement/MonthlyReportAssessment/index`
  - 编辑月报  `/Assessment/MonthlyReport/postform`  → `ConstructionManagement/MonthlyReportAssessment/postform`
- 统计分析  `/StatisticAnalysis`  → `layouts/RouteView`
  - 供应商账款  `/SupplierAccounts`  → `StatisticAnalysis/SupplierAccounts/index`
  - 客户账款  `/StatisticAnalysis/situationCustomer`  → `StatisticAnalysis/situationCustomer/index`
  - 项目经营分析  `/ProjectBusinessAnalysis`  → `StatisticAnalysis/ProjectBusinessAnalysis/index`
  - 合同执行表  `/statistic/ContractExecute`  → `StatisticAnalysis/ContractExecute/index`
  - 收票明细表  `/payInvoiceDetailReport`  → `StatisticAnalysis/payInvoiceDetailReport/index`
  - 产值统计  `/ProjectOutput/ProjectOutputTotalList`  → `ProjectOutput/ProjectOutputTotalList/index`
- 保险管理  `/InsuranceManage`  → `InsuranceManage/index`
- 编辑保险管理  `/addInsuranceManage`  → `InsuranceManage/insurancePostform`
- 资金账户管理  `/FundAccountManagement`  → `layouts/RouteView`
  - 账户管理  `/FundAccountManagement/AccountManagement`  → `FundAccountManagement/AccountManagement/index`
  - 编辑账户管理  `/FundAccountManagement/AccountManagement/postform`  → `FundAccountManagement/AccountManagement/postform`
  - 资金到账  `/FundAccountManagement/fundreceipt`  → `FundAccountManagement/fundreceipt/index`
  - 编辑资金到账  `/FundAccountManagement/fundreceipt/postform`  → `FundAccountManagement/fundreceipt/postform`
  - 资金支付  `/FundAccountManagement/fundpayment`  → `FundAccountManagement/fundpayment/index`
  - 编辑资金支付  `/FundAccountManagement/fundpayment/postform`  → `FundAccountManagement/fundpayment/postform`
  - 编辑单据  `/FundAccountManagement/fundpayment/postformPayment`  → `FundAccountManagement/fundpayment/postformPayment`
  - 资金调拨  `/FundAccountManagement/fundallocation`  → `FundAccountManagement/fundallocation/index`
  - 编辑资金调拨  `/FundAccountManagement/fundallocation/postform`  → `FundAccountManagement/fundallocation/postform`
  - 余额调整  `/FundAccountManagement/balanceadjustment`  → `FundAccountManagement/balanceadjustment/index`
  - 编辑余额调整  `/FundAccountManagement/balanceadjustment/postform`  → `FundAccountManagement/balanceadjustment/postform`
- 资产管理  `/AssetsManage`  → `layouts/RouteView`
  - 资产列表  `/AssetsManage/AssetsList`  → `AssetsManage/AssetsList/index`
  - 编辑资产  `/AssetsManage/addAssets`  → `AssetsManage/AssetsList/addAssets`
  - 资产入库  `/AssetsManage/AssetsInbound`  → `AssetsManage/AssetsInbound/index`
  - 编辑资产入库  `/AssetsManage/addAssetsInbound`  → `AssetsManage/AssetsInbound/addAssetsInbound`
  - 资产领用  `/AssetsManage/Requisition`  → `AssetsManage/Requisition/index`
  - 编辑资产领用  `/AssetsManage/addRequisition`  → `AssetsManage/Requisition/addRequisition`
  - 资产退库  `/AssetsManage/AssetsReturn`  → `AssetsManage/AssetsReturn/index`
  - 编辑资产退库  `/AssetsManage/addAssetsReturn`  → `AssetsManage/AssetsReturn/addAssetsReturn`
  - 资产借用  `/AssetsManage/AssetsBorrow`  → `AssetsManage/AssetsBorrow/index`
  - 编辑资产借用  `/AssetsManage/addAssetsBorrow`  → `AssetsManage/AssetsBorrow/addAssetsBorrow`
- 加工厂  `/CRM`  → `layouts/RouteView`
  - 销售订单管理  `/SalesOrder`  → `CRM/SalesOrder/index`
  - 编辑销售订单  `/addSalesOrder`  → `CRM/SalesOrder/postform`
  - 生产执行统计表  `/SalesOrderExecute`  → `CRM/SalesOrder/SalesOrderExecute`
  - 进货单  `/stockIn`  → `CRM/stockIn/index`
  - 编辑进货单  `/addStockIn`  → `CRM/stockIn/postform`
  - 进货单统计表  `/stockInStatistics`  → `CRM/stockIn/stockInStatistics`
  - 进货单明细表  `/stockInDetails`  → `CRM/stockIn/stockInDetails`
  - 退货单  `/stockInReturn`  → `CRM/stockInReturn/index`
  - 编辑退货单  `/addStockInReturn`  → `CRM/stockInReturn/postform`
  - 材料出库单  `/materialOutbound`  → `CRM/materialOutbound/index`
  - 编辑材料出库单  `/addMaterialOutbound`  → `CRM/materialOutbound/postform`
  - 材料出库明细表  `/materialOutboundDetails`  → `CRM/materialOutbound/materialOutboundDetails`
  - 成品入库  `/productInbound`  → `CRM/productInbound/index`
  - 编辑成品入库  `/addProductInbound`  → `CRM/productInbound/postform`
  - 销售订单出库  `/SalesOut`  → `CRM/SalesOut/index`
  - 编辑销售订单出库  `/addSalesOut`  → `CRM/SalesOut/postform`
  - 出门证  `/ExitPermit`  → `CRM/ExitPermit/index`
  - 新增出门证  `/addExitPermit`  → `CRM/ExitPermit/postform`
  - 库存现存量管理  `/inventoryStock`  → `CRM/inventoryStock/index`
- 考勤管理  `/attendanceSetting`  → `layouts/RouteView`
  - 考勤设置  `/attendanceListSetting`  → `attendanceList/index_setting`
  - 考勤列表  `/attendanceSetting/attendanceList`  → `attendanceList/index`
- OA审批  `/OA`  → `layout/RouteView`
  - 用印申请  `/SealApplication`  → `projectmanage/SealApplication/index`
  - 外出申请  `/OA/myGoOut`  → `OA/goOut/myindex`
  - 补卡申请  `/OA/myReplacementapply`  → `OA/replacementapply/myindex`
  - 离职申请  `/OA/myResignation`  → `OA/resignation/myindex`
  - 出差申请  `/OA/myBusinessTrip`  → `OA/businessTrip/myindex`
  - 请假申请  `/OA/myTakeLeave`  → `OA/takeLeave/myindex`
  - 加班申请  `/OA/myWorkovertime`  → `OA/workovertime/myindex`
  - 公函制发申请  `/OA/mysendletter`  → `OA/sendletter/myindex`
  - 转正申请  `/OA/myformalemployee`  → `OA/formalemployee/myindex`
  - 业务招待申请  `/OA/myhospitality`  → `OA/hospitality/myindex`
  - 资产领用申请  `/AssetsManage/myAssetRequisitionApplication`  → `AssetsManage/AssetRequisitionApplication/myindex`
  - 资产退库申请  `/AssetsManage/AssetsReturnApplication/myindex`  → `AssetsManage/AssetsReturnApplication/myindex`
  - 资产借用申请  `/AssetsManage/myAssetsBorrowApplication`  → `AssetsManage/AssetsBorrowApplication/myindex`
  - 通用审批  `/CommonApproval`  → `CommonApproval/index`
  - 编辑通用申请  `/CommonApproval/postform`  → `CommonApproval/postform`
- OA管理  `/OAManage`  → `layouts/RouteView`
  - 外出申请  `/OA/goOut`  → `OA/goOut/index`
  - 补卡申请  `/OA/replacementapply`  → `OA/replacementapply/index`
  - 离职申请  `/OA/resignation`  → `OA/resignation/index`
  - 出差申请  `/OA/businessTrip`  → `OA/businessTrip/index`
  - 请假申请  `/OA/takeLeave`  → `OA/takeLeave/index`
  - 加班申请  `/OA/workovertime`  → `OA/workovertime/index`
  - 公函制发申请  `/OA/sendletter`  → `OA/sendletter/index`
  - 转正申请  `/OA/formalemployee`  → `OA/formalemployee/index`
  - 业务招待申请  `/OA/hospitality`  → `OA/hospitality/index`
  - 资产领用申请  `/AssetRequisitionApplication`  → `AssetsManage/AssetRequisitionApplication/index`
  - 资产退库申请  `/AssetsManage/AssetsReturnApplication`  → `AssetsManage/AssetsReturnApplication/index`
  - 资产借用申请  `/AssetsManage/AssetsBorrowApplication`  → `AssetsManage/AssetsBorrowApplication/index`
- 编辑用印申请  `/addSealApplication`  → `projectmanage/SealApplication/postform`
- 编辑外出申请  `/OA/addgoOut`  → `OA/goOut/addgoOut`
- 编辑补卡申请  `/OA/addreplacementapply`  → `OA/replacementapply/addreplacementapply`
- 编辑离职申请  `/OA/addresignation`  → `OA/resignation/addresignation`
- 编辑出差申请  `/OA/addbusinessTrip`  → `OA/businessTrip/addbusinessTrip`
- 编辑请假申请  `/OA/addtakeLeave`  → `OA/takeLeave/addtakeLeave`
- 编辑加班申请  `/OA/addworkovertime`  → `OA/workovertime/addworkovertime`
- 编辑公函制发申请  `/OA/addsendletter`  → `OA/sendletter/addsendletter`
- 编辑转正申请  `/OA/addformalemployee`  → `OA/formalemployee/addformalemployee`
- 编辑业务招待申请  `/OA/addhospitality`  → `OA/hospitality/addhospitality`
- 编辑资产领用申请  `/AssetsManage/addAssetRequisitionApplication`  → `AssetsManage/AssetRequisitionApplication/addAssetRequisitionApplication`
- 资产领用申请发放  `/AssetsManage/addAssetRequisitionApplicationIssue`  → `AssetsManage/AssetRequisitionApplication/addAssetRequisitionApplicationIssue`
- 编辑资产退库申请  `/AssetsManage/addAssetsReturnApplication`  → `AssetsManage/AssetsReturnApplication/addAssetsReturnApplication`
- 资产退库申请退库  `/AssetsManage/addAssetsReturnApplicationIssue`  → `AssetsManage/AssetsReturnApplication/addAssetsReturnApplicationIssue`
- 编辑资产借用申请  `/AssetsManage/addAssetsBorrowApplication`  → `AssetsManage/AssetsBorrowApplication/addAssetsBorrowApplication`
- 资产借用申请发放  `/AssetsManage/addAssetsBorrowApplicationIssue`  → `AssetsManage/AssetsBorrowApplication/addAssetsBorrowApplicationIssue`
- 铜铝价格  `/onlinePrice`  → `layouts/RouteView`
  - 价格分析  `/price/commodity`  → `price/commodity/index`
- 订单管理  `/sysorder`  → `sysorder/index`
- 个人设置  `/person/accountsetting`  → `demo/page/account/setting/index`
- 系统管理  `/isystem`  → `layouts/RouteView`
  - 系统公告  `/purchasenotice`  → `purchasenotice/index`
  - 预警管理  `/EarlyWarningManagement`  → `system/EarlyWarningManagement/index`
  - 角色管理  `/system/role`  → `system/role/index`
  - 物料信息  `/system/baseinfosetting/materialwarehouse`  → `system/baseinfosetting/materialwarehouse/index`
  - 技术评分项管理  `/TechnicalScore`  → `system/TechnicalScore/index`
  - 商务评分项管理  `/BusinessRating`  → `system/BusinessRating/index`
  - 用户管理  `/system/user`  → `system/user/index`
  - 采购单位设置  `/system/purchaseset`  → `system/purchaseset/index`
  - 网站banner  `/system/purchasebanner`  → `system/purchasebanner/index`
  - 部门管理  `/system/depart`  → `system/depart/index`
  - 我的部门  `/system/depart-user`  → `system/departUser/index`
- (无标题)  `/online`  → `layouts/default/index`
  - AUTO在线报表  `/online/cgreport/:id`  → `super/online/cgreport/auto/OnlCgReportList`
  - AUTO在线表单  `/online/cgformList/:id`  → `super/online/cgform/auto/default/OnlineAutoList`
  - AUTO树表单列表  `/online/cgformTreeList/:id`  → `super/online/cgform/auto/tree/OnlineAutoTreeList`
  - Online表单视图  `/online/copyform/:code`  → `super/online/cgform/CgformCopyList`
  - AUTO在线图表  `/online/graphreport/chart/:code`  → `super/online/graphreport/auto/GraphreportAutoChart`

---

## A.5 系统字典清单（53 条）

| dictCode | 字典名 | 说明 |
|---|---|---|
| `msg_category` | 通告类型 | 消息类型1:通知公告2:系统消息 |
| `position_rank` | 职务职级 | 职务表职级字典 |
| `org_category` | 机构类型 | 机构类型 1公司，2部门 3岗位 |
| `form_perms_type` | 表单权限策略 |  |
| `urgent_level` | 紧急程度 | 日程计划紧急程度 |
| `eoa_plan_type` | 日程计划类型 |  |
| `eoa_cms_menu_type` | 分类栏目类型 |  |
| `eoa_plan_status` | 日程计划状态 |  |
| `database_type` | 数据库类型 |  |
| `ol_form_biz_type` | Online表单业务分类 |  |
| `quartz_status` | 定时任务状态 |  |
| `tenant_status` | 租户状态 | 租户状态 |
| `is_open` | 开关 |  |
| `pruchase_project_status` | 采购项目状态 |  |
| `workType` | 工种 | 施工管理-花名册-人员工种 |
| `revenueCategory` | 收入类别 | 财务账款-收入登记 |
| `payCategory` | 支出类别 | 财务账款-支出类别 |
| `industry` | 行业类别 | 行业类别 |
| `natureOfBusiness` | 企业性质 | 企业性质 |
| `accountType` | 账户类型 | 资金账户 |
| `sealType` | 印章类型 | 用印申请-印章类型 |
| `warningType` | 预警分类 |  |
| `commonType` | 通用分类 |  |
| `resignationReason` | 离职原因 |  |
| `approvalType` | 申请类型 | 通用申请-申请类型 |
| `activiti_sync` | 同步工作流引擎 | 同步工作流引擎 |
| `perms_type` | 权限策略 | 权限策略 |
| `msg_type` | 推送类别 |  |
| `del_flag` | 删除状态 |  |
| `sex` | 性别 |  |
| `global_perms_type` | 全局权限策略 | 全局权限策略 |
| `online_graph_display_template` | Online图表展示模板 | Online图表展示模板 |
| `dict_item_status` | 字典状态 |  |
| `priority` | 优先级 | 优先级 |
| `rule_conditions` | 条件规则 |  |
| `msgType` | 发送消息类型 |  |
| `valid_status` | 有效无效状态 | 有效无效状态 |
| `user_type` | 用户类型 |  |
| `cgform_table_type` | Online表单类型 |  |
| `bpm_status` | 流程状态 | 流程状态 |
| `log_type` | 日志类型 |  |
| `status` | 状态 |  |
| `operate_type` | 操作类型 | 操作类型 |
| `send_status` | 发布状态 | 发布状态 |
| `yn` | 1是0否 |  |
| `msgSendStatus` | 消息发送状态 |  |
| `menu_type` | 菜单类型 |  |
| `ceshi_online` | onlineT类型 |  |
| `online_graph_data_type` | Online图表数据类型 | Online图表数据类型 |
| `depart_status` | 部门状态 |  |
| `online_graph_type` | Online图表类型 | Online图表类型 |
| `bpm_process_type` | 流程类型 |  |
| `user_status` | 用户状态 |  |

> 注：**业务枚举**（支出类别 / 收入类别等）不在 `sys_dict`，而在 `/common/type/list`（按公司隔离），见 02 卷 §4.2。

---

## A.6 运行时发现的接口（静态抽取未覆盖，`E`）

> 来源：授权只读运行时观察（2026-10-10）。A.1 节来自 bundle 静态抽取，**未包含**下列接口（它们由运行时动态拼接或使用了未覆盖的模块前缀）。本节的每条接口均经实际调用验证。
> **这是 07 卷"资金流转与经营分析"的全部数据来源。**

### 经营分析层（`/analyse/*`）

- `GET /analyse/project/v1` —— 项目维度合计（58 指标）
- `GET /analyse/project/list/v1` —— 项目维度分页（每指标 `{divisor,dividend,rate}`）
- `GET /analyse/supplier/v1` / `GET /analyse/supplier/list/v1` —— 供应商账款（25 指标）
- `GET /analyse/customer/v1` / `GET /analyse/customer/list/v1` —— 客户账款（16 指标）
- `GET /analyse/contract/execute/list?label=Income|Expense` —— 合同执行表明细
- `GET /analyse/contract/execute/total?label=Income|Expense` —— 合同执行汇总（含 `unRecordAmount`）
- `GET /analyse/Invoice/list` / `GET /analyse/Invoice/selectTotal` —— 收票明细表

### 项目级经营看板（`/analyze/*`，14 子页的数据源）

- `GET /analyze/fund/imprest/v1` —— 备用金剩余金额（`byjsy`）
- `GET /analyze/cost/material/v1` —— 材料成本（`clcb`，含 `costAddPie`/`costReducePie`）
- `GET /analyze/cost/labor/v1` —— 人工成本（`rgcb`）
- `GET /analyze/cost/device/v1` —— 设备成本（`sbcb`）
- `GET /analyze/cost/farming/v1` —— 分包成本（`fbcb`）
- `GET /analyze/cost/fee/v1` —— 费用成本（`fycb`，本租户返回 `null`）
- `GET /analyze/cost/other/v1` —— 其他成本（`qtcb`）
- `GET /analyze/fund/pay/v1` —— 付款登记（`fkdj`，含 `unRelatedPay`/`supplierPie`）
- `GET /analyze/fund/collection/v1` —— 收款登记（`skdj`，含 `unRelatedCollection`）
- `GET /analyze/fund/other/v1` —— 其他支出（`qtzc`）
- `GET /analyze/fund/expenses/v1` —— 费用报销（`fybx`，本租户返回 `null`）
- `GET /analyze/fund/wage/v1` —— 薪资发放（`xzff`，双维构成）
- `GET /analyze/fund/unLoan/v1` —— 未收回借款（`whsjk`）
- `GET /analyze/rate/trend/v1` —— 形象进度（`xxjd`，本租户返回 `[]`）

### 资金账户与支付（`/fun/*`）

- `GET /fun/account/list` / `GET /fun/account/selectTotal` —— 账户管理（公户/私户）
- `GET /fun/collection/list` / `GET /fun/collection/selectTotal` —— 资金到账（`ZJDZ`）
- `GET /fun/payment/list?payStatus=!PAID` / `GET /fun/payment/selectTotal` —— **资金支付中心**（待付/已付/支付流水）
- `GET /fun/allot/list` / `GET /fun/allot/selectTotal` —— 资金调拨（`ZJDB`）
- `GET /fun/balance/modify/list` / `GET /fun/balance/modify/selectTotal` —— 余额调整

### 费用管理（`/feeManage/*`）

- `GET /feeManage/expensesClaim/list` / `selectTotal` —— 费用报销（`FYBX`，含三向冲抵）
- `GET /feeManage/impress/list` / `selectTotal` —— 备用金（`BYJ`）
- `GET /feeManage/loan/list` / `selectTotal` —— 借款单（`JKD`）
- `GET /feeManage/repayment/list` / `selectTotal` —— 还款单（`HKD`）

### 资金筹备（`/found/*`）

- `GET /found/record/list` / `selectTotal` —— 资金筹备
- `GET /found/return/list` / `selectTotal` —— 筹备金退回

### 采购寻源（运行时确认）

- `GET /inquiry/task/list?serviceType=Material|Lease|Labor|Team&taskType=Inquiry` —— 询价列表
- `GET /bid/purchaser/list` —— 竞价拍卖列表
- `GET /epp/rc/list?buyWay=00` —— 招标项目列表
- `GET /epp/tender/{rcId}/node` —— **招标 12 节点状态机**
- `GET /purchaser/list` / `POST /categoryOwn/list` —— 供应商管理
- `GET /industry/list` —— 供应商行业分类

### 其他运行时确认

- `GET /early/warning/list` —— 预警规则
- `GET /common/type/list` —— **业务字典**（按公司隔离，非 `sys_dict`）
- `GET /cost/center/list` / `GET /cost/center/total` —— 成本中心（`costPaid=1` 实付口径）
- `GET /contract/list` / `GET /contract/selectTotal?label=Income|Expense`
- `GET /pay/apply/list` / `GET /pay/apply/selectTotal`
- `GET /collection/list` / `GET /collection/selectTotal`
- `GET /purchase/request/list` / `GET /purchase/order/list`
- `GET /wms/in/list` / `GET /material/settlement/list`
- `GET /project/budget/list` / `GET /project/budget/selectBudgetTotal`
- `GET /project/output/total/list` —— 产值统计
- `GET /pub/areas` —— 地区级联
- `GET /pub/winning/{id}` —— 中标公示详情
- `GET /wflow/business/usable/{typeId}` / `GET /wflow/model/detail/{modelId}` —— 审批流绑定
- `GET /oa/org/user/{userId}/dept` —— 用户部门
- `GET /doc/folder/{业务类型}` —— 项目文档目录（76 个业务目录键）

> **审批业务类型 ID 实测（`E`）**：`24` = 支出合同、`30` = 材料计划、`51` = 付款申请。
> **流程模型 ID 实测**：`wf65407546e4b001289f23c4f5`（支出合同）、`wf6548b2f8e4b06d429a1d03f3`（材料计划）。

---

## A.7 表单实时校验接口（`E`，运行时发现）

> 这些接口在**打开表单时**即被调用，用于实时带出余额/在途，是防超额领用的关键。

- `GET /feeManage/impress/balance/{projectId}/{userId}` —— 该人该项目的**备用金余额**（备用金表单加载时）
- `GET /feeManage/impress/unReturnOffset/{projectId}/{userId}` —— 该人**未归还的冲抵额**（备用金表单加载时）
- `GET /feeManage/repayment/amount` —— **可还金额**（还款单表单加载时）

## A.8 审批业务类型 ID 与流程模型 ID（`E`，实测）

> 来源：各单据表单加载时调用 `GET /wflow/business/usable/{typeId}` 与 `GET /wflow/model/detail/{modelId}`。

| 业务类型 ID | 业务 | 流程模型 ID |
|---|---|---|
| 24 | 支出合同 | `wf65407546e4b001289f23c4f5` |
| 30 | 材料计划 | `wf6548b2f8e4b06d429a1d03f3` |
| 50 | 收款登记 | — |
| 51 | 付款申请 | — |
| 52 | 付款登记 | `wf65407547e4b001289f23c4fb` |
| 53 | 薪资发放 | `wf65407547e4b001289f23c4fc` |
| 54 | 其他支出 | `wf65407547e4b001289f23c4fd` |
| 55 | 开票申请 | `wf65407547e4b001289f23c4fe` |
| 56 | 进项发票 | — |
| 57 | 销项发票 | — |
| 70 | 备用金 | `wf65951d0ee4b0e9b5156eac16` |
| 71 | 借款单 | `wf65951d0ee4b0e9b5156eac17` |
| 72 | 还款单 | `wf65951d0fe4b0e9b5156eac18` |
| 73 | 费用报销 | `wf65951d0fe4b0e9b5156eac19` |
| 75 | 筹备金退回 | — |
| 83 | 资金调拨 | `wf65fd4d27e4b01fdcc25f768a` |
| 84 | 余额调整 | `wf65fd4d27e4b01fdcc25f768b` |

> **不走审批流的资金单据（`E`）**：账户管理、资金到账、资金支付、资金筹备（表单无 `usable/{typeId}` 调用，按钮为"保存"）。

## A.9 单号规则补充（`E`，本卷新增）

| 单据 | 规则 | 实测样例 |
|---|---|---|
| 收款登记 | `SKDJ{年月日}{3位}` | `SKDJ20260616001` |
| 资金到账 | `ZJDZ{年月日}{3位}` | `ZJDZ20241217001` |
| 资金调拨 | `ZJDB{年月日}{3位}` | `ZJDB20240412001` |
| 付款申请 | `FKSQ{年月日}{3位}` | `FKSQ20251011001` |
| 费用报销 | `FYBX{年月日}{3位}` | `FYBX20240902001` |
| 备用金 | `BYJ{年月日}{3位}` | `BYJ20240823001` |
| 借款单 | `JKD{年月日}{3位}` | `JKD20240325001` |
| 还款单 | `HKD{年月日}{3位}` | `HKD20240409001` |
| 薪资发放 | `XZFF{年月日}{3位}` | `XZFF20240125001` |
