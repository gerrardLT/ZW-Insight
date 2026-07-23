# 一致性报告 - platform

生成时间：2026-07-23T12:46:54.370Z

共 3 个页面，不一致项 2 处。

## 租户管理 (/platform/tenant)
- 接口：`GET /v1/platform/tenant`
- 后端记录数：1
- 结果：一致 ✅

## 租户类型 (/platform/tenant-type)
- 接口：`GET /v1/platform/tenant-type`
- 后端记录数：0
- 结果：发现 1 处不一致 ❌

| 行 | 列 | 字段 | 期望(后端) | 实际(前端) |
|---|---|---|---|---|
| -1 | __empty__ |  | 有种子数据 | 测试租户下该列表为空，跳过逐行比对（非一致性缺陷） |

## 存储配置 (/platform/storage)
- 接口：`GET /v1/file/storage`
- 结果：发现 1 处不一致 ❌

| 行 | 列 | 字段 | 期望(后端) | 实际(前端) |
|---|---|---|---|---|
| -1 | __apiError__ |  | code=200 | code=500 message=系统内部错误，请稍后重试 |
