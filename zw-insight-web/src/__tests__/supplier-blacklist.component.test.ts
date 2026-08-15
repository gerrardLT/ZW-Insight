/**
 * basedata/supplier-blacklist.vue 供应商黑名单组件测试（2026-08-15 P3 方向1 第三批）
 *
 * 页面为「加入/移出」语义而非标准 CRUD（无编辑/搜索重置，handleRemove 移出黑名单），
 * 不适配 CRUD 工厂，定制 4 用例：
 * - 挂载渲染行与 total
 * - 必填规则配置层断言（happy-dom validate 恒 resolve 实证）
 * - 加入黑名单：提交组装 formData 调 create 并刷新
 * - 移出黑名单：确认后调 delete 并刷新
 */
import { describe, it, expect, vi, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'

const { mockPage, mockCreate, mockDelete } = vi.hoisted(() => ({
  mockPage: vi.fn(async (): Promise<any> => ({ code: 200, data: { records: [], total: 0 } })),
  mockCreate: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockDelete: vi.fn(async (): Promise<any> => ({ code: 200 })),
}))

vi.mock('@/api/basedata', () => ({
  getSupplierBlacklistPage: mockPage,
  createSupplierBlacklist: mockCreate,
  deleteSupplierBlacklist: mockDelete,
}))
vi.mock('element-plus', async (importOriginal) => {
  const actual: any = await importOriginal()
  return {
    ...actual,
    ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
    ElMessageBox: { ...actual.ElMessageBox, confirm: vi.fn(async () => 'confirm') },
  }
})

import SupplierBlacklist from '@/views/basedata/supplier-blacklist.vue'

const RECORDS = [
  { id: 1, supplierName: '失信供应商A', reason: '多次逾期交货', createTime: '2026-07-01' },
  { id: 2, supplierName: '失信供应商B', reason: '质量事故', createTime: '2026-07-15' },
]

let wrapper: any = null
afterEach(() => {
  if (wrapper) { try { wrapper.unmount() } catch { /* 忽略 */ } wrapper = null }
  vi.clearAllMocks()
})

async function mountPage(records = RECORDS) {
  mockPage.mockResolvedValue({ code: 200, data: { records, total: records.length } })
  wrapper = mount(SupplierBlacklist, { global: { plugins: [ElementPlus] } })
  await flushPromises()
  return wrapper
}

describe('supplier-blacklist.vue 供应商黑名单', () => {
  it('挂载加载列表渲染行与 total', async () => {
    const w = await mountPage()
    expect(mockPage).toHaveBeenCalled()
    expect(w.findAll('.el-table__row')).toHaveLength(2)
    expect(w.text()).toContain('失信供应商A')
  })

  it('必填规则配置：供应商名称 + 加入原因', async () => {
    const w = await mountPage([])
    const rules = w.vm.$.setupState.formRules
    const msgs = Object.values(rules).flat().map((r: any) => r.message)
    expect(msgs).toContain('请输入供应商名称')
    expect(msgs).toContain('请输入加入原因')
  })

  it('加入黑名单：提交组装 formData 调 create 并刷新列表', async () => {
    const w = await mountPage([])
    const st = w.vm.$.setupState
    st.handleAdd()
    await flushPromises()
    expect(st.dialogVisible).toBe(true)
    st.formData.supplierName = '新失信供应商'
    st.formData.reason = '合同违约'
    mockPage.mockClear()
    await st.handleFormSubmit()
    await flushPromises()
    expect(mockCreate).toHaveBeenCalledTimes(1)
    expect((mockCreate.mock.calls as any)[0][0]).toMatchObject({ supplierName: '新失信供应商', reason: '合同违约' })
    expect(mockPage).toHaveBeenCalled() // 提交后刷新
    expect(st.dialogVisible).toBe(false)
  })

  it('移出黑名单：确认后调 delete 并刷新列表', async () => {
    const w = await mountPage()
    mockPage.mockClear()
    await w.vm.$.setupState.handleRemove(RECORDS[0])
    await flushPromises()
    expect(mockDelete).toHaveBeenCalledWith(1)
    expect(mockPage).toHaveBeenCalled()
  })
})
