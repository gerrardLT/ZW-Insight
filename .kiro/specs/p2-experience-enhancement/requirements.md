# Requirements Document

## Introduction

基于 ZW-Insight 工程项目管理系统功能表对比差异分析，本需求文档覆盖 P2 优先级的 7 个体验增强功能。这些功能旨在补全移动端业务场景、提供供应商自助服务门户、增强文件管理与数据导入导出能力、完善工程进度可视化，以及提升移动端离线与现场管理体验。

技术栈：Spring Boot 3.2 + MyBatis-Plus + Vue 3 + Element Plus + uni-app + MySQL + Redis 7 + MinIO + dhtmlx-gantt + EasyExcel。

现有基础：后端已有收票/其他付款/备用金/个人报销/退货出库/三方比价/进度计划等完整 API；移动端已有登录、审批、材料入出库、现场管理、部分财务功能页面；dhtmlx-gantt 依赖已安装；EasyExcel 已在部分模块使用。

## Glossary

- **Mobile_App**：基于 uni-app 构建的移动端应用（zw-insight-app）
- **Supplier_Portal**：供应商自助服务门户，独立于主系统的轻量级 Web 应用
- **File_Preview_Service**：文件在线预览服务，支持 Office/PDF 文档预览
- **Template_Service**：模板管理服务，管理导入导出模板和打印模板
- **Batch_Import_Service**：批量导入导出服务，基于 EasyExcel 实现
- **Gantt_Component**：基于 dhtmlx-gantt 的工程进度甘特图组件
- **Offline_Service**：移动端离线数据缓存服务
- **Watermark_Service**：拍照水印服务，在照片上叠加时间/位置/人员信息
- **Location_Service**：GPS 定位签到服务

---

## Requirements

### Requirement 1: 移动端业务页面补全

**User Story:** 作为项目财务人员，我需要在手机端完成收票登记、其他费用付款、备用金申请/归还和个人报销操作，以便在外出或施工现场时也能及时处理财务业务。

#### Acceptance Criteria

1. THE Mobile_App SHALL 提供收票登记页面，支持录入发票号码、供应商名称、金额、发票类型、发票日期，并调用后端 InvoiceReceivedController 保存和提交审批
2. THE Mobile_App SHALL 提供其他费用付款页面，支持选择项目、填写费用类型、金额、收款方信息，并调用后端 OtherPaymentController 保存和提交审批
3. THE Mobile_App SHALL 提供备用金申请页面，支持填写申请金额、用途说明、预计归还日期，并调用后端 ReserveFundController /apply 接口
4. THE Mobile_App SHALL 提供备用金归还页面，展示当前未归还备用金列表，支持选择归还的备用金记录并录入归还金额，调用 ReserveFundController /return 接口
5. THE Mobile_App SHALL 提供个人报销页面，支持填写报销金额、报销事由、费用明细（多行），上传票据附件，调用 PersonalReimbursementController 保存和提交
6. WHEN 用户在移动端提交任何财务单据后，THE Mobile_App SHALL 展示提交成功提示，并可在"我的审批 → 我发起的"列表中查看该单据
7. THE Mobile_App SHALL 在每个财务页面支持附件上传（拍照/从相册选择），文件上传至 MinIO

---

### Requirement 2: 移动端材料退货

**User Story:** 作为材料管理员，我需要在施工现场通过手机端发起材料退货出库操作，以便及时处理不合格材料的退还流程。

#### Acceptance Criteria

1. THE Mobile_App SHALL 提供材料退货页面，支持选择项目、选择入库记录（关联原入库单）、填写退货数量和退货原因
2. WHEN 用户选择入库记录时，THE Mobile_App SHALL 展示该入库记录的材料名称、规格型号、入库数量、已退货数量和可退数量
3. THE Mobile_App SHALL 校验退货数量不超过可退数量（可退 = 入库数量 - 已退货数量），IF 超出 THEN 展示错误提示
4. WHEN 用户提交退货时，THE Mobile_App SHALL 调用后端 OutboundController（outboundType=RETURN）创建退货出库单
5. THE Mobile_App SHALL 支持退货单拍照上传凭证（退货签收单等）
6. WHEN 退货出库单创建成功后，THE Batch_Import_Service SHALL 自动扣减对应项目材料库存

---

### Requirement 3: 供应商门户（独立报价入口）

**User Story:** 作为受邀供应商，我需要通过独立的门户页面查看询价公告详情并提交报价，无需拥有系统完整账号，以便简化报价流程并降低参与门槛。

#### Acceptance Criteria

