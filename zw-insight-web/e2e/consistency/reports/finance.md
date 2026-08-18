# 一致性报告 - finance

生成时间：2026-08-18T00:45:07.865Z

共 3 个页面，不一致项 1 处。

## 开票申请列表 (/finance/invoice-apply)
- 接口：`GET /v1/finance/invoice-apply/page`
- 后端记录数：1
- 结果：一致 ✅

## 付款申请列表 (/finance/payment-apply)
- 接口：`GET /v1/finance/payment-apply/page`
- 后端记录数：1
- 结果：一致 ✅

## 财务封账列表 (/finance/finance-lock)
- 接口：`GET /v1/finance/lock/page`
- 后端记录数：0
- 结果：发现 1 处不一致 ❌

| 行 | 列 | 字段 | 期望(后端) | 实际(前端) |
|---|---|---|---|---|
| -1 | __empty__ |  | 有封账记录 | 租户无封账记录，跳过逐行比对（非一致性缺陷） |
