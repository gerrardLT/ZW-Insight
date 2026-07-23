# 一致性报告 - dashboard

生成时间：2026-07-23T12:46:30.377Z

共 2 个页面，不一致项 2 处。

## 首页驾驶舱 (/dashboard)
- 接口：`GET /v1/dashboard/company-overview`
- 结果：发现 1 处不一致 ❌

| 行 | 列 | 字段 | 期望(后端) | 实际(前端) |
|---|---|---|---|---|
| -1 | __silentFallback__ | loadStats/pie/bar catch | 接口异常应显式提示 | index.vue 三处 catch 静默兜底空数据，无错误提示（违反无静默 fallback 约定） |

## 项目看板 (/project-dashboard)
- 接口：`GET /v1/dashboard/project/{id}/overview`
- 结果：发现 1 处不一致 ❌

| 行 | 列 | 字段 | 期望(后端) | 实际(前端) |
|---|---|---|---|---|
| -1 | __note__ | overview | 维度接口 code=200 | 接口正常返回 |
