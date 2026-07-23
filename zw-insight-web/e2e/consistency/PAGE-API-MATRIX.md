# 页面-接口一致性测试矩阵（PAGE-API-MATRIX）

> 前端展示 vs 后端数据 字段级一致性测试的施工图。
> 来源：`src/router/index.ts`（路由）+ `src/api/*.ts`（接口）+ `deploy/db-init/31_V2026_26__seed_demo_data.sql`（种子数据）。
> 目标服务器：前端 `http://129.204.3.200:18081`，后端 `http://129.204.3.200:18080`，账号 admin/123456。

## 模板说明

- **A 列表页**：抓 `.../page` 响应 → 表格行数=records 长度、每列值=字段（格式化后）、枚举翻译、金额/日期格式、分页 total。
- **B 详情页**：抓 `.../{id}` 响应 → descriptions/表单字段值=响应字段、明细子表行数=子数组长度。
- **C 编辑表单回显**：抓 `.../{id}` → input/select value=响应字段、下拉选项来自真实接口。
- **D 统计/看板**：抓聚合接口 → 卡片数字=聚合值、图表点数=数组长度。

## 种子数据覆盖结论（缺口核查）

种子脚本 `31_V2026_26` 覆盖 **120+ 业务表**（id 90000-99999，tenant_id=1），核心与支持模块基本全覆盖。关键覆盖表：
biz_project / biz_construction_contract / biz_boq_item / biz_budget(+detail) / biz_budget_change / biz_purchase_contract(+settlement) / biz_labor_contract / biz_team / biz_labor_roster / biz_work_order / biz_labor_payroll / biz_machine_contract(+ledger/entry/work_log/repair/settlement) / biz_subcontract(+settlement) / biz_material_inbound/outbound/transfer/refund/inventory / biz_invoice_apply / biz_payment_received/apply / biz_retention_money / biz_reserve_fund_apply / biz_schedule_plan / biz_construction_log / biz_inspection / biz_tender_register / biz_office_supply / biz_vehicle / biz_entry_apply / biz_resign_apply / bd_material/company/supplier/owner / sys_org/user/role/menu/dict / msg_announcement / wf_business_type 等。

**数据状态标注**：✅=种子已覆盖可直读比对；⚠️=种子未直接覆盖，用例内先建 tenant_id=9999 数据再比对；—=纯配置/无列表数据页。

---

## Phase 1 — 核心业务模块

### project 项目管理（4 页）
| 路由 | view | 主接口 | 模板 | 种子表 | 数据 |
|---|---|---|---|---|---|
| /project/list | project/index.vue | GET /v1/project/page | A | biz_project | ✅ |
| /project/detail/:id | project/detail.vue | GET /v1/project/{id} | B | biz_project(+member) | ✅ |
| /project/edit/:id | project/form.vue | GET /v1/project/{id} | C | biz_project | ✅ |
| /project/create | project/form.vue | POST /v1/project | C(建) | — | 建9999 |

### contract 合同管理（4 页）
| 路由 | view | 主接口 | 模板 | 种子表 | 数据 |
|---|---|---|---|---|---|
| /contract/list | contract/index.vue | GET /v1/contract/page | A | biz_construction_contract | ✅ |
| /contract/create,edit | contract/form.vue | GET /v1/contract/{id} | B/C | biz_contract_detail | ✅ |
| /contract/boq/:contractId | contract/boq-upload.vue | GET /v1/contract/quantity | A | biz_boq_item | ✅ |

### budget 预算管理（5 页）
| 路由 | view | 主接口 | 模板 | 种子表 | 数据 |
|---|---|---|---|---|---|
| /budget/list | budget/index.vue | GET /v1/budget/page | A | biz_budget(+detail) | ✅ |
| /budget/change | budget/change/index.vue | GET /v1/budget/change/page | A | biz_budget_change | ✅ |
| /budget/change/form | budget/change/form.vue | GET /v1/budget/change/trace | B/C | biz_budget_change_detail | ✅ |
| /budget/config | budget/config.vue | GET /v1/budget/config/list | A | biz_cost_subcategory | ✅ |
| /budget/control-config | budget/control-config/index.vue | GET /v1/budget-control-configs | A | — | ⚠️ |

