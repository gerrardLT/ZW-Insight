import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getBrandConfig } from '@/api/brand'
import defaultLogo from '@/assets/logo.png'
import defaultLogoLight from '@/assets/logo-light.png'

const DEFAULT_SYSTEM_NAME = '中维智营'
const DEFAULT_SYSTEM_SUB = 'INSIGHT OS'
const DEFAULT_COPYRIGHT = '© 2026 中维智营 · 工程项目管理平台'

/**
 * 品牌 Store — 承载当前部署环境的系统名称/副标题/Logo/版权信息
 */
export const useBrandStore = defineStore('brand', () => {
  const systemName = ref(DEFAULT_SYSTEM_NAME)
  const systemSub = ref(DEFAULT_SYSTEM_SUB)
  const logoUrl = ref('')
  const logoLightUrl = ref('')
  const faviconUrl = ref('')
  const copyright = ref(DEFAULT_COPYRIGHT)
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
      }
    } catch {
      // 保持默认
    } finally {
      loaded.value = true
      applyDocumentTitleAndFavicon()
    }
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
          document.getElementsByTagName('head')[0].appendChild(link)
        }
        link.type = 'image/x-icon'
        link.href = faviconUrl.value
      }
    }
  }

  return { systemName, systemSub, logoUrl, logoLightUrl, faviconUrl, copyright, loaded, resolveLogo, fetchBrandConfig, applyDocumentTitleAndFavicon }
})
