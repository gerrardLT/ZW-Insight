# 一致性报告 - system

生成时间：2026-07-23T12:47:34.096Z

共 8 个页面，不一致项 3 处。

## 用户管理 (/system/user)
- 接口：`GET /v1/system/user`
- 后端记录数：8
- 结果：一致 ✅

## 岗位管理 (/system/post)
- 接口：`GET /v1/system/post`
- 后端记录数：7
- 结果：一致 ✅

## 操作日志 (/system/log)
- 接口：`GET /v1/system/log/oper`
- 后端记录数：0
- 结果：发现 1 处不一致 ❌

| 行 | 列 | 字段 | 期望(后端) | 实际(前端) |
|---|---|---|---|---|
| -1 | __empty__ |  | 有种子数据 | 测试租户下该列表为空，跳过逐行比对（非一致性缺陷） |

## 数据备份 (/system/backup)
- 接口：`GET /v1/system/backup/list`
- 后端记录数：20
- 结果：一致 ✅

## 打印模板 (/system/print-template)
- 接口：`GET /v1/print-template/list`
- 后端记录数：0
- 结果：发现 1 处不一致 ❌

| 行 | 列 | 字段 | 期望(后端) | 实际(前端) |
|---|---|---|---|---|
| -1 | __empty__ |  | 有种子数据 | 测试租户下该列表为空，跳过逐行比对（非一致性缺陷） |

## 编号规则 (/system/serial-number)
- 接口：`GET /v1/file/serial`
- 后端记录数：5
- 结果：一致 ✅

## 通用模板 (/system/template)
- 接口：`GET /v1/file/template`
- 后端记录数：5
- 结果：一致 ✅

## 版本管理 (/system/version)
- 接口：`GET /v1/system/version/list`
- 后端记录数：0
- 结果：发现 1 处不一致 ❌

| 行 | 列 | 字段 | 期望(后端) | 实际(前端) |
|---|---|---|---|---|
| -1 | __empty__ |  | 有种子数据 | 测试租户下该列表为空，跳过逐行比对（非一致性缺陷） |
