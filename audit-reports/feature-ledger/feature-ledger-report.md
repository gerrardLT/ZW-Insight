# 功能深度账本报告

> 本文件由 `tools/feature-ledger` 脚本生成，**请勿手改**。
> 人工判断请编辑 `tools/feature-ledger/data/ledger-data.json` 的 manual 字段
> （levelFinal / gapNotes / benchmarkNote / roi），再重跑 `npm run dev -- report` 刷新本报告。

- 生成时间：2026/8/22 23:44:29
- 信号规则版本：v3
- 条目：133（PC 105 / 移动端 28）
- 人工复核进度：133 / 133（100%）

## 一、成熟度分布总览

| 等级 | 条目数 | 占比 |
|---|---:|---:|
| L0 缺失/占位 | 1 | 1% |
| L1 单路径CRUD | 55 | 41% |
| L2 规则完整 | 30 | 23% |
| L3 协同流转 | 40 | 30% |
| L4 数据智能 | 7 | 5% |

### 八维缺口计数（适用维度中信号为 0 的条目数）

| 维度 | PC 缺失 | 移动端缺失* |
|---|---:|---:|
| 效率(批量/导入导出) | 86/105 | 25/28 |
| 查询(组合筛选) | 13/105 | — |
| 状态(状态机) | 55/105 | — |
| 追溯(审计日志) | 87/105 | — |
| 通知(消息触达) | 104/105 | 28/28 |
| 权限(按钮级) | 76/105 | — |
| 异常(错误恢复) | 22/105 | 2/28 |
| 价值(聚合分析) | 98/105 | — |

\* 移动端仅评效率/异常/通知三维子集，其余维度不适用。

## 二、最基础功能 Top 20

按有效等级（levelFinal ?? levelAuto）升序，同分按信号总数升序（越少越基础）。

| # | 页面 | 分组/模块 | 等级 | 缺口 | 复核 |
|---:|---|---|---|---|:---:|
| 1 | 系统监控 | D-system | L0 | 效状通异值 | ✓ |
| 2 | 项目档案 | A-project | L1 | 效异通 | ✓ |
| 3 | 无权限 | D-403 | L1 | 效查状追通权异值 | ✓ |
| 4 | 页面不存在 | D-404 | L1 | 效查状追通权异值 | ✓ |
| 5 | 找回密码 | D-forgot-password | L1 | 效查状追通权异值 | ✓ |
| 6 | 登录 | D-login | L1 | 效查状追通权异值 | ✓ |
| 7 | 登录设备 | D-user | L1 | 效查状追通权值 | ✓ |
| 8 | 业务类型 | D-workflow | L1 | 效查状追通权值 | ✓ |
| 9 | 系统设置 | D-system | L1 | 效查状追通权值 | ✓ |
| 10 | 版本管理 | D-system | L1 | 效状追通异值 | ✓ |
| 11 | 工作台 | D-nav | L1 | 异通 | ✓ |
| 12 | 供应商黑名单 | D-basedata | L1 | 效状追通权值 | ✓ |
| 13 | 流程定义 | D-workflow | L1 | 查状追通权值 | ✓ |
| 14 | 菜单管理 | D-system | L1 | 效查状追通值 | ✓ |
| 15 | 我的审批 | D-workflow | L2→L1 | 通 | ✓ |
| 16 | 档案查询 | D-archive | L1 | 效状追通权值 | ✓ |
| 17 | 流程设计器 | D-workflow | L1 | 查状追通权值 | ✓ |
| 18 | 数据字典 | D-system | L1 | 效状追通值 | ✓ |
| 19 | 发票汇总 | C-finance | L1 | 效状追通权异值 | ✓ |
| 20 | 供应商评价 | D-basedata | L1 | 效状追通权值 | ✓ |

## 三、ROI 差距清单

按优先级 P0 → P1 → P2 → 未定，同级 Impact 降序、Effort 升序。

