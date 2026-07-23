# 一致性报告 - archive

生成时间：2026-07-23T12:46:13.231Z

共 4 个页面，不一致项 3 处。

## 档案首页列表 (/archive/index)
- 接口：`GET /v1/archive/project/0`
- 结果：发现 1 处不一致 ❌

| 行 | 列 | 字段 | 期望(后端) | 实际(前端) |
|---|---|---|---|---|
| -1 | __apiError__ |  | code=200 | code=500 message=系统内部错误，请稍后重试 |

## 办公用品档案列表 (/archive/office-supply)
- 接口：`GET /v1/archive/office-supply`
- 后端记录数：5
- 结果：一致 ✅

## 其它支出合同档案列表 (/archive/other-expense-contract)
- 接口：`GET /v1/archive/other-expense-contract`
- 后端记录数：0
- 结果：发现 1 处不一致 ❌

| 行 | 列 | 字段 | 期望(后端) | 实际(前端) |
|---|---|---|---|---|
| -1 | __empty__ |  | 有种子数据 | 测试租户下该列表为空，跳过逐行比对（非一致性缺陷） |

## 其它收入合同档案列表 (/archive/other-income-contract)
- 接口：`GET /v1/archive/other-income-contract`
- 后端记录数：0
- 结果：发现 1 处不一致 ❌

| 行 | 列 | 字段 | 期望(后端) | 实际(前端) |
|---|---|---|---|---|
| -1 | __empty__ |  | 有种子数据 | 测试租户下该列表为空，跳过逐行比对（非一致性缺陷） |
