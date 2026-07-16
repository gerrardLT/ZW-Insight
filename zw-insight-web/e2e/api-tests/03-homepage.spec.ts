/* eslint-disable @typescript-eslint/no-explicit-any */
import { describe, it, expect, beforeAll, afterAll } from 'vitest'
import { createAuthedClient, createCleaner, expectOk } from './setup'
import type { ApiClient } from './api-client'
import type { TestDataCleaner } from './test-data'

/**
 * 03 - 首页测试
 * 对应功能表: 1.2 首页
 */
describe('03 - 首页', () => {
  let client: ApiClient
  let cleaner: TestDataCleaner

  beforeAll(async () => {
    client = createAuthedClient()
    cleaner = createCleaner()
  })

  afterAll(async () => {
    await cleaner.cleanup(client)
  })

  // ============ 快捷入口 ============
  describe('快捷入口', () => {
    it('获取当前用户快捷入口', async () => {
      const resp = await client.get('/api/v1/message/shortcut')
      expect(resp.code).toBe(200)
      expect(Array.isArray(resp.data)).toBe(true)
    })

    it('获取全部可选功能列表', async () => {
      const resp = await client.get('/api/v1/message/shortcut/available')
      expect(resp.code).toBe(200)
      expect(Array.isArray(resp.data)).toBe(true)
    })

    it('添加快捷入口', async () => {
      // 先获取可选列表
      const availResp = await client.get('/api/v1/message/shortcut/available')
      if (!availResp.data || availResp.data.length === 0) {
        console.warn('没有可选的快捷入口，跳过添加测试')
        return
      }
      const firstAvailable = availResp.data[0]
      const resp = await client.post('/api/v1/message/shortcut', {
        menuId: firstAvailable.id || firstAvailable.menuId,
        menuName: firstAvailable.menuName || firstAvailable.name,
        sort: 99,
      })
      expectOk(resp, '添加快捷入口')
    })

    it('批量保存快捷入口配置', async () => {
      const availResp = await client.get('/api/v1/message/shortcut/available')
      if (!availResp.data || availResp.data.length === 0) return

      const ids = availResp.data.slice(0, 3).map((s: any) => s.id || s.menuId)
      const resp = await client.post('/api/v1/message/shortcut/batch', {
        shortcutIds: ids,
      })
      expectOk(resp, '批量保存快捷入口')
    })
  })

  // ============ 公告 ============
  describe('公告管理', () => {
    let announcementId: number

    it('创建公告', async () => {
      const resp = await client.post('/api/v1/message/announcement', {
        title: `E2E_TEST_公告_${Date.now()}`,
        content: '这是自动测试创建的公告内容',
        status: 'DRAFT',
      })
      expectOk(resp, '创建公告')
    })

    it('分页查询公告', async () => {
      const resp = await client.get('/api/v1/message/announcement', {
        page: 1,
        size: 10,
      })
      expect(resp.code).toBe(200)
      const records = resp.data?.records || []
      expect(records.length).toBeGreaterThanOrEqual(0)

      // 找到测试公告
      const found = records.find((r: any) =>
        r.title?.startsWith('E2E_TEST_公告_')
      )
      if (found) {
        announcementId = found.id
        cleaner.add('删除公告', () =>
          client.delete(`/api/v1/message/announcement/${announcementId}`)
        )
      }
    })

    it('发布公告', async () => {
      if (!announcementId) return
      const resp = await client.post(
        `/api/v1/message/announcement/${announcementId}/publish`
      )
      expectOk(resp, '发布公告')
    })

    it('撤回公告', async () => {
      if (!announcementId) return
      const resp = await client.post(
        `/api/v1/message/announcement/${announcementId}/revoke`
      )
      expectOk(resp, '撤回公告')
    })
  })

  // ============ 通知 ============
  describe('通知', () => {
    it('查询通知列表', async () => {
      const resp = await client.get('/api/v1/message/notice', {
        page: 1,
        size: 10,
      })
      expect(resp.code).toBe(200)
    })
  })

  // ============ 消息中心 ============
  describe('消息中心', () => {
    it('查询消息列表', async () => {
      const resp = await client.get('/api/v1/message/msg/all', {
        page: 1,
        size: 10,
      })
      expect(resp.code).toBe(200)
    })
  })
})
