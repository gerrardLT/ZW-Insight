# 一致性报告 - workflow

生成时间：2026-08-11T08:53:09.570Z

共 3 个页面，不一致项 1 处。

## 审批待办 (/workflow/approval)
- 接口：`GET /v1/workflow/approval/todo`
- 后端记录数：10
- 结果：一致 ✅

## 流程定义 (/workflow/process)
- 接口：`GET /v1/workflow/process`
- 后端记录数：24
- 结果：一致 ✅

## 回滚日志 (/workflow/rollback)
- 接口：`GET /v1/workflow/rollback/logs`
- 后端记录数：0
- 结果：发现 1 处不一致 ❌

| 行 | 列 | 字段 | 期望(后端) | 实际(前端) |
|---|---|---|---|---|
| -1 | __empty__ |  | 有种子数据 | 测试租户下该列表为空，跳过逐行比对（非一致性缺陷） |