| 优先级 | 页面 | 等级 | Impact | Effort | 建议补齐 |
|---|---|---|:---:|:---:|---|
| P0 | 项目报备 | L3 | 5/5 | 2/5 | 效值 |
| P0 | 预算编制 | L3 | 5/5 | 3/5 | 效追值 |
| P0 | 施工合同 | L3 | 5/5 | 3/5 | 效值 |
| P0 | 工资单 | L3 | 5/5 | 3/5 | 效值 |
| P0 | 付款申请 | L3 | 5/5 | 3/5 | 值 |
| P0 | 质量安全检查 | L1 | 5/5 | 3/5 | 状 |
| P0 | 劳务花名册 | L2→L1 | 4/5 | 1/5 | 状 |
| P0 | 产值上报 | L3 | 4/5 | 2/5 | 效值 |
| P0 | 库存查询 | L1 | 4/5 | 2/5 | 值 |
| P0 | 材料入库 | L2 | 4/5 | 2/5 | 效 |
| P0 | 材料出库 | L2 | 4/5 | 2/5 | 效 |
| P0 | 回款登记 | L1 | 4/5 | 2/5 | 状值 |
| P0 | 检查详情 | L3→L1 | 4/5 | 2/5 | 状 |
| P0 | 质量检查 | L2 | 4/5 | 2/5 | 效 |
| P0 | 安全检查 | L2 | 4/5 | 2/5 | 效 |
| P0 | 工作台 | L1 | 4/5 | 2/5 | 异通 |
| P0 | 我的审批 | L2→L1 | 4/5 | 2/5 | 异效 |
| P0 | 目标成本变更 | L3 | 4/5 | 3/5 | 效追 |
| P0 | 劳务合同 | L3 | 4/5 | 3/5 | 效值 |
| P0 | 材料字典 | L1 | 3/5 | 1/5 | 效 |
| P1 | 质保金管理 | L3 | 4/5 | 1/5 | 状 |
| P1 | 项目详情 | L3 | 4/5 | 2/5 | 值 |
| P1 | 投标报名 | L3 | 4/5 | 2/5 | 值 |
| P1 | 领料出库 | L3 | 4/5 | 2/5 | 值 |
| P1 | 询价比价 | L3 | 4/5 | 2/5 | 值 |
| P1 | 分包合同 | L3 | 4/5 | 2/5 | 值 |
| P1 | 分包结算 | L3 | 4/5 | 2/5 | 值 |
| P1 | 开票申请 | L3 | 4/5 | 2/5 | 值 |
| P1 | 项目看板 | L4 | 4/5 | 2/5 | 查 |
| P1 | 审批管理 | L3 | 4/5 | 2/5 | 效 |
| P1 | BOQ 上传 | L3 | 4/5 | 3/5 | 值 |
| P1 | 到货入库 | L3 | 4/5 | 3/5 | 效追 |
| P1 | 采购结算 | L3 | 4/5 | 3/5 | 追 |
| P1 | 项目最终结算 | L3 | 4/5 | 3/5 | 值 |
| P1 | 进度计划 | L3 | 4/5 | 3/5 | 值 |
| P1 | 角色管理 | L1 | 4/5 | 3/5 | 权 |
| P1 | 项目档案 | L1 | 3/5 | 1/5 | 异 |
| P1 | 进度反馈 | L2 | 3/5 | 1/5 | — |
| P1 | 人员管理 | L1 | 3/5 | 1/5 | 效状 |
| P1 | 审批详情 | L2 | 3/5 | 1/5 | 效通 |
| P1 | 变更单表单 | L3 | 3/5 | 2/5 | 追 |
| P1 | 编辑合同 | L3 | 3/5 | 2/5 | 效异 |
| P1 | 用工单 | L3 | 3/5 | 2/5 | 值 |
| P1 | 机械合同 | L3 | 3/5 | 2/5 | 值 |
| P1 | 进出场登记 | L1 | 3/5 | 2/5 | 状 |
| P1 | 机械台账 | L1 | 3/5 | 2/5 | 效值 |
| P1 | 台班/工作量 | L1 | 3/5 | 2/5 | 效状 |
| P1 | 退货退款 | L2 | 3/5 | 2/5 | 状 |
| P1 | 采购合同 | L3 | 3/5 | 2/5 | 值 |
| P1 | 收票登记 | L1 | 3/5 | 2/5 | 效状 |
| P1 | 发票汇总 | L1 | 3/5 | 2/5 | 值 |
| P1 | 其他费用付款 | L2 | 3/5 | 2/5 | 状 |
| P1 | 施工日志 | L1 | 3/5 | 2/5 | 效状 |
| P1 | 编辑检查 | L1 | 3/5 | 2/5 | 效 |
| P1 | 施工日志 | L2 | 3/5 | 2/5 | 效 |
| P1 | 档案查询 | L1 | 3/5 | 2/5 | 效查 |
| P1 | 检查方案 | L1 | 3/5 | 2/5 | 效 |
| P1 | 供应商 | L1 | 3/5 | 2/5 | 效 |
| P2 | 编辑项目 | L3 | 3/5 | 1/5 | 异 |
| P2 | 薪资统计 | L4 | 3/5 | 1/5 | 值 |
| P2 | 付款申请 | L2 | 3/5 | 1/5 | — |
| P2 | 机械结算 | L4 | 3/5 | 2/5 | 追 |
| P2 | 材料调拨 | L3 | 3/5 | 2/5 | 追 |
| P2 | 个人报销 | L3 | 3/5 | 2/5 | 效 |
| P2 | 项目报销 | L3 | 3/5 | 2/5 | 值 |
| P2 | 备用金管理 | L3 | 3/5 | 2/5 | 状 |
| P2 | 首页 | L4 | 3/5 | 2/5 | 值 |
| P2 | 收票登记 | L2 | 3/5 | 3/5 | 效 |
| P2 | 打印模板 | L1 | 3/5 | 4/5 | 值 |
| P2 | 流程设计器 | L1 | 3/5 | 4/5 | 值 |
| P2 | 预算控制配置 | L1 | 2/5 | 1/5 | 状 |
| P2 | 证件管理 | L1 | 2/5 | 1/5 | 效 |
| P2 | 班组管理 | L1 | 2/5 | 1/5 | 效 |
| P2 | 故障维修 | L1 | 2/5 | 1/5 | 状 |
| P2 | 新建结算单 | L4 | 2/5 | 1/5 | — |
| P2 | 结算单详情 | L4 | 2/5 | 1/5 | — |
| P2 | 材料退货 | L2 | 2/5 | 1/5 | — |
| P2 | 财务封账 | L2 | 2/5 | 1/5 | 追 |
| P2 | 结算单详情 | L3 | 2/5 | 1/5 | — |
| P2 | 开票申请 | L2 | 2/5 | 1/5 | — |
| P2 | 其他付款 | L2 | 2/5 | 1/5 | — |
| P2 | 回款登记 | L2 | 2/5 | 1/5 | — |
| P2 | 个人报销 | L2 | 2/5 | 1/5 | — |
| P2 | 项目报销 | L2 | 2/5 | 1/5 | — |
| P2 | 备用金申请 | L2 | 2/5 | 1/5 | — |
| P2 | 入职申请 | L3 | 2/5 | 1/5 | — |
| P2 | 离职申请 | L3 | 2/5 | 1/5 | — |
| P2 | 人事统计 | L4 | 2/5 | 1/5 | 查 |
| P2 | 检查方案详情 | L2 | 2/5 | 1/5 | — |
| P2 | 供应商黑名单 | L1 | 2/5 | 1/5 | 效状追通权值 |
| P2 | 登录 | L1 | 2/5 | 1/5 | 异 |
| P2 | 公告管理 | L3 | 2/5 | 1/5 | 效追通权值 |
| P2 | 消息中心 | L1 | 2/5 | 1/5 | 状 |
| P2 | 通知管理 | L3 | 2/5 | 1/5 | 效追通值 |
| P2 | 信息中心 | L2 | 2/5 | 1/5 | 通 |
| P2 | 首页 | L2 | 2/5 | 1/5 | 效通 |
| P2 | 日志管理 | L1 | 2/5 | 1/5 | 查效 |
| P2 | 流程定义 | L1 | 2/5 | 1/5 | 查状追通权值 |
| P2 | 审批回滚 | L3 | 2/5 | 1/5 | 效追通权异值 |
| P2 | 供应商评价 | L1 | 2/5 | 2/5 | 值 |
| P2 | 租户管理 | L2 | 2/5 | 2/5 | 状 |
| P2 | 数据备份 | L1 | 2/5 | 2/5 | 状 |
| P2 | 系统监控 | L0 | 2/5 | 2/5 | 值 |
| P2 | 税率管理 | L2 | 1/5 | 1/5 | — |
| P2 | 备用金归还 | L2 | 1/5 | 1/5 | — |
| P2 | 办公用品 | L3 | 1/5 | 1/5 | — |
| P2 | 车辆管理 | L3 | 1/5 | 1/5 | — |
| P2 | 无权限 | L1 | 1/5 | 1/5 | 效查状追通权异值 |
| P2 | 页面不存在 | L1 | 1/5 | 1/5 | 效查状追通权异值 |
| P2 | 办公用品档案 | L1 | 1/5 | 1/5 | 效状追通权异值 |
| P2 | 其它支出合同档案 | L1 | 1/5 | 1/5 | 效状追通权异值 |
| P2 | 其它收入合同档案 | L1 | 1/5 | 1/5 | 效状追通权异值 |
| P2 | 自持公司 | L1 | 1/5 | 1/5 | 效状追通权值 |
| P2 | 甲方单位 | L1 | 1/5 | 1/5 | 效状追通权值 |
| P2 | 找回密码 | L1 | 1/5 | 1/5 | 效查状追通权异值 |
| P2 | 找回密码 | L2 | 1/5 | 1/5 | 效通 |
| P2 | 登录 | L2 | 1/5 | 1/5 | 效通 |
| P2 | 推送渠道配置 | L1 | 1/5 | 1/5 | 效状追通权值 |
| P2 | 我的 | L2 | 1/5 | 1/5 | 效通 |
| P2 | 修改密码 | L2 | 1/5 | 1/5 | 效通 |
| P2 | 编辑快捷入口 | L2 | 1/5 | 1/5 | 效通 |
| P2 | 存储管理 | L1 | 1/5 | 1/5 | 效状通值 |
| P2 | 用户类型 | L1 | 1/5 | 1/5 | 效状追通权值 |
| P2 | 系统设置 | L1 | 1/5 | 1/5 | 效查状追通权值 |
| P2 | 数据字典 | L1 | 1/5 | 1/5 | 效状追通值 |
| P2 | 菜单管理 | L1 | 1/5 | 1/5 | 效查状追通值 |
| P2 | 机构管理 | L1 | 1/5 | 1/5 | 效状追通值 |
| P2 | 岗位管理 | L1 | 1/5 | 1/5 | 效状追通值 |
| P2 | 编号规则管理 | L1 | 1/5 | 1/5 | 效状通值 |
| P2 | 模板管理 | L1 | 1/5 | 1/5 | 状通值 |
| P2 | 版本管理 | L1 | 1/5 | 1/5 | 效状追通异值 |
| P2 | 登录设备 | L1 | 1/5 | 1/5 | 效查状追通权值 |
| P2 | 业务类型 | L1 | 1/5 | 1/5 | 效查状追通权值 |

