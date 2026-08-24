# Implementation Plan: P2 体验增强

## Overview

本实现计划覆盖 P2 优先级的 7 个体验增强功能。任务按独立性分为 4 个波次，大部分任务可并行执行。

## Task Dependency Graph

```json
{
  "waves": [
    {
      "name": "Wave 1: 基础设施",
      "tasks": [1, 2]
    },
    {
      "name": "Wave 2: 独立功能模块",
      "tasks": [3, 4, 5, 6, 7, 8]
    },
    {
      "name": "Wave 3: 前端集成",
      "tasks": [9, 10]
    },
    {
      "name": "Wave 4: 移动端增强",
      "tasks": [11, 12]
    }
  ]
}
```

## Tasks

- [x] 1. 数据库迁移 + Docker 服务配置
  - Requirements: R3, R4, R5, R7
  - Dependencies: None
  - Description: 创建新增表的 SQL 迁移脚本，配置 KKFileView Docker 服务
  - Sub-tasks:
    1. [x] CREATE TABLE `sys_supplier_account`（供应商账户）
    2. [x] CREATE TABLE `sys_template`（模板管理）
    3. [x] CREATE TABLE `biz_sign_record`（签到记录）
    4. [x] docker-compose.yml 新增 KKFileView 服务配置
    5. [x] INSERT 初始化系统导入模板数据（机械台账/花名册/人员/供应商/材料）

- [x] 2. 批量导入导出后端框架
  - Requirements: R5
  - Dependencies: Task 1
  - Description: 实现 EasyExcel 批量导入导出通用框架 + 异步导出机制
  - Sub-tasks:
    1. [x] 创建 `BatchImportExportService` 接口和实现
    2. [x] 实现通用导入流程：文件上传 → 校验 → 部分成功模式 → 错误报告生成
    3. [x] 实现异步导出：@Async + Redis 状态跟踪 + MinIO 文件存储
    4. [x] 实现机械台账导入（MachineLedgerImportListener）
    5. [x] 实现劳务花名册导入（LaborRosterImportListener）
    6. [x] 实现人员信息导入（SysUserImportListener）
    7. [x] 实现供应商导入（SupplierImportListener）
    8. [x] 实现材料字典导入（MaterialImportListener）
    9. [x] 实现各模块导出（机械台账/花名册/人员/项目/库存/工资单）
    10. [x] 创建 `BatchImportExportController` REST API
    11. [x] 模板下载接口

- [x] 3. 文件预览服务
  - Requirements: R4 (AC 1-3)
  - Dependencies: Task 1
  - Description: 实现文件在线预览，集成 KKFileView
  - Sub-tasks:
    1. [x] 创建 `FilePreviewController` 提供预览 URL 生成接口
    2. [x] 实现 MinIO 临时访问 URL 生成（presigned URL, 有效期 30 分钟）
    3. [x] 实现预览 URL 编码转换（base64 编码传给 KKFileView）
    4. [x] PC 前端附件列表增加"预览"按钮，点击在新标签页打开
    5. [x] 支持图片直接预览（jpg/png 不走 KKFileView，直接展示）

- [x] 4. 模板管理后端
  - Requirements: R4 (AC 4-8)
  - Dependencies: Task 1
  - Description: 实现导入/导出/打印模板的 CRUD 管理
  - Sub-tasks:
    1. [x] 创建 `SysTemplate` 实体类 + Mapper
    2. [x] 实现 `TemplateService`：CRUD + 按模块查询 + 默认模板
    3. [x] 创建 `TemplateController` REST API
    4. [x] 实现打印模板 HTML 变量替换引擎
    5. [x] 初始化预设导入模板文件（上传到 MinIO）

