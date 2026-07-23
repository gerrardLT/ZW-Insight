# 一致性报告 - basedata

生成时间：2026-07-23T12:46:29.019Z

共 7 个页面，不一致项 1 处。

## 自持公司列表 (/basedata/company)
- 接口：`GET /v1/basedata/company/page`
- 后端记录数：6
- 结果：一致 ✅

## 材料字典列表 (/basedata/material)
- 接口：`GET /v1/basedata/material/page`
- 后端记录数：10
- 结果：一致 ✅

## 甲方单位列表 (/basedata/owner)
- 接口：`GET /v1/basedata/owner/page`
- 后端记录数：4
- 结果：一致 ✅

## 供应商列表 (/basedata/supplier)
- 接口：`GET /v1/basedata/supplier/page`
- 后端记录数：10
- 结果：一致 ✅

## 供应商黑名单列表 (/basedata/supplier-blacklist)
- 接口：`GET /v1/basedata/supplier-blacklist`
- 后端记录数：1
- 结果：一致 ✅

## 供应商评价列表 (/basedata/supplier-evaluation)
- 接口：`GET /v1/basedata/supplier-evaluation`
- 结果：发现 1 处不一致 ❌

| 行 | 列 | 字段 | 期望(后端) | 实际(前端) |
|---|---|---|---|---|
| -1 | __apiError__ |  | code=200 | code=500 message=系统内部错误，请稍后重试 |

## 检查方案列表 (/basedata/inspection-scheme)
- 接口：`GET /v1/basedata/inspection-scheme/page`
- 后端记录数：2
- 结果：一致 ✅
