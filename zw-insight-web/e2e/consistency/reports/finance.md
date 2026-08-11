# 一致性报告 - finance

生成时间：2026-08-11T07:16:39.583Z

共 2 个页面，不一致项 4 处。

## 开票申请列表 (/finance/invoice-apply)
- 接口：`GET /v1/finance/invoice-apply/page`
- 后端记录数：5
- 结果：一致 ✅

## 付款申请列表 (/finance/payment-apply)
- 接口：`GET /v1/finance/payment-apply/page`
- 后端记录数：10
- 结果：发现 4 处不一致 ❌

| 行 | 列 | 字段 | 期望(后端) | 实际(前端) |
|---|---|---|---|---|
| 0 | 状态 | status | SUBMITTED | 审批中 |
| 1 | 状态 | status | SUBMITTED | 审批中 |
| 8 | 状态 | status | SUBMITTED | 审批中 |
| 9 | 状态 | status | SUBMITTED | 审批中 |