- [x] 5. 供应商门户后端
  - Requirements: R3
  - Dependencies: Task 1
  - Description: 实现供应商独立认证和报价接口
  - Sub-tasks:
    1. [x] 创建 `SysSupplierAccount` 实体类 + Mapper
    2. [x] 实现供应商短信验证码发送（复用已有 SMS 配置或 stub）
    3. [x] 实现供应商验证码登录 + JWT 颁发（独立 token，非主系统 token）
    4. [x] 实现询价列表查询（仅返回当前供应商被邀请的询价）
    5. [x] 实现报价提交（含截止时间校验）
    6. [x] 实现"我的报价记录"查询
    7. [x] 创建 `SupplierPortalController` REST API
    8. [x] 供应商门户专用 JWT 过滤器（独立于主系统认证链）

- [x] 6. 甘特图组件开发
  - Requirements: R6
  - Dependencies: None
  - Description: 基于 dhtmlx-gantt 实现工程进度甘特图 Vue 组件
  - Sub-tasks:
    1. [x] 创建 `src/components/GanttChart.vue` 组件
    2. [x] 实现后端数据 → dhtmlx-gantt 数据格式转换
    3. [x] 实现时间轴缩放（日/周/月视图切换）
    4. [x] 实现任务依赖关系连线渲染
    5. [x] 实现颜色状态区分（未开始/进行中/已完成/已延期）
    6. [x] 实现关键路径高亮
    7. [x] 实现拖拽编辑 + 保存至后端
    8. [x] 集成到现场管理进度计划页面

- [x] 7. 签到后端服务
  - Requirements: R7 (AC 7-10)
  - Dependencies: Task 1
  - Description: 实现 GPS 定位签到后端服务
  - Sub-tasks:
    1. [x] 创建 `BizSignRecord` 实体类 + Mapper
    2. [x] 实现 `LocationSignService`：签到 + 范围校验 + 月度统计
    3. [x] 创建 `SignController` REST API（POST /sign, GET /records, GET /monthly）
    4. [x] 实现项目签到范围配置接口（中心坐标 + 半径）
    5. [x] PC 端签到统计查询页面后端接口


- [x] 8. 供应商门户前端
  - Requirements: R3
  - Dependencies: Task 5
  - Description: 创建供应商门户独立 Vue 3 SPA 前端应用
  - Sub-tasks:
    1. [x] 初始化 `zw-supplier-portal/` 项目（Vite + Vue 3 + TypeScript + Tailwind CSS）
    2. [x] 实现登录页面（手机号 + 验证码）
    3. [x] 实现询价列表页面
    4. [x] 实现询价详情 + 报价表单页面
    5. [x] 实现"我的报价记录"页面
    6. [x] 实现 JWT token 管理和路由守卫
    7. [x] 配置代理和部署（nginx 反向代理到后端 API）

- [x] 9. PC 端文件预览/模板/导入导出集成
  - Requirements: R4, R5
  - Dependencies: Task 2, Task 3, Task 4
  - Description: 在 PC 端各管理页面集成文件预览、模板管理和导入导出按钮
  - Sub-tasks:
    1. [x] 创建 `src/views/system/template/index.vue` 模板管理页面
    2. [x] 在附件展示组件中增加"预览"按钮（调用预览 URL）
    3. [x] 在机械台账、花名册、人员、供应商、材料列表页添加"导入/导出"按钮
    4. [x] 实现导入弹窗组件（上传文件 → 展示校验结果 → 确认导入）
    5. [x] 实现异步导出进度弹窗（轮询状态 → 完成后下载）
    6. [x] 创建打印模板编辑页面（富文本 + 变量插入）
    7. [x] 注册路由 `/system/template`

- [x] 10. PC 端甘特图页面集成
  - Requirements: R6
  - Dependencies: Task 6
  - Description: 将甘特图组件集成到现场管理进度计划页面
  - Sub-tasks:
    1. [x] 替换 `src/views/site/schedule/index.vue` 中的表格为甘特图
    2. [x] 实现视图切换（表格视图 / 甘特图视图）
    3. [x] 实现进度反馈后甘特图自动刷新
    4. [x] 实现甘特图导出为图片功能

