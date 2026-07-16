/* eslint-disable @typescript-eslint/no-explicit-any */
/**
 * 测试共享 setup
 * 每个 spec 文件 import 此模块来获取已认证的 client
 */
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { ApiClient } from './api-client'
import { TestDataCleaner } from './test-data'

const AUTH_FILE = resolve(__dirname, '.auth-state.json')

/** 从 globalSetup 写入的文件中恢复 token */
function loadAuthState(): { token: string; tenantId: number } {
  try {
    const raw = readFileSync(AUTH_FILE, 'utf-8')
    return JSON.parse(raw)
  } catch {
    throw new Error(
      '[test-setup] 认证状态文件不存在，请确保 globalSetup 已执行登录。' +
        '路径: ' + AUTH_FILE
    )
  }
}

/** 创建已认证的 ApiClient */
export function createAuthedClient(): ApiClient {
  const { token, tenantId } = loadAuthState()
  const client = new ApiClient()
  client.setToken(token)
  // tenantId 通过手动设置
  ;(client as any).tenantId = tenantId
  return client
}

/** 创建清理器 */
export function createCleaner(): TestDataCleaner {
  return new TestDataCleaner()
}

/**
 * 辅助：从分页结果中按字段查找记录
 */
export function findRecord<T extends Record<string, any>>(
  records: T[],
  field: string,
  value: any
): T | undefined {
  return records.find((r) => r[field] === value)
}

/**
 * 辅助：断言 API 响应成功（code === 200）
 */
export function expectOk(resp: { code: number; message: string }, label?: string) {
  if (resp.code !== 200) {
    throw new Error(
      `[期望 code=200] ${label || ''} 实际 code=${resp.code}, message=${resp.message}`
    )
  }
}
