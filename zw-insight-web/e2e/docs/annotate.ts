/**
 * 文档截图辅助：真实服务器页面截图 + 可选红框标注。
 *
 * 设计原则（与「真实接口真实流程」一致）：
 *   - 全程复用 e2e/fixtures/auth-real.setup.ts 的真实登录 storageState，不 mock；
 *   - 截图为视口截图（1440×900），标注 overlay 用视口坐标（页面不滚动，避免坐标错位）；
 *   - 标注元素缺失时静默跳过该框（不报错、不伪造），保证一图失败不阻断整套。
 */
import type { Page, Locator } from '@playwright/test'

export interface Mark {
  /** CSS 选择器（querySelector 第一个命中） */
  selector: string
  /** 角标序号；缺省按顺序自动编号 */
  label?: number
}

/** 通用等待：网络空闲 + 加载遮罩消失 + 骨架屏消失 */
export async function settle(page: Page) {
  await page.waitForLoadState('networkidle', { timeout: 30_000 }).catch(() => {})
  await page.waitForSelector('.el-loading-mask', { state: 'hidden', timeout: 15_000 }).catch(() => {})
  await page.waitForTimeout(600)
}

/** 注入标注样式并在命中元素外绘制红框 + 序号角标（视口坐标） */
export async function annotate(page: Page, marks: Mark[]) {
  if (!marks.length) return
  await page.evaluate((marks) => {
    document.querySelectorAll('[data-doc-annot]').forEach((e) => e.remove())
    if (!document.getElementById('doc-annot-style')) {
      const style = document.createElement('style')
      style.id = 'doc-annot-style'
      style.textContent = `
        .doc-mark{position:absolute;border:2px solid #e5484d;border-radius:4px;pointer-events:none;z-index:20000;box-sizing:border-box;}
        .doc-mark-badge{position:absolute;background:#e5484d;color:#fff;font:600 12px/1 system-ui,sans-serif;padding:3px 7px;border-radius:10px;z-index:20001;pointer-events:none;transform:translate(-50%,-100%);}
      `
      document.head.appendChild(style)
    }
    let auto = 1
    marks.forEach((m) => {
      const el = document.querySelector(m.selector) as HTMLElement | null
      if (!el) return
      const r = el.getBoundingClientRect()
      if (r.width === 0 || r.height === 0) return
      const num = m.label ?? auto++
      const box = document.createElement('div')
      box.className = 'doc-mark'
      box.setAttribute('data-doc-annot', '1')
      box.style.left = r.left - 2 + 'px'
      box.style.top = r.top - 2 + 'px'
      box.style.width = r.width + 4 + 'px'
      box.style.height = r.height + 4 + 'px'
      document.body.appendChild(box)
      const badge = document.createElement('div')
      badge.className = 'doc-mark-badge'
      badge.setAttribute('data-doc-annot', '1')
      badge.textContent = String(num)
      badge.style.left = r.left + 'px'
      badge.style.top = r.top - 4 + 'px'
      document.body.appendChild(badge)
    })
  }, marks)
}

/**
 * 尝试点击任一候选文案按钮以打开弹窗，返回命中的文案；全部未命中返回 null。
 * 候选按顺序尝试，点击后等待 .el-dialog 出现判定成功。
 */
export async function tryOpenDialog(page: Page, buttonTexts: string[]): Promise<string | null> {
  for (const text of buttonTexts) {
    // 遍历含目标文案的按钮：跳过不可见/禁用（如未选中时禁用的"批量通过"），
    // 点第一个能真正打开弹窗的（兼顾行内"通过"链接按钮与含图标的"新增"按钮）
    const btns = page.locator(`button:has-text("${text}")`)
    const n = await btns.count()
    for (let i = 0; i < n; i++) {
      const b = btns.nth(i)
      try {
        if (!(await b.isVisible())) continue
        if (await b.isDisabled()) continue
        await b.click({ timeout: 2500 })
        await page.waitForSelector('.el-dialog', { state: 'visible', timeout: 4000 })
        await page.waitForTimeout(400)
        return text
      } catch {
        continue
      }
    }
  }
  return null
}

/** 截取当前弹窗（第一个可见 .el-dialog） */
export async function screenshotDialog(page: Page, path: string) {
  const dialog: Locator = page.locator('.el-dialog:visible').last()
  await dialog.screenshot({ path })
}

/** 视口整页截图 */
export async function screenshotViewport(page: Page, path: string) {
  await page.screenshot({ path })
}
