/**
 * BatchImportDialog 组件测试（P0 差距收口 T1）
 *
 * 批量导入对话框：选文件 → 调 importData（moduleCode + params 透传）→ 展示结果。
 * 覆盖：extraQuery + projectId 合并透传、仅 projectId、部分成功触发 success、
 * 导入失败错误消息透传（不吞错）、模板下载。
 * 交互通过 el-upload on-change prop 与按钮点击驱动，不依赖组件内部暴露。
 */
import { describe, it, expect, vi, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'

vi.mock('element-plus', async (importOriginal) => {
  const actual = await importOriginal<typeof import('element-plus')>()
  return { ...actual, ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() } }
})
vi.mock('@/api/batch', () => ({
  importData: vi.fn(),
  downloadTemplate: vi.fn()
}))

import { importData, downloadTemplate } from '@/api/batch'
import { ElMessage } from 'element-plus'
import BatchImportDialog from '@/components/BatchImportDialog.vue'

const mockImport = vi.mocked(importData)
const mockTemplate = vi.mocked(downloadTemplate)

function mountDialog(props: Record<string, any> = {}) {
  return mount(BatchImportDialog, {
    props: { visible: false, moduleCode: 'LABOR_ROSTER', ...props },
    global: { plugins: [ElementPlus] },
    attachTo: document.body
  })
}

async function openDialog(props: Record<string, any> = {}) {
  const wrapper = mountDialog(props)
  await wrapper.setProps({ visible: true })
  await flushPromises()
  return wrapper
}

function pickFile(wrapper: any) {
  const file = new File(['dummy'], 'roster.xlsx', { type: 'application/vnd.openxmlformats-officedocument.sheet' })
  const upload = wrapper.findComponent({ name: 'ElUpload' })
  upload.props('onChange')({ raw: file })
  return file
}

async function clickImport(wrapper: any) {
  const btn = wrapper.findAll('button').find((b: any) => b.text().includes('开始导入'))
  await btn!.trigger('click')
  await flushPromises()
}

afterEach(() => {
  vi.clearAllMocks()
  document.body.innerHTML = ''
})

describe('BatchImportDialog 组件', () => {
  it('导入：projectId 与 extraQuery 合并透传 importData', async () => {
    mockImport.mockResolvedValue({ data: { totalRows: 2, successRows: 2, failedRows: 0, errors: [] } } as any)
    const wrapper = await openDialog({ projectId: 10, extraQuery: { teamId: 88 } })
    const file = pickFile(wrapper)
    await wrapper.vm.$nextTick()

    await clickImport(wrapper)

    expect(mockImport).toHaveBeenCalledWith('LABOR_ROSTER', file, { teamId: 88, projectId: 10 })
    expect(wrapper.emitted('success')).toBeTruthy()
    wrapper.unmount()
  })

  it('导入：无 extraQuery 时仅携带 projectId', async () => {
    mockImport.mockResolvedValue({ data: { totalRows: 1, successRows: 1, failedRows: 0 } } as any)
    const wrapper = await openDialog({ projectId: 5 })
    const file = pickFile(wrapper)
    await wrapper.vm.$nextTick()

    await clickImport(wrapper)

    expect(mockImport).toHaveBeenCalledWith('LABOR_ROSTER', file, { projectId: 5 })
    wrapper.unmount()
  })

  it('部分成功：successRows>0 仍触发 success 事件并展示失败明细', async () => {
    mockImport.mockResolvedValue({ data: { totalRows: 3, successRows: 2, failedRows: 1, errors: [{ row: 2, message: '身份证号已存在' }] } } as any)
    const wrapper = await openDialog()
    pickFile(wrapper)
    await wrapper.vm.$nextTick()

    await clickImport(wrapper)

    expect(wrapper.emitted('success')).toBeTruthy()
    expect(wrapper.text()).toContain('部分导入成功')
    expect(wrapper.text()).toContain('身份证号已存在')
    wrapper.unmount()
  })

  it('导入失败：错误消息透传（不吞错）', async () => {
    mockImport.mockRejectedValue({ response: { data: { message: '模块未注册' } } })
    const wrapper = await openDialog()
    pickFile(wrapper)
    await wrapper.vm.$nextTick()

    await clickImport(wrapper)

    expect(ElMessage.error).toHaveBeenCalledWith('模块未注册')
    expect(wrapper.emitted('success')).toBeFalsy()
    wrapper.unmount()
  })

  it('未选择文件：导入按钮禁用，不发起请求', async () => {
    const wrapper = await openDialog()
    const btn = wrapper.findAll('button').find((b: any) => b.text().includes('开始导入'))
    expect(btn!.attributes('disabled')).toBeDefined()
    expect(mockImport).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('模板下载：调用 downloadTemplate 并触发浏览器下载', async () => {
    mockTemplate.mockResolvedValue(new ArrayBuffer(8) as any)
    const createObjectURL = vi.fn(() => 'blob:mock')
    const revokeObjectURL = vi.fn()
    vi.stubGlobal('URL', { ...URL, createObjectURL, revokeObjectURL })
    HTMLAnchorElement.prototype.click = vi.fn()

    const wrapper = await openDialog()
    const btn = wrapper.findAll('button').find((b: any) => b.text().includes('下载导入模板'))
    await btn!.trigger('click')
    await flushPromises()

    expect(mockTemplate).toHaveBeenCalledWith('LABOR_ROSTER')
    expect(createObjectURL).toHaveBeenCalled()
    expect(revokeObjectURL).toHaveBeenCalledWith('blob:mock')

    wrapper.unmount()
    vi.unstubAllGlobals()
  })
})
