# 前后端联调-核心错位清单 (HTTP_METHOD_MISMATCH + FRONTEND_EXTRA_API)

生成时间: 2026-06-30T04:10:15.471Z（基于 audit-report-2026-06-30T04-10-15）

> 基线 63 项 → 当前 54 项（阶段一 system + basedata 归类 A 修复 9 项）

## basedata (2)

- [HTTP_METHOD_MISMATCH] PC前端使用 PUT 请求 /v1/basedata/supplier-evaluation，但后端声明为 POST ★归类B待排期
- [FRONTEND_EXTRA_API] PC前端接口 DELETE /v1/basedata/supplier-evaluation/{id}（deleteSupplierEvaluation）在后端无对应 Controller 方法 ★归类B待排期

## budget (6)

- [HTTP_METHOD_MISMATCH] PC前端使用 PUT 请求 /v1/budget，但后端声明为 POST
- [HTTP_METHOD_MISMATCH] PC前端使用 DELETE 请求 /v1/budget/{id}，但后端声明为 POST
- [HTTP_METHOD_MISMATCH] PC前端使用 PUT 请求 /v1/budget/change，但后端声明为 GET
- [HTTP_METHOD_MISMATCH] PC前端使用 PUT 请求 /v1/budget/change/{id}/submit，但后端声明为 POST
- [HTTP_METHOD_MISMATCH] PC前端使用 PUT 请求 /v1/budget/config，但后端声明为 GET
- [HTTP_METHOD_MISMATCH] PC前端使用 DELETE 请求 /v1/budget/config/{id}，但后端声明为 PUT

## contract (7)

- [HTTP_METHOD_MISMATCH] PC前端使用 PUT 请求 /v1/contract，但后端声明为 POST
- [HTTP_METHOD_MISMATCH] PC前端使用 DELETE 请求 /v1/contract/{id}，但后端声明为 POST
- [HTTP_METHOD_MISMATCH] PC前端使用 POST 请求 /v1/contract/{contractId}/details，但后端声明为 DELETE
- [FRONTEND_EXTRA_API] PC前端接口 GET /v1/contract/change-visa/page（getChangeVisaPage）在后端无对应 Controller 方法
- [HTTP_METHOD_MISMATCH] PC前端使用 GET 请求 /v1/contract/quantity/page，但后端声明为 DELETE
- [FRONTEND_EXTRA_API] PC前端接口 GET /v1/contract/settlement/page（getFinalSettlementPage）在后端无对应 Controller 方法
- [FRONTEND_EXTRA_API] PC前端接口 GET /v1/contract/output/page（getOutputReportPage）在后端无对应 Controller 方法

## hr (14)

- [FRONTEND_EXTRA_API] PC前端接口 GET /v1/hr/regular/page（getHrRegularPage）在后端无对应 Controller 方法
- [FRONTEND_EXTRA_API] PC前端接口 POST /v1/hr/regular（createHrRegular）在后端无对应 Controller 方法
- [FRONTEND_EXTRA_API] PC前端接口 PUT /v1/hr/regular/{data.id}（updateHrRegular）在后端无对应 Controller 方法
- [FRONTEND_EXTRA_API] PC前端接口 DELETE /v1/hr/regular/{id}（deleteHrRegular）在后端无对应 Controller 方法
- [FRONTEND_EXTRA_API] PC前端接口 GET /v1/hr/transfer/page（getHrTransferPage）在后端无对应 Controller 方法
- [FRONTEND_EXTRA_API] PC前端接口 POST /v1/hr/transfer（createHrTransfer）在后端无对应 Controller 方法
- [FRONTEND_EXTRA_API] PC前端接口 PUT /v1/hr/transfer/{data.id}（updateHrTransfer）在后端无对应 Controller 方法
- [FRONTEND_EXTRA_API] PC前端接口 DELETE /v1/hr/transfer/{id}（deleteHrTransfer）在后端无对应 Controller 方法
- [FRONTEND_EXTRA_API] PC前端接口 GET /v1/hr/seal-apply/page（getSealApplyPage）在后端无对应 Controller 方法
- [FRONTEND_EXTRA_API] PC前端接口 PUT /v1/hr/seal-apply/{data.id}（updateSealApply）在后端无对应 Controller 方法
- [FRONTEND_EXTRA_API] PC前端接口 DELETE /v1/hr/seal-apply/{id}（deleteSealApply）在后端无对应 Controller 方法
- [HTTP_METHOD_MISMATCH] PC前端使用 DELETE 请求 /v1/hr/office-supply/{id}，但后端声明为 POST
- [FRONTEND_EXTRA_API] PC前端接口 PUT /v1/hr/office-supply/{id}/submit（submitOfficeSupply）在后端无对应 Controller 方法
- [HTTP_METHOD_MISMATCH] PC前端使用 DELETE 请求 /v1/hr/vehicle/{id}，但后端声明为 POST

## machine (3)