### finance 财务管理（16 页）
| 路由 | view | 主接口 | 模板 | 种子表 | 数据 |
|---|---|---|---|---|---|
| /finance/invoice-apply | finance/invoice-apply.vue | GET /v1/finance/invoice-apply/page | A | biz_invoice_apply | ✅ |
| /finance/invoice-received | finance/invoice-received.vue | GET /v1/finance/invoice-received | A | biz_invoice_received | ✅ |
| /finance/invoice-summary | finance/invoice-summary.vue | GET /v1/finance/invoice-summary | D | — | ✅ |
| /finance/payment-received | finance/payment-received.vue | GET /v1/finance/payment-received/page | A | biz_payment_received | ✅ |
| /finance/payment-apply | finance/payment-apply.vue | GET /v1/finance/payment-apply/page | A | biz_payment_apply | ✅ |
| /finance/other-payment | finance/other-payment.vue | GET /v1/finance/other-payment | A | biz_other_payment | ✅ |
| /finance/project-reimbursement | finance/project-reimbursement.vue | GET /v1/finance/project-reimbursement | A | biz_project_reimbursement | ✅ |
| /finance/reserve-fund | finance/reserve-fund.vue | GET /v1/finance/reserve-fund/apply | A | biz_reserve_fund_apply | ✅ |
| /finance/personal-reimbursement | finance/personal-reimbursement.vue | GET /v1/finance/personal-reimbursement | A | biz_personal_reimbursement | ✅ |
| /finance/retention | finance/retention.vue | GET /v1/finance/retention/page | A | biz_retention_money | ✅ |
| /finance/settlement | finance/settlement/index.vue | GET /v1/project-settlements | A | biz_project_settlement | ✅ |
| /finance/settlement/:id | finance/settlement/detail.vue | GET /v1/project-settlements/{id} | B | biz_settlement_contract_detail | ✅ |
| /finance/finance-lock | finance/finance-lock/index.vue | GET /v1/finance/lock/... | A | biz_finance_lock | ✅ |
| /finance/tax-rate | finance/tax-rate/index.vue | GET /v1/finance/tax-rate/list | A | — | ⚠️ |

### purchase 采购管理（3 页）
| 路由 | view | 主接口 | 模板 | 种子表 | 数据 |
|---|---|---|---|---|---|
| /purchase/contract | purchase/contract.vue | GET /v1/purchase/contract/page | A | biz_purchase_contract | ✅ |
| /purchase/settlement | purchase/settlement.vue | GET /v1/purchase/settlement/page | A | biz_purchase_settlement | ✅ |
| /purchase/inquiry | purchase/inquiry.vue | GET /v1/purchase/inquiry/page | A | biz_inquiry(+supplier/item) | ✅ |

### labor 劳务管理（6 页）
| 路由 | view | 主接口 | 模板 | 种子表 | 数据 |
|---|---|---|---|---|---|
| /labor/contract | labor/contract.vue | GET /v1/labor/contract/page | A | biz_labor_contract | ✅ |
| /labor/team | labor/team.vue | GET /v1/labor/team/page | A | biz_team | ✅ |
| /labor/roster | labor/roster.vue | GET /v1/labor/roster/page | A | biz_labor_roster | ✅ |
| /labor/work-order | labor/work-order.vue | GET /v1/labor/work-order/page | A | biz_work_order | ✅ |
| /labor/payroll | labor/payroll.vue | GET /v1/labor/payroll/page | A | biz_labor_payroll | ✅ |
| /labor/salary-stats | labor/salary/stats.vue | GET /v1/labor/salary/stats | D | biz_labor_payroll | ✅ |

### material 材料库存（5 页）
| 路由 | view | 主接口 | 模板 | 种子表 | 数据 |
|---|---|---|---|---|---|
| /material/inbound | material/inbound.vue | GET /v1/material/inbound/page | A | biz_material_inbound(+detail) | ✅ |
| /material/outbound | material/outbound.vue | GET /v1/material/outbound/page | A | biz_material_outbound(+detail) | ✅ |
| /material/transfer | material/transfer.vue | GET /v1/material/transfer/page | A | biz_material_transfer | ✅ |
| /material/stock | material/stock.vue | GET /v1/material/stock/page | A | biz_project_material_stock/inventory | ✅ |
| /material/refund | material/refund.vue | GET /v1/material/refund | A | biz_material_refund | ✅ |

### machine 机械管理（8 页）
| 路由 | view | 主接口 | 模板 | 种子表 | 数据 |
|---|---|---|---|---|---|
| /machine/contract | machine/contract.vue | GET /v1/machine/contract/page | A | biz_machine_contract | ✅ |
| /machine/ledger | machine/ledger.vue | GET /v1/machine/ledger/page | A | biz_machine_ledger | ✅ |
| /machine/entry | machine/entry.vue | GET /v1/machine/entry/page | A | biz_machine_entry | ✅ |
| /machine/work-log | machine/work-log.vue | GET /v1/machine/work-log/page | A | biz_machine_work_log | ✅ |
| /machine/repair | machine/repair.vue | GET /v1/machine/repair/page | A | biz_machine_repair | ✅ |
| /machine/settlement | machine/settlement/index.vue | GET /v1/machine/work-settlement/... | A | biz_machine_work_settlement | ✅ |
| /machine/settlement/detail/:id | machine/settlement/detail.vue | GET /v1/machine/work-settlement/{id} | B | biz_machine_work_settlement_detail | ✅ |

