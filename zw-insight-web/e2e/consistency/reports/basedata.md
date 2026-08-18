# 一致性报告 - basedata

生成时间：2026-08-18T00:45:12.414Z

共 7 个页面，不一致项 3 处。

## 自持公司列表 (/basedata/company)
- 接口：`GET /v1/basedata/company/page`
- 后端记录数：1
- 结果：一致 ✅

## 材料字典列表 (/basedata/material)
- 接口：`GET /v1/basedata/material/page`
- 后端记录数：2
- 结果：一致 ✅

## 甲方单位列表 (/basedata/owner)
- 接口：`GET /v1/basedata/owner/page`
- 后端记录数：1
- 结果：一致 ✅

## 供应商列表 (/basedata/supplier)
- 接口：`GET /v1/basedata/supplier/page`
- 后端记录数：5
- 结果：一致 ✅

## 供应商黑名单列表 (/basedata/supplier-blacklist)
- 接口：`GET /v1/basedata/supplier-blacklist`
- 后端记录数：0
- 结果：发现 1 处不一致 ❌

| 行 | 列 | 字段 | 期望(后端) | 实际(前端) |
|---|---|---|---|---|
| -1 | __empty__ |  | 有种子数据 | 测试租户下该列表为空，跳过逐行比对（非一致性缺陷） |

## 供应商评价列表 (/basedata/supplier-evaluation)
- 接口：`GET /v1/basedata/supplier-evaluation`
- 后端记录数：0
- 结果：发现 1 处不一致 ❌

| 行 | 列 | 字段 | 期望(后端) | 实际(前端) |
|---|---|---|---|---|
| -1 | __empty__ |  | 有种子数据 | 测试租户下该列表为空，跳过逐行比对（非一致性缺陷） |

## 检查方案列表 (/basedata/inspection-scheme)
- 接口：`GET /v1/basedata/inspection-scheme/page`
- 后端记录数：0
- 结果：发现 1 处不一致 ❌

| 行 | 列 | 字段 | 期望(后端) | 实际(前端) |
|---|---|---|---|---|
| -1 | __empty__ |  | 有种子数据 | 测试租户下该列表为空，跳过逐行比对（非一致性缺陷） |
