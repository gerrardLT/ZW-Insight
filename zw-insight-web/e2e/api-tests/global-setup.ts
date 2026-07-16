/* eslint-disable @typescript-eslint/no-explicit-any */
/**
 * 全局 setup：登录一次，所有测试共享 token
 * 支持两种模式：
 * 1. 验证码关闭（auth.captcha-enabled=false）：直接登录
 * 2. 验证码开启：通过 Tesseract.js OCR 解码验证码
 */
import { writeFileSync, mkdirSync, existsSync } from 'node:fs'
import { resolve, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'

const __filename = fileURLToPath(import.meta.url)
const __dirname = dirname(__filename)

const API_BASE = 'http://129.204.3.200:18080'
const AUTH_FILE = resolve(__dirname, '.auth-state.json')

/** 执行一次登录请求 */
async function doLogin(
  captchaUuid?: string,
  captchaCode?: string
): Promise<{ code: number; message: string; data?: any }> {
  const body: any = {
    loginType: 'PASSWORD',
    username: 'admin',
    password: '123456',
  }
  if (captchaUuid && captchaCode) {
    body.captchaUuid = captchaUuid
    body.captchaCode = captchaCode
  }

  const resp = await fetch(`${API_BASE}/api/v1/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
  return resp.json()
}

/** OCR 解码验证码 */
async function solveCaptcha(imageBase64: string): Promise<string> {
  const Tesseract = require('tesseract.js')
  const b64 = imageBase64.replace('data:image/png;base64,', '')
  const buf = Buffer.from(b64, 'base64')
  const worker = await Tesseract.createWorker('eng')
  await worker.setParameters({
    tessedit_char_whitelist: 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789',
    tessedit_pageseg_mode: '7',
  })
  const { data } = await worker.recognize(buf)
  await worker.terminate()
  return data.text.trim().replace(/[^a-zA-Z0-9]/g, '').substring(0, 4)
}

/** 带重试的登录 */
async function loginWithRetry(maxRetry = 5): Promise<{ token: string; tenantId: number }> {
  for (let i = 1; i <= maxRetry; i++) {
    console.log(`[global-setup] 登录尝试 ${i}/${maxRetry}...`)

    // 先尝试无验证码登录（验证码关闭时直接成功）
    const directResult = await doLogin()
    if (directResult.code === 200 && directResult.data?.token) {
      console.log(`[global-setup] 登录成功(无验证码), userId=${directResult.data.userId}`)
      return { token: directResult.data.token, tenantId: directResult.data.tenantId }
    }

    // 如果需要验证码，获取验证码 + OCR
    console.log(`[global-setup] 需要验证码: ${directResult.message}`)
    const captchaResp = await fetch(`${API_BASE}/api/v1/captcha/image`)
    const captchaJson = await captchaResp.json()
    const uuid = captchaJson.data?.uuid
    const imageBase64 = captchaJson.data?.imageBase64
    if (!uuid || !imageBase64) {
      console.warn('[global-setup] 验证码接口异常')
      continue
    }

    let captchaCode = ''
    try {
      captchaCode = await solveCaptcha(imageBase64)
      console.log(`[global-setup] OCR: "${captchaCode}"`)
    } catch (e: any) {
      console.warn('[global-setup] OCR 失败:', e?.message)
      continue
    }

    if (!captchaCode || captchaCode.length < 3) {
      continue
    }

    const result = await doLogin(uuid, captchaCode)
    if (result.code === 200 && result.data?.token) {
      console.log(`[global-setup] 登录成功, userId=${result.data.userId}, tenantId=${result.data.tenantId}`)
      return { token: result.data.token, tenantId: result.data.tenantId }
    }

    console.warn(`[global-setup] 登录失败: code=${result.code}, msg=${result.message}`)
  }

  throw new Error(`登录在 ${maxRetry} 次重试内失败，请检查验证码是否已关闭(auth.captcha-enabled=false)`)
}

export async function setup() {
  console.log('[global-setup] 正在登录远程服务器...')
  const { token, tenantId } = await loginWithRetry()

  const authDir = resolve(__dirname, '.auth')
  if (!existsSync(authDir)) {
    mkdirSync(authDir, { recursive: true })
  }
  writeFileSync(AUTH_FILE, JSON.stringify({ token, tenantId }))
}

export async function teardown() {
  console.log('[global-setup] 清理认证状态...')
  try {
    const { unlinkSync } = await import('node:fs')
    unlinkSync(AUTH_FILE)
  } catch {
    // ignore
  }
}