- [FRONTEND_EXTRA_API] PC前端接口 POST /v1/machine/repair（createMachineRepair）在后端无对应 Controller 方法
- [HTTP_METHOD_MISMATCH] PC前端使用 PUT 请求 /v1/machine/repair/{data.id}，但后端声明为 POST
- [HTTP_METHOD_MISMATCH] PC前端使用 DELETE 请求 /v1/machine/repair/{id}，但后端声明为 POST

## project (1)

- [HTTP_METHOD_MISMATCH] PC前端使用 PUT 请求 /v1/project，但后端声明为 POST

## site (7)

- [HTTP_METHOD_MISMATCH] PC前端使用 GET 请求 /v1/site/schedule/plan，但后端声明为 POST
- [HTTP_METHOD_MISMATCH] PC前端使用 DELETE 请求 /v1/site/schedule/{id}，但后端声明为 POST
- [HTTP_METHOD_MISMATCH] PC前端使用 PUT 请求 /v1/site/inspection/{data.id}，但后端声明为 GET
- [HTTP_METHOD_MISMATCH] PC前端使用 DELETE 请求 /v1/site/inspection/{id}，但后端声明为 GET
- [HTTP_METHOD_MISMATCH] PC前端使用 PUT 请求 /v1/site/inspection/{data.id}，但后端声明为 GET
- [HTTP_METHOD_MISMATCH] PC前端使用 DELETE 请求 /v1/site/inspection/{id}，但后端声明为 GET
- [FRONTEND_EXTRA_API] 移动端接口 POST /v1/site/inspection/{id}/results（submitInspectionResults）在后端无对应 Controller 方法

## subcontract (1)

- [HTTP_METHOD_MISMATCH] PC前端使用 PUT 请求 /v1/subcontract/settlement/{id}/submit，但后端声明为 POST

## system (2)

- [HTTP_METHOD_MISMATCH] PC前端使用 GET 请求 /v1/system/role/{roleId}/menus，但后端声明为 PUT ★归类B待排期
- [FRONTEND_EXTRA_API] PC前端接口 PUT /v1/system/post/{id}/status（updatePostStatus）在后端无对应 Controller 方法 ★归类B待排期

## tender (11)

- [HTTP_METHOD_MISMATCH] PC前端使用 PUT 请求 /v1/tender/task/{data.id}，但后端声明为 GET
- [HTTP_METHOD_MISMATCH] PC前端使用 DELETE 请求 /v1/tender/task/{id}，但后端声明为 GET
- [HTTP_METHOD_MISMATCH] PC前端使用 PUT 请求 /v1/tender/deposit/{data.id}，但后端声明为 POST
- [HTTP_METHOD_MISMATCH] PC前端使用 DELETE 请求 /v1/tender/deposit/{id}，但后端声明为 POST
- [HTTP_METHOD_MISMATCH] PC前端使用 PUT 请求 /v1/tender/open-bid/{data.id}，但后端声明为 GET
- [HTTP_METHOD_MISMATCH] PC前端使用 DELETE 请求 /v1/tender/open-bid/{id}，但后端声明为 GET
- [FRONTEND_EXTRA_API] PC前端接口 PUT /v1/tender/deposit/return/{data.id}（updateTenderRefund）在后端无对应 Controller 方法
- [FRONTEND_EXTRA_API] PC前端接口 DELETE /v1/tender/deposit/return/{id}（deleteTenderRefund）在后端无对应 Controller 方法
- [FRONTEND_EXTRA_API] PC前端接口 GET /v1/tender/certificate/page（getCertificatePage）在后端无对应 Controller 方法
- [FRONTEND_EXTRA_API] PC前端接口 POST /v1/tender/certificate（createCertificate）在后端无对应 Controller 方法
- [HTTP_METHOD_MISMATCH] PC前端使用 DELETE 请求 /v1/tender/certificate/{id}，但后端声明为 POST

---

## 汇总

| 模块 | 基线项数 | 当前项数 | 归类A已修复 | 归类B待排期 |
| --- | --- | --- | --- | --- |
| system | 3 | 2 | 1 | 2 |
| basedata | 10 | 2 | 8 | 2 |
| project | 1 | 1 | 0 | - |
| budget | 6 | 6 | 0 | - |
| contract | 7 | 7 | 0 | - |
| subcontract | 1 | 1 | 0 | - |
| tender | 11 | 11 | 0 | - |
| site | 7 | 7 | 0 | - |
| machine | 3 | 3 | 0 | - |
| hr | 14 | 14 | 0 | - |
| **合计** | **63** | **54** | **9** | **4** |

> system 归类 A 的 updateUserStatus 已改为调用 /v1/system/user/status（后端存在），审计不再报告为错位。
> basedata 归类 A 的 8 项已修复：material/material-category/supplier/owner/company/inspection-scheme 的 PUT→PUT/{id}（6项）+ supplier-evaluation/page → 根 GET（1项）+ supplier-blacklist/page → 根 GET（1项）。
> system 归类 B 的 2 项（role/{id}/menus GET + post/{id}/status PUT）和 basedata 归类 B 的 2 项（supplier-evaluation PUT/{id} + DELETE/{id}）仍在清单中，均为后端缺接口待排期。

**门禁结论：核心两类之和 = 54 ≤ 63（基线），单调收敛 ✅ 门禁通过。**