## 四、分组明细

缺口列单字：效=批量/导入导出，查=组合筛选，状=状态机，追=审计日志，通=消息触达，
权=按钮级权限，异=错误恢复，值=聚合分析。「人工」列=该条目含任意人工判断字段，内容见 JSON。

### A 核心主链（14 条）

| 页面 | 路由 | 模块 | 等级 | 置信度 | 缺口 | 人工 |
|---|---|---|---|:---:|---|:---:|
| 目标成本变更 | /budget/change | budget | L3 | high | 效追通值 | ✓ |
| 变更单表单 | /budget/change/form | budget | L3 | high | 效追通值 | ✓ |
| 预算控制配置 | /budget/control-config | budget | L1 | high | 效状追通值 | ✓ |
| 预算编制 | /budget/list | budget | L3 | high | 效追通值 | ✓ |
| BOQ 上传 | /contract/boq/:contractId | contract | L3 | high | 通值 | ✓ |
| 编辑合同 | /contract/edit/:id | contract | L3 | high | 效通异值 | ✓ |
| 施工合同 | /contract/list | contract | L3 | high | 通值 | ✓ |
| 产值上报 | /contract/output-report | contract | L3 | high | 效通值 | ✓ |
| 项目详情 | /project/detail/:id | project | L3 | high | 效通权异值 | ✓ |
| 编辑项目 | /project/edit/:id | project | L3 | high | 效通权异值 | ✓ |
| 项目报备 | /project/list | project | L3 | high | 通权值 | ✓ |
| 项目档案 | pages/project/archive | project | L1 | ⚠ | 效异通 | ✓ |
| 证件管理 | /tender/certificate | tender | L1 | high | 效状追通权值 | ✓ |
| 投标报名 | /tender/register | tender | L3 | high | 效追通权值 | ✓ |

