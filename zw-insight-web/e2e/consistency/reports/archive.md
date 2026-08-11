# 一致性报告 - archive

生成时间：2026-08-11T08:51:49.843Z

共 4 个页面，不一致项 2 处。

## 档案首页 (/archive/index)
- 接口：`GET /v1/project/list + GET /v1/archive/project/{id}`
- 后端记录数：50
- 结果：一致 ✅

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
