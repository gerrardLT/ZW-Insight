import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getBrandConfig } from '@/api/brand'
import defaultLogo from '@/assets/logo.png'
import defaultLogoLight from '@/assets/logo-light.png'

const DEFAULT_SYSTEM_NAME = '中维智营'
const DEFAULT_SYSTEM_SUB = 'INSIGHT OS'
const DEFAULT_COPYRIGHT = '© 2026 中维智营 · 工程项目管理平台'

/** 十六进制色值合法性（#RRGGBB） */
const HEX_COLOR_RE = /^#[0-9a-fA-F]{6}$/

/**
 * 品牌主色派生：由基色生成 --zw-brand 变量家族的各档色值。
 * 派生规则与内置橙色主题（tokens/light.css、dark.css）的档位关系同构：
 * hover 提亮、active 压暗、light/lighter 为白底高掺白面板色、gradient 同 hover。
 * 深色模式下面板色（light/lighter）改向黑掺——保持与内置深色主题的低亮度基调。
 */
function deriveBrandFamily(base: string, isDark: boolean) {
  const n = parseInt(base.slice(1), 16)
  const r = (n >> 16) & 0xff
  const g = (n >> 8) & 0xff
  const b = n & 0xff
  const channel = (v: number, target: number, ratio: number) =>
    Math.round(v * (1 - ratio) + target * ratio)
  const hex = (r2: number, g2: number, b2: number) =>
    `#${[r2, g2, b2].map((v) => Math.max(0, Math.min(255, v)).toString(16).padStart(2, '0')).join('')}`
  const lift = (ratio: number) => hex(channel(r, 255, ratio), channel(g, 255, ratio), channel(b, 255, ratio))
  const sink = (ratio: number) => hex(channel(r, 0, ratio), channel(g, 0, ratio), channel(b, ratio))
  return {
    brand: base,
    hover: lift(0.12),
    active: sink(0.14),
    light: isDark ? sink(0.72) : lift(0.82),
    lighter: isDark ? sink(0.82) : lift(0.92),
    gradient: lift(0.12),
    gradientHover: lift(0.2),
  }
}

/** 把派生色族写到 :root 的 CSS 变量（浅/深两套同名变量，改 root 即整体换肤） */
function applyBrandColor(base: string, isDark: boolean) {
  if (typeof document === 'undefined') return
  const family = deriveBrandFamily(base, isDark)
  const root = document.documentElement
  root.style.setProperty('--zw-brand', family.brand)
  root.style.setProperty('--zw-brand-hover', family.hover)
  root.style.setProperty('--zw-brand-active', family.active)
  root.style.setProperty('--zw-brand-light', family.light)
  root.style.setProperty('--zw-brand-lighter', family.lighter)
  root.style.setProperty('--zw-brand-gradient', family.gradient)
  root.style.setProperty('--zw-brand-gradient-hover', family.gradientHover)
}

/**
 * 品牌 Store — 承载当前部署环境的系统名称/副标题/Logo/版权/主色信息
 */
export const useBrandStore = defineStore('brand', () => {
  const systemName = ref(DEFAULT_SYSTEM_NAME)
  const systemSub = ref(DEFAULT_SYSTEM_SUB)
  const logoUrl = ref('')
  const logoLightUrl = ref('')
  const faviconUrl = ref('')
  const copyright = ref(DEFAULT_COPYRIGHT)
  /** 品牌主色（#RRGGBB）；空 = 使用内置主题色，不写任何 CSS 变量 */
  const brandColor = ref('')
  const loaded = ref(false)

  /** Logo 实际展示地址：优先取自定义配置，区分暗色/亮色适配，留空回退内置默认图 */
  function resolveLogo(isDark: boolean): string {
    if (isDark) {
      if (logoLightUrl.value) return logoLightUrl.value
      if (logoUrl.value) return logoUrl.value
      return defaultLogoLight
    } else {
      if (logoUrl.value) return logoUrl.value
      return defaultLogo
    }
  }

  /** 拉取品牌配置 */
  async function fetchBrandConfig() {
    try {
      const res = await getBrandConfig()
      const data = res?.data
      if (data) {
        systemName.value = data.systemName || DEFAULT_SYSTEM_NAME
        systemSub.value = data.systemSub || DEFAULT_SYSTEM_SUB
        logoUrl.value = data.logoUrl || ''
        logoLightUrl.value = data.logoLightUrl || ''
        faviconUrl.value = data.faviconUrl || ''
        copyright.value = data.copyright || DEFAULT_COPYRIGHT
        brandColor.value = data.brandColor || ''
      }
    } catch {
      // 保持默认
    } finally {
      loaded.value = true
      applyDocumentTitleAndFavicon()
      applyBrandTheme()
    }
  }

/** 应用品牌主色换肤：非法/留空时移除内联覆盖，回落 tokens 内置色 */
function applyBrandTheme() {
  if (typeof document === 'undefined') return
  const base = brandColor.value.trim()
  if (!HEX_COLOR_RE.test(base)) {
    // 回落：清掉内联变量（主题切换的 dark/light token 自动接管）
    const root = document.documentElement
    for (const key of ['--zw-brand', '--zw-brand-hover', '--zw-brand-active', '--zw-brand-light', '--zw-brand-lighter', '--zw-brand-gradient', '--zw-brand-gradient-hover']) {
      root.style.removeProperty(key)
    }
    return
  }
  // 主题由 data-theme 属性驱动（见 app store applyTheme）
  const isDark = document.documentElement.dataset.theme === 'dark'
  applyBrandColor(base.toLowerCase(), isDark)
}

  /** 将系统名称与 Favicon 动态应用到浏览器 Tab */
  function applyDocumentTitleAndFavicon() {
    if (typeof document !== 'undefined') {
      document.title = `${systemName.value} - 工程项目管理平台`
      if (faviconUrl.value) {
        let link: HTMLLinkElement | null = document.querySelector("link[rel*='icon']")
        if (!link) {
          link = document.createElement('link')
          link.rel = 'shortcut icon'
          document.getElementsByTagName('head').appendChild(link)
        }
        link.type = 'image/x-icon'
        link.href = faviconUrl.value
      }
    }
  }

  return {
    systemName,
    systemSub,
    logoUrl,
    logoLightUrl,
    faviconUrl,
    copyright,
    brandColor,
    loaded,
    resolveLogo,
    fetchBrandConfig,
    applyDocumentTitleAndFavicon,
    applyBrandTheme,
  }
})