### B 支出域（27 条）

| 页面 | 路由 | 模块 | 等级 | 置信度 | 缺口 | 人工 |
|---|---|---|---|:---:|---|:---:|
| 劳务合同 | /labor/contract | labor | L3 | high | 追通权值 | ✓ |
| 工资单 | /labor/payroll | labor | L3 | high | 追通权值 | ✓ |
| 劳务花名册 | /labor/roster | labor | L2→L1 | high | 追通权值 | ✓ |
| 薪资统计 | /labor/salary-stats | labor | L4 | high | 状追通权 | ✓ |
| 班组管理 | /labor/team | labor | L1 | high | 效状追通权值 | ✓ |
| 用工单 | /labor/work-order | labor | L3 | high | 效追通权值 | ✓ |
| 机械合同 | /machine/contract | machine | L3 | high | 效追通权值 | ✓ |
| 进出场登记 | /machine/entry | machine | L1 | high | 效状追通权值 | ✓ |
| 机械台账 | /machine/ledger | machine | L1 | high | 状追通权值 | ✓ |
| 故障维修 | /machine/repair | machine | L1 | high | 效状追通权值 | ✓ |
| 机械结算 | /machine/settlement | machine | L4 | high | 追通权 | ✓ |
| 新建结算单 | /machine/settlement/create | machine | L4 | high | 效追通权异 | ✓ |
| 结算单详情 | /machine/settlement/detail/:id | machine | L4 | high | 追通权 | ✓ |
| 台班/工作量 | /machine/work-log | machine | L1 | high | 效状追通权值 | ✓ |
| 到货入库 | /material/inbound | material | L3 | high | 效追通权值 | ✓ |
| 领料出库 | /material/outbound | material | L3 | high | 效追通权值 | ✓ |
| 退货退款 | /material/refund | material | L2 | high | 效追通权异值 | ✓ |
| 库存查询 | /material/stock | material | L1 | high | 状通值 | ✓ |
| 材料调拨 | /material/transfer | material | L3 | high | 效追通权值 | ✓ |
| 材料入库 | pages/material/inbound | material | L2 | high | 效通 | ✓ |
| 材料出库 | pages/material/outbound | material | L2 | high | 效通 | ✓ |
| 材料退货 | pages/material/return | material | L2 | high | 效通 | ✓ |
| 采购合同 | /purchase/contract | purchase | L3 | high | 效追通权值 | ✓ |
| 询价比价 | /purchase/inquiry | purchase | L3 | high | 效追通权值 | ✓ |
| 采购结算 | /purchase/settlement | purchase | L3 | high | 效追通权值 | ✓ |
| 分包合同 | /subcontract/contract | subcontract | L3 | high | 效追通权值 | ✓ |
| 分包结算 | /subcontract/settlement | subcontract | L3 | high | 效追通权值 | ✓ |

