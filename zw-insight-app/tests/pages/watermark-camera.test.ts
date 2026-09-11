// @vitest-environment happy-dom
/**
 * 现场工程防伪水印相机组件测试 (watermark-camera)
 * 覆盖 pages/site/watermark-camera/index.vue
 */
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'

vi.mock('@/api/common', () => ({
  getProjectList: vi.fn(),
}))

import WatermarkCamera from '@/pages/site/watermark-camera/index.vue'
import { getProjectList } from '@/api/common'
import { useUserStore } from '@/stores/user'
import { resetUniStorage, getUni } from '../setup'

beforeEach(() => {
  resetUniStorage()
  setActivePinia(createPinia())
  vi.clearAllMocks()
  vi.useFakeTimers()
  vi.mocked(getProjectList).mockResolvedValue({
    code: 200,
    data: { records: [{ id: 1, projectName: '测试示范工程' }] },
  } as any)
  ;(getUni() as any).getLocation = (opts: any) => {
    opts.success({ latitude: 30.12345, longitude: 120.54321 })
  }
  ;(getUni() as any).chooseImage = vi.fn()
  ;(getUni() as any).saveImageToPhotosAlbum = vi.fn()
})

afterEach(() => {
  vi.useRealTimers()
})

describe('site/watermark-camera/index.vue 工程防伪水印相机', () => {
  it('初始化展示当前用户姓名、时钟与 GPS 经纬度位置', async () => {
    useUserStore().setUserInfo({ realName: '李工' } as any)
    const wrapper = mount(WatermarkCamera)
    await flushPromises()

    expect(wrapper.text()).toContain('李工')
    expect(wrapper.text()).toContain('中维智营 · 工程防伪影像')
    expect(wrapper.text()).toContain('北纬30.12345° 东经120.54321°')
    wrapper.unmount()
  })

  it('未选择项目时点击拍照弹出 toast 拦截', async () => {
    vi.mocked(getProjectList).mockResolvedValue({ code: 200, data: { records: [] } } as any)
    const toast = vi.fn()
    ;(getUni() as any).showToast = toast
    const wrapper = mount(WatermarkCamera)
    await flushPromises()

    wrapper.vm.projectId = null
    await wrapper.vm.handleCapture()

    expect(toast).toHaveBeenCalledWith(expect.objectContaining({ title: '请先选择项目' }))
    expect((getUni() as any).chooseImage).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('拍照成功后渲染预览图与重拍按钮', async () => {
    ;(getUni() as any).chooseImage = (opts: any) => {
      opts.success({ tempFilePaths: ['temp://photo-1.jpg'] })
    }
    const wrapper = mount(WatermarkCamera)
    await flushPromises()

    wrapper.vm.selectProject({ id: 1, projectName: '测试示范工程' })
    await wrapper.vm.handleCapture()
    await flushPromises()

    expect(wrapper.vm.previewImage).toBe('temp://photo-1.jpg')
    expect(wrapper.text()).toContain('重拍')
    expect(wrapper.text()).toContain('保存相册')

    // 点击重拍恢复
    wrapper.vm.resetPhoto()
    expect(wrapper.vm.previewImage).toBe('')
    wrapper.unmount()
  })
})