1. THE Supplier_Portal SHALL 提供独立的登录页面，供应商通过手机号 + 短信验证码登录（或通过邀请链接免登直接进入报价页）
2. THE Supplier_Portal SHALL 展示当前供应商被邀请的询价公告列表（状态为"报价中"的询价）
3. WHEN 供应商点击某个询价公告时，THE Supplier_Portal SHALL 展示询价详情：项目名称、材料清单（名称/规格/数量/单位）、截止时间、要求说明
4. THE Supplier_Portal SHALL 提供报价表单，供应商可逐项填写单价和备注，并上传资质文件附件
5. WHEN 供应商提交报价后，THE Supplier_Portal SHALL 调用后端 QuotationController 保存报价数据，并展示"报价提交成功"提示
6. IF 当前时间已超过询价截止时间，THEN THE Supplier_Portal SHALL 禁止提交报价并展示"报价已截止"提示
7. THE Supplier_Portal SHALL 确保供应商仅能查看和操作自己被邀请的询价，不可访问其他供应商的报价数据
8. THE Supplier_Portal SHALL 提供"我的报价记录"页面，展示历史报价列表及中标结果

---

### Requirement 4: 文件预览/模板管理/打印模板

**User Story:** 作为系统用户，我需要在线预览已上传的 Office/PDF 文件而无需下载，并通过模板管理功能统一维护导入导出模板和打印模板，以提升文件操作效率。

#### Acceptance Criteria

1. THE File_Preview_Service SHALL 支持在线预览以下文件格式：.doc, .docx, .xls, .xlsx, .pdf, .ppt, .pptx, .jpg, .png
2. WHEN 用户点击附件的"预览"按钮时，THE PC_Frontend SHALL 在新标签页或弹窗中打开文件预览，无需下载到本地
3. THE File_Preview_Service SHALL 通过集成 KKFileView 或类似开源预览服务实现 Office 文件转换预览
4. THE Template_Service SHALL 提供导入模板管理页面，支持上传/下载/删除 Excel 导入模板，每个模板关联一个业务模块（如"机械台账导入模板"）
5. THE Template_Service SHALL 提供导出模板管理页面，支持配置导出字段顺序、表头名称、数据格式
6. THE Template_Service SHALL 提供打印模板管理页面，支持 HTML 格式的打印模板编辑（使用简单的富文本编辑器），模板中可插入占位符变量
7. WHEN 用户在业务页面点击"打印"按钮时，THE PC_Frontend SHALL 根据打印模板填充业务数据并调用浏览器打印功能
8. THE Template_Service SHALL 为每个业务模块提供系统预设的默认模板，用户可基于默认模板自定义修改

---

### Requirement 5: 批量导入导出

**User Story:** 作为数据管理人员，我需要通过 Excel 文件批量导入机械台账、劳务花名册、人员信息等基础数据，以及批量导出业务数据用于线下分析和汇报。

#### Acceptance Criteria

1. THE Batch_Import_Service SHALL 支持以下数据的批量 Excel 导入：机械台账（MachineLedger）、劳务花名册（LaborRoster）、系统用户（SysUser）、供应商（Supplier）、材料字典（Material）
2. THE Batch_Import_Service SHALL 提供模板下载功能，用户下载标准模板后填入数据再上传
3. WHEN 用户上传 Excel 文件时，THE Batch_Import_Service SHALL 先执行数据校验（必填字段、格式校验、唯一性校验），校验不通过的行标记错误原因并在导入结果中展示
4. THE Batch_Import_Service SHALL 支持部分成功导入：校验通过的行正常导入，失败的行汇总展示错误明细（行号 + 错误原因），用户可下载错误报告
5. THE Batch_Import_Service SHALL 支持以下数据的批量 Excel 导出：机械台账、劳务花名册、人员信息、项目列表、材料库存、工资单
6. WHEN 导出数据量超过 5000 行时，THE Batch_Import_Service SHALL 使用异步导出模式，导出完成后通过站内消息通知用户下载
7. THE Batch_Import_Service SHALL 使用 EasyExcel 框架实现导入导出，确保大数据量场景下内存友好（流式读写）
8. THE PC_Frontend SHALL 在对应管理列表页面提供"导入"和"导出"按钮

---

### Requirement 6: 甘特图前端渲染

**User Story:** 作为项目管理人员，我需要通过甘特图可视化查看工程进度计划、任务依赖关系和实际完成进度，以便直观掌握项目整体推进情况。

#### Acceptance Criteria