### C 财务现场域（38 条）

| 页面 | 路由 | 模块 | 等级 | 置信度 | 缺口 | 人工 |
|---|---|---|---|:---:|---|:---:|
| 财务封账 | /finance/finance-lock | finance | L2 | high | 效追通值 | ✓ |
| 开票申请 | /finance/invoice-apply | finance | L3 | high | 效通值 | ✓ |
| 收票登记 | /finance/invoice-received | finance | L1 | high | 效状追通权异值 | ✓ |
| 发票汇总 | /finance/invoice-summary | finance | L1 | high | 效状追通权异值 | ✓ |
| 其他费用付款 | /finance/other-payment | finance | L2 | high | 效追通权异值 | ✓ |
| 付款申请 | /finance/payment-apply | finance | L3 | high | 效通值 | ✓ |
| 回款登记 | /finance/payment-received | finance | L1 | high | 效状通值 | ✓ |
| 个人报销 | /finance/personal-reimbursement | finance | L3 | high | 效追通权值 | ✓ |
| 项目报销 | /finance/project-reimbursement | finance | L3 | high | 效追通权值 | ✓ |
| 备用金管理 | /finance/reserve-fund | finance | L3 | high | 效追通权值 | ✓ |
| 质保金管理 | /finance/retention | finance | L3 | high | 效追通权异值 | ✓ |
| 项目最终结算 | /finance/settlement | finance | L3 | high | 追通权值 | ✓ |
| 结算单详情 | /finance/settlement/:id | finance | L3 | high | 追通权值 | ✓ |
| 税率管理 | /finance/tax-rate | finance | L2 | high | 效查追通值 | ✓ |
| 开票申请 | pages/finance/invoice-apply | finance | L2 | high | 效通 | ✓ |
| 收票登记 | pages/finance/invoice-received | finance | L2 | high | 效通 | ✓ |
| 其他付款 | pages/finance/other-payment | finance | L2 | high | 效通 | ✓ |
| 付款申请 | pages/finance/payment-apply | finance | L2 | high | 效通 | ✓ |
| 回款登记 | pages/finance/payment-received | finance | L2 | high | 效通 | ✓ |
| 个人报销 | pages/finance/personal-reimbursement | finance | L2 | high | 效通 | ✓ |
| 项目报销 | pages/finance/reimbursement | finance | L2 | high | 效通 | ✓ |
| 备用金申请 | pages/finance/reserve-fund-apply | finance | L2 | high | 效通 | ✓ |
| 备用金归还 | pages/finance/reserve-fund-return | finance | L2 | high | 效通 | ✓ |
| 入职申请 | /hr/entry | hr | L3 | high | 效追通权值 | ✓ |
| 办公用品 | /hr/office-supply | hr | L3 | high | 效追通权值 | ✓ |
| 离职申请 | /hr/resign-apply | hr | L3 | high | 效追通权值 | ✓ |
| 人事统计 | /hr/statistics | hr | L4 | ⚠ | 效查状追通权 | ✓ |
| 车辆管理 | /hr/vehicle | hr | L3 | high | 效追通权值 | ✓ |
| 施工日志 | /site/construction-log | site | L1 | high | 效状追通权值 | ✓ |
| 质量安全检查 | /site/inspection | site | L1 | high | 效状追通权值 | ✓ |
| 检查详情 | /site/inspection/detail/:id | site | L3→L1 | high | 效追通权值 | ✓ |
| 编辑检查 | /site/inspection/form/:id | site | L1 | high | 效状追通权值 | ✓ |
| 进度计划 | /site/schedule | site | L3 | high | 效追通权值 | ✓ |
| 施工日志 | pages/site/construction-log | site | L2 | high | 效通 | ✓ |
| 检查方案详情 | pages/site/inspection-detail | site | L2 | high | 效通 | ✓ |
| 进度反馈 | pages/site/progress-feedback | site | L2 | high | 效通 | ✓ |
| 质量检查 | pages/site/quality-check | site | L2 | high | 效通 | ✓ |
| 安全检查 | pages/site/safety-check | site | L2 | high | 效通 | ✓ |

