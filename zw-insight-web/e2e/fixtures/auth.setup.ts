import { test as setup } from '@playwright/test'
import fs from 'fs'
import path from 'path'
import { fileURLToPath } from 'url'

const __filename = fileURLToPath(import.meta.url)
const __dirname = path.dirname(__filename)
const authDir = path.resolve(__dirname, '..', '.auth')
const storageStatePath = path.join(authDir, 'storage-state.json')

/**
 * 全局 setup：直接写入 storageState 文件（注入 token 到 localStorage），
 * 跳过浏览器登录流程（避免验证码和网络依赖）
 */
setup('认证：生成 storageState', async () => {
  fs.mkdirSync(authDir, { recursive: true })

  const storageState = {
    cookies: [],
    origins: [
      {
        origin: 'http://localhost:3000',
        localStorage: [
          { name: 'token', value: 'mock-jwt-token-e2e-test' },
          {
            name: 'user',
            value: JSON.stringify({
              token: 'mock-jwt-token-e2e-test',
              userId: 1,
              username: 'admin',
              realName: '管理员',
              tenantId: 1,
              tenantName: '测试租户',
              roles: ['SUPER_ADMIN'],
              permissions: ['*:*:*'],
            }),
          },
        ],
      },
    ],
  }

  fs.writeFileSync(storageStatePath, JSON.stringify(storageState, null, 2))
})
