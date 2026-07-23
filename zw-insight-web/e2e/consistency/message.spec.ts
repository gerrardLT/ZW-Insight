/**
 * message 消息模块 —— 前端展示 vs 后端数据 一致性
 *  分页列表页（records/total）：
 *   - 公告管理     /message/announcement GET /v1/message/announcement (pageNum/pageSize)
 *   - 通知管理     /message/notice       GET /v1/message/notice       (pageNum/pageSize)
 *   - 消息中心     /message/center       GET /v1/message/msg/unread   (pageNum/pageSize, 默认未读页签)
 *   - 推送配置     /message/push-config  GET /v1/message/push-config  (page/size)
 *
 * ⚠ 分页参数不一致：announcement/notice/center 用 pageNum/pageSize，push-config 用 page/size。
 * ⚠ 消息中心「标题」列含条件安全提醒 tag（异地登录），不做逐字比对。
 */
import { test } from '@playwright/test'
import {
  runListConsistency,
  writeModuleReport,
  type PageConsistencyResult,
  type ColumnSpec,
} from './consistency-helper'

const results: PageConsistencyResult[] = []

// —— 公告：状态映射（与页面 statusLabel 一致） ——
const ANNOUNCE_STATUS: Record<string, string> = { DRAFT: '草稿', PUBLISHED: '已发布', REVOKED: '已撤回' }

test.describe.serial('message 一致性', () => {
  test('公告管理 /message/announcement', async ({ page }) => {
    const columns: ColumnSpec[] = [
      { label: '标题', index: 0, field: 'title' },
      { label: '发布状态', index: 1, expect: (r) => ANNOUNCE_STATUS[r.status] || r.status },
      { label: '创建时间', index: 2, field: 'createTime', type: 'datetime' },
      { label: '发布时间', index: 3, field: 'publishTime', type: 'datetime' },
    ]
    await runListConsistency(page, {
      route: '/message/announcement', title: '公告管理', api: 'GET /v1/message/announcement',
      urlPattern: /\/v1\/message\/announcement/, columns,
    }, results)
  })

  test('通知管理 /message/notice', async ({ page }) => {
    const columns: ColumnSpec[] = [
      { label: '标题', index: 0, field: 'title' },
      { label: '状态', index: 1, expect: (r) => (r.status === 'PUBLISHED' ? '已发布' : '草稿') },
      { label: '创建时间', index: 2, field: 'createTime', type: 'datetime' },
    ]
    await runListConsistency(page, {
      route: '/message/notice', title: '通知管理', api: 'GET /v1/message/notice',
      urlPattern: /\/v1\/message\/notice/, columns,
    }, results)
  })

  test('消息中心 /message/center', async ({ page }) => {
    const columns: ColumnSpec[] = [
      // index 0 为标题列（含条件「安全提醒」tag），不做逐字比对
      { label: '来源', index: 1, field: 'source' },
      { label: '时间', index: 2, field: 'createTime', type: 'datetime' },
      { label: '状态', index: 3, expect: (r) => (r.isRead ? '已读' : '未读') },
    ]
    await runListConsistency(page, {
      route: '/message/center', title: '消息中心', api: 'GET /v1/message/msg/unread',
      urlPattern: /\/v1\/message\/msg\/unread/, columns,
    }, results)
  })

  test('推送配置 /message/push-config', async ({ page }) => {
    const columns: ColumnSpec[] = [
      { label: '业务类型', index: 0, field: 'businessTypeName' },
      { label: '类型编码', index: 1, field: 'businessType' },
      { label: '站内信', index: 2, expect: (r) => (r.enableInApp ? '开启' : '关闭') },
      { label: '短信', index: 3, expect: (r) => (r.enableSms ? '开启' : '关闭') },
      { label: '邮件', index: 4, expect: (r) => (r.enableEmail ? '开启' : '关闭') },
      { label: 'APP推送', index: 5, expect: (r) => (r.enableAppPush ? '开启' : '关闭') },
      { label: '创建时间', index: 6, field: 'createdAt', type: 'datetime' },
    ]
    await runListConsistency(page, {
      route: '/message/push-config', title: '推送配置', api: 'GET /v1/message/push-config',
      urlPattern: /\/v1\/message\/push-config/, columns,
    }, results)
  })

  test.afterAll(async () => {
    writeModuleReport('message', results)
  })
})