### D 平台支撑域（54 条）

| 页面 | 路由 | 模块 | 等级 | 置信度 | 缺口 | 人工 |
|---|---|---|---|:---:|---|:---:|
| 无权限 | /403 | 403 | L1 | ⚠ | 效查状追通权异值 | ✓ |
| 页面不存在 | /404 | 404 | L1 | ⚠ | 效查状追通权异值 | ✓ |
| 档案查询 | /archive/index | archive | L1 | high | 效状追通权值 | ✓ |
| 办公用品档案 | /archive/office-supply | archive | L1 | high | 效状追通权异值 | ✓ |
| 其它支出合同档案 | /archive/other-expense-contract | archive | L1 | high | 效状追通权异值 | ✓ |
| 其它收入合同档案 | /archive/other-income-contract | archive | L1 | high | 效状追通权异值 | ✓ |
| 自持公司 | /basedata/company | basedata | L1 | high | 效状追通权值 | ✓ |
| 检查方案 | /basedata/inspection-scheme | basedata | L1 | high | 效状追通权值 | ✓ |
| 材料字典 | /basedata/material | basedata | L1 | high | 状追通权值 | ✓ |
| 甲方单位 | /basedata/owner | basedata | L1 | high | 效状追通权值 | ✓ |
| 供应商 | /basedata/supplier | basedata | L1 | high | 效状追通权值 | ✓ |
| 供应商黑名单 | /basedata/supplier-blacklist | basedata | L1 | high | 效状追通权值 | ✓ |
| 供应商评价 | /basedata/supplier-evaluation | basedata | L1 | high | 效状追通权值 | ✓ |
| 首页 | /dashboard | dashboard | L4 | high | 效状追通权 | ✓ |
| 找回密码 | /forgot-password | forgot-password | L1 | ⚠ | 效查状追通权异值 | ✓ |
| 登录 | /login | login | L1 | ⚠ | 效查状追通权异值 | ✓ |
| 找回密码 | pages/login/forgot-password | login | L2 | high | 效通 | ✓ |
| 登录 | pages/login/index | login | L2 | high | 效通 | ✓ |
| 公告管理 | /message/announcement | message | L3 | high | 效追通权值 | ✓ |
| 消息中心 | /message/center | message | L1 | high | 效状追权异值 | ✓ |
| 通知管理 | /message/notice | message | L3 | high | 效追通值 | ✓ |
| 推送渠道配置 | /message/push-config | message | L1 | high | 效状追通权值 | ✓ |
| 信息中心 | pages/message-center/index | message | L2 | high | 通 | ✓ |
| 首页 | pages/home/index | nav | L2 | high | 效通 | ✓ |
| 我的 | pages/mine/index | nav | L2 | high | 效通 | ✓ |
| 修改密码 | pages/mine/password | nav | L2 | high | 效通 | ✓ |
| 编辑快捷入口 | pages/mine/shortcut-edit | nav | L2 | high | 效通 | ✓ |
| 工作台 | pages/workbench/index | nav | L1 | high | 异通 | ✓ |
| 存储管理 | /platform/storage | platform | L1 | ⚠ | 效状通值 | ✓ |
| 租户管理 | /platform/tenant | platform | L2 | high | 效追通值 | ✓ |
| 用户类型 | /platform/tenant-type | platform | L1 | high | 效状追通权值 | ✓ |
| 项目看板 | /project-dashboard | project-dashboard | L4 | ⚠ | 效查状追通权异 | ✓ |
| 数据备份 | /system/backup | system | L1 | high | 效状追通值 | ✓ |
| 系统设置 | /system/config | system | L1 | high | 效查状追通权值 | ✓ |
| 数据字典 | /system/dict | system | L1 | high | 效状追通值 | ✓ |
| 日志管理 | /system/log | system | L1 | high | 效状追通权异值 | ✓ |
| 菜单管理 | /system/menu | system | L1 | high | 效查状追通值 | ✓ |
| 系统监控 | /system/monitor | system | L0 | ⚠ | 效状通异值 | ✓ |
| 机构管理 | /system/org | system | L1 | high | 效状追通值 | ✓ |
| 岗位管理 | /system/post | system | L1 | high | 效状追通值 | ✓ |
| 打印模板 | /system/print-template | system | L1 | ⚠ | 效状通值 | ✓ |
| 角色管理 | /system/role | system | L1 | high | 效状通值 | ✓ |
| 编号规则管理 | /system/serial-number | system | L1 | ⚠ | 效状通值 | ✓ |
| 模板管理 | /system/template | system | L1 | ⚠ | 状通值 | ✓ |
| 人员管理 | /system/user | system | L1 | high | 状通值 | ✓ |
| 版本管理 | /system/version | system | L1 | high | 效状追通异值 | ✓ |
| 登录设备 | /user/devices | user | L1 | ⚠ | 效查状追通权值 | ✓ |
| 审批管理 | /workflow/approval | workflow | L3 | high | 追通权值 | ✓ |
| 业务类型 | /workflow/business-type | workflow | L1 | high | 效查状追通权值 | ✓ |
| 流程设计器 | /workflow/designer | workflow | L1 | high | 查状追通权值 | ✓ |
| 流程定义 | /workflow/process | workflow | L1 | high | 查状追通权值 | ✓ |
| 审批回滚 | /workflow/rollback | workflow | L3 | high | 效追通权异值 | ✓ |
| 审批详情 | pages/approval/detail | workflow | L2 | high | 效通 | ✓ |
| 我的审批 | pages/approval/index | workflow | L2→L1 | high | 通 | ✓ |

## 附录

### 清单扫描警告

- 清单差异: views 有文件但无路由: zw-insight-web/src/views/project/components/ProjectMember.vue
- PC 功能页数 105 与基准 104 不符（前端路由可能已增删，属正常演进，请复核清单差异）

### 已下线页面（removed，保留历史判断）

无
