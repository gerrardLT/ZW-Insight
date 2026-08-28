/**
 * 页面注册一致性测试（2026-08-28 移动端设计迁移 Phase 0）
 *
 * 钉住孤儿页类缺陷：src/pages 下每个 .vue 页面文件都必须在 pages.json 注册，
 * 否则 uni.navigateTo 直达必失败（历史案例：mine/sign.vue 已实现+已测试+后端
 * 快捷字典入口就绪，但漏注册导致「定位签到」点击即断链）。
 */
import { describe, it, expect } from 'vitest'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

// 以测试文件位置锚定根目录（不依赖 process.cwd()，防根目录误跑）
const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const PAGES_DIR = path.resolve(ROOT, 'src/pages')
const PAGES_JSON = path.resolve(ROOT, 'src/pages.json')

function walkVueFiles(dir: string): string[] {
  const out: string[] = []
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name)
    if (entry.isDirectory()) out.push(...walkVueFiles(full))
    else if (entry.name.endsWith('.vue')) out.push(full)
  }
  return out
}

describe('pages.json 路由注册一致性', () => {
  const registered: string[] = (JSON.parse(fs.readFileSync(PAGES_JSON, 'utf-8')).pages as any[]).map(
    (p) => p.path
  )
  const files = walkVueFiles(PAGES_DIR)

  it('pages.json 注册的每个路由都有对应页面文件（防死路由）', () => {
    for (const route of registered) {
      const file = path.resolve(ROOT, `src/${route}.vue`)
      expect(fs.existsSync(file), `路由 ${route} 无对应页面文件`).toBe(true)
    }
  })

  it('src/pages 下每个页面文件都已注册（防孤儿页，mine/sign 断链类缺陷）', () => {
    const unregistered = files
      .map((f) => 'pages/' + path.relative(PAGES_DIR, f).replace(/\.vue$/, '').replace(/\\/g, '/'))
      .filter((rel) => !registered.includes(rel))
    expect(unregistered).toEqual([])
  })
})
