/**
 * 前端资产批次测试（2026-10-05）
 * 覆盖：ZwCountUp（reduced-motion 确定性/格式化/续动）、ZwSkeleton（四形态渲染）、
 *       错误话术映射（具体透出/泛化兜底/网络关键词）、品牌主色派生与回落。
 */
import { describe, it, expect, afterEach } from 'vitest'
import { mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'

import ZwCountUp from '@/components/ZwCountUp.vue'
import ZwSkeleton from '@/components/ZwSkeleton.vue'
import { humanizeApiError } from '@/constants/error-messages'
import { useBrandStore } from '@/stores/brand'
import { createPinia, setActivePinia } from 'pinia'

afterEach(() => {
  document.documentElement.style.removeProperty('--zw-brand')
  document.documentElement.style.removeProperty('--zw-brand-hover')
})

describe('ZwCountUp 数字翻牌', () => {
  it('duration=0 确定性落定：挂载即终值（测试/无障碍路径）', () => {
    const w = mount(ZwCountUp, { props: { value: 1234.5, duration: 0 } })
    expect(w.find('[data-testid="zw-count-up"]').text()).toBe('1234.5')
  })

  it('format 函数生效（千分位/单位）', () => {
    const w = mount(ZwCountUp, {
      props: { value: 9200, duration: 0, format: (n: number) => n.toFixed(1) + ' 万' },
    })
    expect(w.text()).toBe('9200.0 万')
  })

  it('reduced-motion 偏好下直接落定，不起动画', () => {
    const matchSpy = vi.spyOn(window, 'matchMedia').mockReturnValue({ matches: true } as MediaQueryList)
    const w = mount(ZwCountUp, { props: { value: 777, duration: 800 } })
    expect(w.text()).toBe('777')
    matchSpy.mockRestore()
  })
})

describe('ZwSkeleton 骨架屏', () => {
  it.each(['cards', 'table', 'chart', 'detail'] as const)('%s 形态渲染且带 testid', (variant) => {
    const w = mount(ZwSkeleton, { props: { variant }, global: { plugins: [ElementPlus] } })
    expect(w.find(`[data-testid="zw-skeleton-${variant}"]`).exists()).toBe(true)
  })

  it('cards 形态按 count 渲染卡片数', () => {
    const w = mount(ZwSkeleton, { props: { variant: 'cards', count: 6 }, global: { plugins: [ElementPlus] } })
    expect(w.findAll('.zw-skeleton__card')).toHaveLength(6)
  })
})

describe('错误话术映射 humanizeApiError', () => {
  it('具体业务提示原样透出（不覆盖）', () => {
    expect(humanizeApiError(500, '仅草稿状态可删除')).toBe('仅草稿状态可删除')
    expect(humanizeApiError(400, '预测天数需在1-365之间')).toBe('预测天数需在1-365之间')
  })

  it('泛化文案按码表兜底', () => {
    expect(humanizeApiError(500, '系统内部错误，请稍后重试')).toContain('系统开小差')
    expect(humanizeApiError(403, '')).toContain('权限')
    expect(humanizeApiError(409, '请求失败')).toContain('冲突')
  })

  it('无码时按网络关键词翻译', () => {
    expect(humanizeApiError(undefined, 'timeout of 15000ms exceeded')).toContain('超时')
    expect(humanizeApiError(undefined, 'Failed to fetch')).toContain('网络')
  })

  it('完全无信息走默认兜底', () => {
    expect(humanizeApiError(undefined, '')).toBe('操作未完成，请稍后重试')
  })
})

describe('品牌主色换肤', () => {
  it('合法色值写入 --zw-brand 家族，非法/空清除回落', async () => {
    setActivePinia(createPinia())
    const s = useBrandStore()
    s.brandColor = '#2b6cb0'
    s.applyBrandTheme()
    expect(document.documentElement.style.getPropertyValue('--zw-brand')).toBe('#2b6cb0')
    expect(document.documentElement.style.getPropertyValue('--zw-brand-hover')).toMatch(/^#[0-9a-f]{6}$/)

    s.brandColor = 'not-a-color'
    s.applyBrandTheme()
    expect(document.documentElement.style.getPropertyValue('--zw-brand')).toBe('')

    s.brandColor = ''
    s.applyBrandTheme()
    expect(document.documentElement.style.getPropertyValue('--zw-brand')).toBe('')
  })

  it('深色模式下派生色全部为合法 hex（回归：sink() 曾漏传 target=0 致蓝色通道 NaN）', () => {
    setActivePinia(createPinia())
    document.documentElement.dataset.theme = 'dark'
    try {
      const s = useBrandStore()
      s.brandColor = '#ff6b00'
      s.applyBrandTheme()
      const keys = ['--zw-brand', '--zw-brand-hover', '--zw-brand-active', '--zw-brand-light', '--zw-brand-lighter', '--zw-brand-gradient', '--zw-brand-gradient-hover']
      for (const k of keys) {
        const v = document.documentElement.style.getPropertyValue(k)
        expect(v, `${k} 应为合法 hex`).toMatch(/^#[0-9a-f]{6}$/)
      }
    } finally {
      delete document.documentElement.dataset.theme
    }
  })
})
