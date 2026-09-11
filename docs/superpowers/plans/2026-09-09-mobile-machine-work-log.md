# 移动端机械台班现场上报实现计划 (Mobile Machine Work Log Implementation Plan)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为中维智营移动端新增机械台班现场上报功能，支持工长现场选择项目与设备，记录运转工时、工程量与油耗，打通离线提交并在工作台与路由中闭环展示。

**Architecture:** 前端基于 uni-app + Vue 3 (Composition API) + Pinia，创建台班日志列表与新增表单，对接后端 `zw-machine` 模块现有 `MachineWorkLogController` 与 `MachineLedgerController`，融入离线入队机制（`submitOrQueue`）。

**Tech Stack:** uni-app, Vue 3, TypeScript, Pinia, Vitest, Happy-DOM.

---

### Task 1: API 层扩展 (api/common.ts)
- [ ] 导出 `getMachineLedgerPage` (拉取设备台账)
- [ ] 导出 `getMachineWorkLogPage` (拉取台班日志)
- [ ] 导出 `saveMachineWorkLog` (提交台班日志)

### Task 2: 机械台班填报页面 (pages/machine/work-log/create.vue)
- [ ] 项目选择与联动设备选择（从 `getMachineLedgerPage` 选取）
- [ ] 工作日期、台班数（1-3个台班）、工程量、耗油量与备注表单项
- [ ] 接入 `submitOrQueue` 支持施工现场离线入队提交
- [ ] 提交成功后返回列表并给出 Toast 提示

### Task 3: 机械台班记录列表页面 (pages/machine/work-log/index.vue)
- [ ] 顶部项目与状态筛选
- [ ] 支持下拉刷新与触底分页加载历史日志
- [ ] 卡片展示机械名称、台班数、工程量、耗油量及日期
- [ ] 右下角悬浮按钮（FAB）一键跳转登记填报

### Task 4: 路由与入口打通 (pages.json, workbench, db-init)
- [ ] 在 `pages.json` 注册两页面路由
- [ ] 在 `pages/workbench/index.vue` 的常用现场业务中补充「机械台班」入口
- [ ] 在 `deploy/db-init` 中补充快捷方式 SQL 配置

### Task 5: 单元测试与完整性验证
- [ ] 创建 `tests/pages/machine-work-log.test.ts` 覆盖列表加载、表单校验、离线入队与空状态
- [ ] 运行 `npm run test` 确保 100% 绿灯通过
- [ ] 运行一致性审计 `consistency-audit`
