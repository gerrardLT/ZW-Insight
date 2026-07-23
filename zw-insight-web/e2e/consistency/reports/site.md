# 一致性报告 - site

生成时间：2026-07-23T12:47:11.023Z

共 3 个页面，不一致项 1 处。

## 施工日志列表 (/site/construction-log)
- 接口：`GET /v1/site/construction-log/page`
- 后端记录数：9
- 结果：一致 ✅

## 进度计划列表 (/site/schedule)
- 接口：`GET /v1/site/schedule/page`
- 后端记录数：0
- 结果：发现 1 处不一致 ❌

| 行 | 列 | 字段 | 期望(后端) | 实际(前端) |
|---|---|---|---|---|
| -1 | __empty__ |  | 有种子数据 | 测试租户下该列表为空，跳过逐行比对（非一致性缺陷） |

## 质量检查列表 (/site/inspection)
- 接口：`GET /v1/site/inspection/page`
- 后端记录数：1
- 结果：一致 ✅