- [x] 11. 移动端业务页面 + 材料退货
  - Requirements: R1, R2
  - Dependencies: None
  - Description: 在 uni-app 移动端新增 6 个业务页面
  - Sub-tasks:
    1. [x] 创建 `pages/finance/invoice-received` 收票登记页面
    2. [x] 创建 `pages/finance/other-payment` 其他费用付款页面
    3. [x] 创建 `pages/finance/reserve-fund-apply` 备用金申请页面
    4. [x] 创建 `pages/finance/reserve-fund-return` 备用金归还页面
    5. [x] 创建 `pages/finance/personal-reimbursement` 个人报销页面
    6. [x] 创建 `pages/material/return` 材料退货页面
    7. [x] 每个页面实现表单、附件上传、提交调用后端 API
    8. [x] 在 pages.json 注册新页面路由
    9. [x] 在工作台页面添加新功能入口

- [x] 12. 移动端离线缓存/水印/签到
  - Requirements: R7
  - Dependencies: Task 7
  - Description: 实现移动端离线数据缓存、拍照水印和 GPS 签到功能
  - Sub-tasks:
    1. [x] 实现离线检测工具类（uni.getNetworkType 监听）
    2. [x] 实现离线数据缓存层（拦截 API 请求，无网时存本地）
    3. [x] 实现网络恢复后自动同步 + 冲突提示
    4. [x] 实现水印合成工具（Canvas 绘制：时间 + 姓名 + GPS + 项目名）
    5. [x] 改造拍照组件，拍照后自动叠加水印
    6. [x] 创建 `pages/mine/sign` 签到页面
    7. [x] 实现 GPS 定位获取 + 范围校验 + 签到提交
    8. [x] 实现签到记录日历视图（本月打卡日历）
    9. [x] 在 pages.json 注册签到页面

## Notes

- Wave 2 中所有任务相互独立，可按团队分工并行开发
- 移动端任务（Task 11, 12）不依赖后端新开发（API 已就绪），可以立即开始
- 供应商门户是独立项目，与主系统共享后端 API（独立认证链）
- 甘特图是纯前端任务，不需要后端改动（数据 API 已就绪）
- 文件预览依赖 KKFileView Docker 部署，建议 Task 1 优先完成

## 完成状态回填说明（2026-08-24）

本 spec 各任务实际已于 2026 年 7 月交付，但勾选框一直未维护。2026-08-24 深度盘点时逐项对照实际代码核验后统一回填：

- Task 1：`deploy/db-init/11_V2026_08__p2_experience_enhancement.sql`（sys_supplier_account/sys_template/biz_sign_record 建表）+ `docker-compose.yml` kkfileview:4.4.0-beta 服务
- Task 2：`BatchImportExportService/Impl/Controller` + 各 ImportListener（含 SupplierImportListener）+ 单测
- Task 3：`FilePreviewController/Service/Config`（图片 presigned 直连/非图片 KKFileView base64）+ 单测
- Task 4：`SysTemplate` 实体 + `TemplateController/Service` + `PrintTemplateController/Service`（Thymeleaf 变量替换）+ 单测
- Task 5/8：`zw-supplier-portal/` 独立 SPA（Login/InquiryList/InquiryDetail/MyQuotations）+ 后端独立认证链（`SupplierSecurityContext`/`SupplierPortalWebMvcConfig`，zw-purchase 模块）；p1-business-completion 已在其基础上扩展公开询价接口
- Task 6/10：`zw-insight-web/src/components/GanttChart.vue` + 现场进度页集成
- Task 7：`LocationSignService`/`SignController`（Haversine 围栏 + Redis 配置 + 日历/月度统计）+ 单测
- Task 9：`views/system/template/index.vue` 等页面集成
- Task 11：移动端 finance 6 页（invoice-received/other-payment/reserve-fund-apply/reserve-fund-return/personal-reimbursement）+ `material/return.vue`
- Task 12：`offlineCache.ts`/`offlineData.ts`/`OfflineBanner.vue` + `watermark.ts`/`watermarkCompositor.ts` + `sign.vue`

佐证：test-maturity-upgrade 批 5（zw-file 8 测试类含 FilePreview/BatchImportExport/PrintTemplate/Template）与 full-layer-test-suite「supplier-portal 补基础测试」均基于上述已交付代码编写并通过 CI。
