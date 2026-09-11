# 移动端视觉与体验全面工业化升级设计规范 (Industrial Precision Mobile Spec)

- **日期**：2026-09-11
- **目标平台**：Uni-app Vue 3 (`zw-insight-app`，支持 H5、小程序与 App 构建)
- **视觉基准**：工业精密美学（Industrial Precision，对齐 `docs/DESIGN-mix-industrial-precision.md`）

---

## 1. 升级背景与核心目标

PC 端已全量落地工程工业精密风格，通过安全橙（`#ff6b00`）、直角纪律（2px）、设备铭牌、等宽数字（`tabular-nums`）与图签蓝图角标构建了专业、硬朗的工业工程管理视觉。
移动端（`zw-insight-app`）虽已引入部分基础 token，但 38 个业务页面中仍大量存在圆角不一（8px/12px）、缺乏视觉层级、金额与计量未等宽对齐、内联色彩冗杂等问题。

本次升级目标：
1. **统一设计语言**：全量 38 个业务页面全面推行直角纪律、工程名牌、Tabular 等宽数字与胶囊徽章。
2. **沉淀移动端基础微组件**：提供 `ZwMoney`（等宽千分位货币）与 `ZwStatusBadge`（工业状态胶囊）。
3. **扩展移动端签名类（`signature.css`）**：沉淀直角表单输入组、设备名牌、卡片角标与现场警示条纹类。
4. **零破坏与质量门禁**：保持 100% 真实接口与离线同步机制，确保既有 23 个测试套件、170 项 Vitest 单元测试全部全绿通过，H5 编译构建零错误。

---

## 2. 核心设计规范与 Token 映射

### 2.1 基础 Token 约束
- **主品牌色**：`var(--zw-brand)` (`#ff6b00`，Construction Safety Orange)，橙底必须配深字 `var(--zw-on-primary)` (`#14161a`)。
- **页面背景与表面阶梯**：
  - 画布底色：`var(--zw-bg-page)` (`#f6f7f5`，亮色) / (`#101214`，暗色)
  - 卡片表面：`var(--zw-bg-card)` (`#ffffff`，亮色) / (`#16181c`，暗色)
  - 悬浮表面：`var(--zw-bg-elevated)`
- **直角纪律**：
  - 按钮、输入框、表单控件：`var(--zw-radius-xs)` (2px)
  - 普通业务卡片：`var(--zw-radius-sm)` (4px)
  - 唯一胶囊圆角：`var(--zw-radius-pill)` (9999px)，仅用于 `status-badge`
- **等宽排版（Tabular Figures）**：
  - 所有金额、工时、设备编号、台班数、百分比必须使用 `var(--zw-font-mono)` 且开启 `font-variant-numeric: tabular-nums`。

### 2.2 扩展签名工具类（`src/styles/signature.css`）
- `.plate-header`：页面级或主区块设备铭牌头部，左侧带有 3px 品牌安全橙垂直标识线，包含大写窄体等宽眉题（`.page-eyebrow`）。
- `.card-corner-marked`：工程图签蓝图角标，左上角与右下角带 1px 直角标线。
- `.hazard-divider`：45° 黑黄工业警示条纹（用于安全质检告警或离线提示）。
- `.zw-form-group` / `.zw-input`：44px 触控高度、2px 直角边框、聚焦高亮安全橙、等宽数字输入适配。
- `.zw-list-card`：工业卡片项，左侧状态彩色竖标，右侧等宽数据排版。

---

## 3. 全量页面集群改造规划

全量 38 个页面划分为四大集群，协同升级：

### 集群 1：核心枢纽（四大 Tab + 账户中心）
1. `pages/home/index.vue`：首页蓝图角标卡片、快捷入口工业网格、消息中心时间线。
2. `pages/workbench/index.vue`：项目看板卡片等宽化、现场高频业务方块直角化。
3. `pages/approval/index.vue`：待办/已办/我发起 Tab 工业下划线、批量审批操作栏直角化、状态胶囊徽章。
4. `pages/approval/detail.vue`：流程明细时间轴、表单审查清单直角化。
5. `pages/mine/index.vue`：石墨黑头部名牌、精密菜单列表。
6. `pages/mine/sign.vue`：定位签到精密坐标打卡面板。
7. `pages/mine/password.vue`：修改密码表单规范。
8. `pages/mine/shortcut-edit.vue`：快捷入口拖拽编辑网格。
9. `pages/message-center/index.vue`：信息中心分类与消息列表。

### 集群 2：现场施工与履约业务
10. `pages/machine/work-log/index.vue`：机械台班明细、工时统计。
11. `pages/machine/work-log/create.vue`：机械台班填报直角表单。
12. `pages/labor/work-order/index.vue`：劳务点工签认单据列表。
13. `pages/labor/work-order/create.vue`：点工工时签认精密表单。
14. `pages/contract/change-event/index.vue`：变更事件卡片与状态条。
15. `pages/contract/change-event/create.vue`：变更事件填报。
16. `pages/contract/change-event/detail.vue`：变更事件多维度详情。
17. `pages/site/construction-log.vue`：施工日志每日记录。
18. `pages/site/progress-feedback.vue`：进度反馈填报与进度条行走。
19. `pages/site/quality-check.vue`：质量检查整改卡片（含警示条纹）。
20. `pages/site/safety-check.vue`：安全检查隐患卡片（含警示条纹）。
21. `pages/site/inspection-detail.vue`：检查方案设备名牌详情。
22. `pages/site/watermark-camera/index.vue`：工程水印相机十字刻度与蓝图坐标。

### 集群 3：物资仓储流转
23. `pages/material/inbound.vue`：材料入库清单、扫描与步进器。
24. `pages/material/outbound.vue`：材料出库单据与物料明细。
25. `pages/material/return.vue`：材料退货申请。

### 集群 4：财务、成本与项目档案
26. `pages/project/cost-control/index.vue`：成本控制看板、三色告警（正常/警告/严重）等宽数字矩阵。
27. `pages/project/archive.vue`：项目全生命周期档案名牌。
28. `pages/finance/invoice-apply.vue`：开票申请表单。
29. `pages/finance/invoice-received.vue`：收票登记表单。
30. `pages/finance/other-payment.vue`：其他付款申请。
31. `pages/finance/personal-reimbursement.vue`：个人报销表单。
32. `pages/finance/reserve-fund-apply.vue`：备用金申请。
33. `pages/finance/reserve-fund-return.vue`：备用金归还。
34. `pages/finance/payment-received.vue`：回款登记与金额等宽展示。
35. `pages/finance/payment-apply.vue`：付款申请与供应商信息。
36. `pages/finance/reimbursement.vue`：项目报销多明细卡片。
37. `pages/login/index.vue`：登录页石墨控制室画布、大写眉题与直角按钮。
38. `pages/login/forgot-password.vue`：找回密码步骤条。

---

## 4. 质量门禁与验证策略

1. **测试用例守护**：
   - 保留所有测试断言所需的类名选择器（`.stat-value`、`.task-item`、`.checkbox`、`.batch-btn` 等）。
   - 每次集群改造完成后，执行对应的页面单测。
2. **全量回归基准**：
   - `npm test`：全部 23 个测试文件、170+ 项单元测试 100% 通过。
   - `npm run build:h5`：构建编译成功，无类型与打包报错。
