# 移动端（zw-insight-app H5）全页面截图

- **截取时间**：2026-08-29
- **环境**：本地 `npm run dev:h5`（http://localhost:5173，iPhone 13 视口 390×844，fullPage），代理直连真实联调服务器 `http://129.204.3.200:18080`
- **登录**：真实 `/api/v1/auth/login`（admin，验证码图片识别方案获取真实验证码答案；SSH 读 Redis 通道当日被服务器拒连，故采用验证码识别）；token 注入 H5 localStorage
- **数据**：全部真实接口数据（租户 1 演示种子 + 存量数据）；详情页参数来自真实查询（审批 taskId 取自 `/v1/workflow/approval/todo`；检查详情 id=98031、项目档案 projectId=90001 取自真实列表）

## 截图清单（30 张，覆盖 pages.json 全部 29 个路由 + 短信登录态）

| 文件 | 页面 | 渲染状态 |
|------|------|----------|
| 01-login-password | 登录（密码） | ✅ 正常 |
| 02-login-sms | 登录（短信验证码） | ✅ 正常 |
| 03-login-forgot-password | 找回密码 | ✅ 正常 |
| 04-home | 首页（Tab） | ✅ 真实数据（项目 38 / 合同 13200 万等）；常用功能区为空（见发现 8） |
| 05-workbench | 工作台（Tab） | ✅ 真实数据（待审批 153）；"申请人"为空（见发现 4） |
| 06-approval | 我的审批（Tab） | ✅ 真实待办列表；"申请人"为空（见发现 4） |
| 07-mine | 我的（Tab） | ⚠️ 显示"未登录"（见发现 7，token 注入场景下 userInfo 未持久化） |
| 08-approval-detail | 审批详情 | ❌ "网络错误"（见发现 3，调用不存在的接口） |
| 09-material-inbound | 材料入库 | ✅ 表单正常 |
| 10-material-outbound | 材料出库 | ✅ 表单正常 |
| 11-material-return | 材料退货 | ✅ 表单正常 |
| 12-site-construction-log | 施工日志 | ✅ 表单正常 |
| 13-site-progress-feedback | 进度反馈 | ✅ 表单正常 |
| 14-site-quality-check | 质量检查 | ✅ 表单正常 |
| 15-site-safety-check | 安全检查 | ✅ 表单正常 |
| 16-site-inspection-detail | 检查方案详情 | ⚠️ 整改闭环真实数据渲染；该种子记录未关联方案（页面如实提示） |
| 17-finance-invoice-apply | 开票申请 | ✅ 表单正常 |
| 18-finance-invoice-received | 收票登记 | ✅ 表单正常 |
| 19-finance-other-payment | 其他付款 | ✅ 表单正常 |
| 20-finance-personal-reimbursement | 个人报销 | ✅ 表单正常 |
| 21-finance-reserve-fund-apply | 备用金申请 | ✅ 表单正常 |
| 22-finance-reserve-fund-return | 备用金归还 | ✅ 表单正常 |
| 23-finance-payment-received | 回款登记 | ✅ 表单正常 |
| 24-finance-payment-apply | 付款申请 | ✅ 表单正常 |
| 25-finance-reimbursement | 项目报销 | ✅ 表单正常 |
| 26-project-archive | 项目档案 | ⚠️ 框架渲染但字段全空（见发现 6，前后端字段结构错位） |
| 27-mine-password | 修改密码 | ✅ 正常 |
| 28-mine-shortcut-edit | 编辑快捷入口 | ✅ 正常 |
| 29-mine-sign | 定位签到 | ✅ 正常 |
| 30-message-center | 信息中心 | ✅ 真实公告数据 |

## 本次发现的前后端一致性缺陷（均已接口/代码双重实证）

1. **【已修复】H5 代理目标不一致**：`zw-insight-app/src/manifest.json` 的 `h5.devServer.proxy./api.target` 原为 `http://localhost:8080`（本地无服务），覆盖 `vite.config.ts` 中已迁移的联调目标，导致 H5 dev 环境所有 API 500。已改为 `http://129.204.3.200:18080` 与 vite.config.ts 对齐。
2. **移动端登录缺验证码输入**：服务器 `auth.captcha-enabled=true`（实证：无验证码登录返回 400「请输入验证码」+验证码图），而 `pages/login/index.vue` 密码/短信表单均无验证码字段 → 移动端 UI 登录必然失败。需移动端补验证码 UI 或后端对移动端通道豁免。
3. **审批详情调用不存在的接口**：`pages/approval/detail.vue` 调 `GET /v1/workflow/approval/detail/{taskId}`，后端 `ApprovalController` 无此端点（PC 端亦无）→ 详情页恒「网络错误」。
4. **审批列表"申请人"恒空**：前端读 `item.startUserName`，后端 todo/done 返回字段为 `taskId/taskName/assignee/createTime/...`，无 `startUserName`。
5. **雪花 ID 精度丢失**：`pages/site/inspection-detail.vue`（`Number(options.id)`）与 `pages/project/archive.vue`（`Number(options.projectId)`）对 19 位雪花 ID 转 Number 丢精度 → 真实业务记录详情加载失败（实证 id=2091552243927019522 失败、98031 正常）。应全程字符串传参。
6. **项目档案字段错位**：后端 `/v1/archive/project/{id}` 返回嵌套 VO（`data.project`/`fundSummary`/各类列表），前端按顶层 `projectName/finance/progress` 读取 → 页面全空。
7. **"我的"页用户信息不持久**：`userInfo` 仅存 pinia 内存（登录时 set），不持久化、启动不重取 → 有效 token 冷启动后显示「未登录」。
8. **首页快捷入口默认项缺失**：`GET /v1/message/shortcut` 对未配置用户返回 `[]`，与前端注释「未配置时后端返回系统默认项」不符。
