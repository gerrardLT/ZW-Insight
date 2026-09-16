// @vitest-environment happy-dom
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import ZwOfflineIndicator from '../src/components/ZwOfflineIndicator.vue'
import ZwBottomSheetPicker from '../src/components/ZwBottomSheetPicker.vue'
import ZwWatermarkCamera from '../src/components/ZwWatermarkCamera.vue'
import { useNetworkStore } from '../src/stores/network'
import { syncEngine } from '../src/utils/syncEngine'

describe('Mobile Advanced Components', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.restoreAllMocks()
  })

  it('ZwOfflineIndicator 响应网络状态切换与队列计数', async () => {
    const network = useNetworkStore()
    vi.spyOn(syncEngine, 'getQueue').mockReturnValue([
      { id: '1', type: 'INBOUND', endpoint: '/test', payload: {}, timestamp: Date.now(), retryCount: 0, status: 'PENDING' },
      { id: '2', type: 'OUTBOUND', endpoint: '/test', payload: {}, timestamp: Date.now(), retryCount: 0, status: 'PENDING' },
      { id: '3', type: 'LOG', endpoint: '/test', payload: {}, timestamp: Date.now(), retryCount: 0, status: 'PENDING' }
    ])

    const wrapper = mount(ZwOfflineIndicator)

    expect(wrapper.find('.indicator-label').text()).toBe('ONLINE')
    expect(wrapper.find('.led-light').classes()).toContain('led-green')

    network.setOffline(true)
    network.setQueueCount(3)
    await wrapper.vm.$nextTick()

    expect(wrapper.find('.indicator-label').text()).toBe('OFFLINE')
    expect(wrapper.find('.queue-badge').text()).toBe('3')
  })

  it('ZwBottomSheetPicker 选项渲染与选择触发', async () => {
    const options = [
      { label: '钢筋一期采购', value: '1', sub: 'SN-001' },
      { label: '商砼二期浇筑', value: '2', sub: 'SN-002' }
    ]

    const wrapper = mount(ZwBottomSheetPicker, {
      props: {
        options,
        modelValue: '1'
      }
    })

    expect(wrapper.find('.trigger-label').text()).toBe('钢筋一期采购')

    // 唤起抽屉
    await wrapper.find('.picker-trigger').trigger('click')
    expect(wrapper.find('.sheet-panel').exists()).toBe(true)

    // 选择第二项
    const rows = wrapper.findAll('.option-row')
    expect(rows).toHaveLength(2)
    await rows[1].trigger('click')

    // 点击确定
    await wrapper.find('.action-confirm').trigger('click')
    expect(wrapper.emitted('update:modelValue')?.[0]).toEqual(['2'])
  })

  it('ZwWatermarkCamera 默认渲染工业十字瞄准标尺', () => {
    const wrapper = mount(ZwWatermarkCamera, {
      props: {
        projectName: '测试工程',
        serialNumber: 'ZW-SN-TEST'
      }
    })

    expect(wrapper.find('.reticle-box').exists()).toBe(true)
    expect(wrapper.find('.reticle-crosshair').exists()).toBe(true)
    expect(wrapper.find('.reticle-title').text()).toBe('现场工程水印拍照')
  })
})
