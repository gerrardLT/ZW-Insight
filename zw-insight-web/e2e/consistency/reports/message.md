# 一致性报告 - message

生成时间：2026-07-23T12:46:56.769Z

共 4 个页面，不一致项 2 处。

## 公告管理 (/message/announcement)
- 接口：`GET /v1/message/announcement`
- 后端记录数：2
- 结果：一致 ✅

## 通知管理 (/message/notice)
- 接口：`GET /v1/message/notice`
- 后端记录数：0
- 结果：发现 1 处不一致 ❌

| 行 | 列 | 字段 | 期望(后端) | 实际(前端) |
|---|---|---|---|---|
| -1 | __empty__ |  | 有种子数据 | 测试租户下该列表为空，跳过逐行比对（非一致性缺陷） |

## 消息中心 (/message/center)
- 接口：`GET /v1/message/msg/unread`
- 后端记录数：1
- 结果：一致 ✅

## 推送配置 (/message/push-config)
- 接口：`GET /v1/message/push-config`
- 后端记录数：0
- 结果：发现 1 处不一致 ❌

| 行 | 列 | 字段 | 期望(后端) | 实际(前端) |
|---|---|---|---|---|
| -1 | __empty__ |  | 有种子数据 | 测试租户下该列表为空，跳过逐行比对（非一致性缺陷） |