### subcontract 分包管理（2 页）
| 路由 | view | 主接口 | 模板 | 种子表 | 数据 |
|---|---|---|---|---|---|
| /subcontract/contract | subcontract/contract.vue | GET /v1/subcontract/contract/page | A | biz_subcontract | ✅ |
| /subcontract/settlement | subcontract/settlement.vue | GET /v1/subcontract/settlement | A | biz_subcontract_settlement | ✅ |

---

## Phase 2 — 业务支持模块

### site 现场管理（6 页）
| 路由 | view | 主接口 | 模板 | 种子表 | 数据 |
|---|---|---|---|---|---|
| /site/schedule | site/schedule.vue | GET /v1/site/schedule/page | A | biz_schedule_plan | ✅ |
| /site/construction-log | site/construction-log.vue | GET /v1/site/construction-log/page | A | biz_construction_log | ✅ |
| /site/inspection | site/inspection/index.vue | GET /v1/site/inspection/page | A | biz_inspection(+detail) | ✅ |
| /site/inspection/detail/:id | site/inspection/detail.vue | GET /v1/site/inspection/{id} | B | biz_rectification | ✅ |

### tender 投标管理（2 页）
| 路由 | view | 主接口 | 模板 | 种子表 | 数据 |
|---|---|---|---|---|---|
| /tender/register | tender/register.vue | GET /v1/tender/register/page | A | biz_tender_register | ✅ |
| /tender/certificate | tender/certificate.vue | GET /v1/tender/... certificate | A | biz_person/company_certificate | ✅ |

### hr 行政人事（5 页）
| 路由 | view | 主接口 | 模板 | 种子表 | 数据 |
|---|---|---|---|---|---|
| /hr/statistics | hr/statistics.vue | GET /v1/hr/statistics/overview | D | biz_entry_apply 等 | ✅ |
| /hr/entry | hr/entry.vue | GET /v1/hr/entry-apply/page | A | biz_entry_apply | ✅ |
| /hr/office-supply | hr/office-supply.vue | GET /v1/hr/office-supply/page | A | biz_office_supply | ✅ |
| /hr/vehicle | hr/vehicle.vue | GET /v1/hr/vehicle/page | A | biz_vehicle | ✅ |
| /hr/resign-apply | hr/resign-apply.vue | GET /v1/hr/resign-apply | A | biz_resign_apply | ✅ |

### archive 档案管理（4 页）
| 路由 | view | 主接口 | 模板 | 种子表 | 数据 |
|---|---|---|---|---|---|
| /archive/index | archive/index.vue | GET /v1/archive/... | A | file_info | ✅ |
| /archive/other-income-contract | archive/other-income-contract.vue | GET /v1/archive/other-income-contract | A | biz_other_contract | ✅ |
| /archive/other-expense-contract | archive/other-expense-contract.vue | GET /v1/archive/other-expense-contract | A | biz_expense_contract | ✅ |
| /archive/office-supply | archive/office-supply.vue | GET /v1/archive/office-supply | A | biz_office_supply | ✅ |

### basedata 基础数据（7 页）
| 路由 | view | 主接口 | 模板 | 种子表 | 数据 |
|---|---|---|---|---|---|
| /basedata/material | basedata/material.vue | GET /v1/basedata/material/page | A | bd_material | ✅ |
| /basedata/supplier | basedata/supplier.vue | GET /v1/basedata/supplier/page | A | bd_supplier | ✅ |
| /basedata/owner | basedata/owner.vue | GET /v1/basedata/owner/page | A | bd_owner | ✅ |
| /basedata/company | basedata/company.vue | GET /v1/basedata/company/page | A | bd_company | ✅ |
| /basedata/inspection-scheme | basedata/inspection-scheme.vue | GET /v1/basedata/inspection-scheme/page | A | bd_inspection_scheme | ✅ |
| /basedata/supplier-evaluation | basedata/supplier-evaluation.vue | GET /v1/basedata/supplier-evaluation | A | biz_supplier_evaluation | ✅ |
| /basedata/supplier-blacklist | basedata/supplier-blacklist.vue | GET /v1/basedata/supplier-blacklist | A | biz_supplier_blacklist | ✅ |