1. THE Gantt_Component SHALL 基于 dhtmlx-gantt 库渲染工程进度甘特图，数据来源为后端 ScheduleController /plan 接口返回的树形进度计划
2. THE Gantt_Component SHALL 展示以下信息：任务名称、计划开始日期、计划结束日期、实际开始日期、实际完成百分比、任务层级（父子关系）
3. THE Gantt_Component SHALL 支持任务依赖关系展示（前置任务连线），通过 predecessors 字段定义任务间的完成-开始（FS）关系
4. THE Gantt_Component SHALL 支持以下交互操作：缩放时间轴（日/周/月视图切换）、展开/折叠任务层级、拖拽调整任务日期（需权限）、点击任务查看详情
5. WHEN 用户拖拽调整任务日期时，THE Gantt_Component SHALL 调用后端接口更新计划日期，并自动重新计算受影响的下游任务日期
6. THE Gantt_Component SHALL 通过颜色区分任务状态：未开始（灰色）、进行中（蓝色）、已完成（绿色）、已延期（红色）
7. THE Gantt_Component SHALL 支持关键路径高亮显示，标识影响项目总工期的关键任务链
8. THE PC_Frontend SHALL 在现场管理模块的"进度计划"页面集成甘特图组件，替换当前的表格式进度展示

---

### Requirement 7: 离线缓存/拍照水印/定位签到

**User Story:** 作为现场施工人员，我需要在网络信号不佳的施工现场也能使用移动端记录数据（离线缓存），拍照时自动添加时间和位置水印以确保真实性，以及通过 GPS 定位完成每日签到考勤。

#### Acceptance Criteria

1. THE Offline_Service SHALL 在移动端实现关键业务数据的离线缓存，包括：项目基本信息、材料字典、班组花名册、施工日志草稿
2. WHEN 移动端检测到网络不可用时，THE Offline_Service SHALL 自动切换为离线模式，用户提交的数据暂存本地（uni.setStorageSync），并在页面展示"离线模式"标识
3. WHEN 网络恢复后，THE Offline_Service SHALL 自动将本地暂存的数据同步至服务端，同步成功后清除本地缓存并通知用户
4. IF 离线数据同步时发生冲突（服务端数据已被他人修改），THEN THE Offline_Service SHALL 标记冲突记录并提示用户手动处理
5. THE Watermark_Service SHALL 在用户拍照时自动在照片底部叠加水印信息，包含：拍照时间（yyyy-MM-dd HH:mm:ss）、拍照人姓名、GPS 经纬度/地址、项目名称
6. THE Watermark_Service SHALL 确保水印不可被简单编辑移除（使用 Canvas 合成而非 EXIF 标记）
7. THE Location_Service SHALL 提供 GPS 定位签到功能，记录签到时间、签到位置（经纬度 + 逆地理编码地址）、签到人员
8. THE Location_Service SHALL 支持配置签到范围（项目工地坐标 + 允许偏差半径），IF 用户签到位置超出允许范围 THEN 标记为"范围外签到"并记录
9. THE Mobile_App SHALL 在"我的"页面展示本月签到记录日历视图（已签到日期标记为绿色）
10. THE Location_Service SHALL 将签到数据与考勤关联，可在 PC 端查看项目全员签到统计

---

## 依赖关系说明

| 功能 | 上游依赖 | 下游影响 |
|------|---------|---------|
| 移动端业务页面 | 后端财务 API（已就绪） | 无 |
| 移动端材料退货 | 后端 OutboundController（已就绪） | 库存数据 |
| 供应商门户 | 后端三方比价 API（已就绪） | 报价数据 |
| 文件预览 | MinIO 文件存储（已就绪） | 全系统附件预览 |
| 批量导入导出 | 各业务模块 Mapper | 基础数据管理 |
| 甘特图 | ScheduleController（已就绪） | 进度管理体验 |
| 离线/水印/签到 | GPS + Camera API | 考勤、施工日志 |

## 非功能性约束

1. THE File_Preview_Service SHALL 在 10 秒内完成 20MB 以下文件的预览转换
2. THE Batch_Import_Service SHALL 支持单次导入不超过 10000 行数据，导入时间不超过 30 秒
3. THE Gantt_Component SHALL 在 500 个任务节点以内保持流畅渲染（帧率 ≥ 30fps）
4. THE Offline_Service 离线缓存数据上限为 50MB，超出时提示用户清理
5. THE Watermark_Service 水印合成时间不超过 500ms/张
6. THE Location_Service GPS 定位精度要求 ≤ 50 米
7. THE Supplier_Portal 页面加载时间不超过 3 秒（首屏）