### dashboard 看板（2 页）
| 路由 | view | 主接口 | 模板 | 种子表 | 数据 |
|---|---|---|---|---|---|
| /dashboard | dashboard/index.vue | GET /v1/dashboard/company-overview 等 | D | 聚合 | ✅ |
| /project-dashboard | dashboard/project-dashboard.vue | GET /v1/dashboard/budget-execution 等 | D | 聚合 | ✅ |

---

## Phase 3 — 平台与系统模块

### system 系统管理（14 页）
| 路由 | view | 主接口 | 模板 | 种子表 | 数据 |
|---|---|---|---|---|---|
| /system/org | system/org/index.vue | GET /v1/system/org | A(树) | sys_org | ✅ |
| /system/user | system/user/index.vue | GET /v1/system/user | A | sys_user | ✅ |
| /system/role | system/role/index.vue | GET /v1/system/role | A | sys_role | ✅ |
| /system/menu | system/menu/index.vue | GET /v1/system/menu | A(树) | sys_menu | ✅ |
| /system/dict | system/dict/index.vue | GET /v1/system/dict | A | sys_dict(+item) | ✅ |
| /system/post | system/post/index.vue | GET /v1/system/post | A | sys_post | ✅ |
| /system/config | system/config/index.vue | GET /v1/system/config | A | sys_config | — |
| /system/template | system/template/index.vue | GET /v1/message/template | A | — | ⚠️ |
| /system/print-template | system/print-template/index.vue | GET /v1/print-template/list | A | — | ⚠️ |
| /system/log | system/log/index.vue | GET /v1/system/log/oper | A | 运行日志 | ✅ |
| /system/serial-number | system/serial-number/index.vue | GET /v1/... serial | A | serial_number_rule | ✅ |
| /system/backup | system/backup/index.vue | GET /v1/system/backup/list | A | — | ⚠️ |
| /system/version | system/version/index.vue | GET /v1/system/version/list | A | — | ⚠️ |
| /system/monitor | system/monitor/index.vue | GET /v1/system/monitor/metrics | D | — | ✅ |

### platform 平台管理（3 页）
| 路由 | view | 主接口 | 模板 | 种子表 | 数据 |
|---|---|---|---|---|---|
| /platform/tenant | platform/tenant/index.vue | GET /v1/platform/tenant | A | sys_tenant | ✅ |
| /platform/tenant-type | platform/tenant-type/index.vue | GET /v1/platform/tenant-type | A | — | ⚠️ |
| /platform/storage | platform/storage/index.vue | GET /v1/platform/storage/... | A | — | ⚠️ |

### workflow 工作流（5 页）
| 路由 | view | 主接口 | 模板 | 种子表 | 数据 |
|---|---|---|---|---|---|
| /workflow/designer | workflow/designer/index.vue | GET /v1/workflow/process | A | — | ⚠️ |
| /workflow/process | workflow/process/index.vue | GET /v1/workflow/process | A | — | ⚠️ |
| /workflow/business-type | workflow/business-type/index.vue | GET /v1/workflow/business-type/tree | A(树) | wf_business_type | ✅ |
| /workflow/approval | workflow/approval/index.vue | GET /v1/workflow/approval/todo | A | 运行流程 | ⚠️ |
| /workflow/rollback | workflow/rollback/index.vue | GET /v1/workflow/rollback/logs | A | — | ⚠️ |

### message 消息管理（4 页）
| 路由 | view | 主接口 | 模板 | 种子表 | 数据 |
|---|---|---|---|---|---|
| /message/notice | message/notice/index.vue | GET /v1/message/notice | A | msg_message | ✅ |
| /message/announcement | message/announcement/index.vue | GET /v1/message/announcement | A | msg_announcement | ✅ |
| /message/push-config | message/push-config/index.vue | GET /v1/message/push-config | A | — | ⚠️ |
| /message/center | message/center/index.vue | GET /v1/message/msg/all | A | msg_message | ✅ |

### user 个人中心（1 页）
| 路由 | view | 主接口 | 模板 | 种子表 | 数据 |
|---|---|---|---|---|---|
| /user/devices | user/devices.vue | GET /v1/... device | A | 登录设备 | ⚠️ |

---

## 备注

- 部分详情接口路径（`/{id}`）在写各模块 spec 时以实际 view 调用为准补全。
- ⚠️ 标注的页面在对应 spec 中先用 tenant_id=9999 建数据再比对，`afterAll` 清理。
- 每列的字段映射（ColumnSpec.index/field/type/enumMap）在各模块 spec 内定义，读对应 view 的 `<el-table-column>` 顺序确定 index。
