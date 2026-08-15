/**
 * platform/tenant-type/index.vue 租户类型管理组件测试（2026-08-15 P3 方向1 第三批）
 *
 * 与标准 CRUD 工厂的差异（实证）：分页参数为 page/size（非 pageNum/pageSize）、
 * 无 isEdit 标记（编辑态以 formData.id 判定）、update 为双参 (id, formData)。
 * 故定制 7 用例，不走工厂。
 */
import { describe, it, expect, vi, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'

const { mockPage, mockCreate, mockUpdate, mockDelete } = vi.hoisted(() => ({
  mockPage: vi.fn(async (): Promise<any> => ({ code: 200, data: { records: [], total: 0 } })),
  mockCreate: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockUpdate: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockDelete: vi.fn(async (): Promise<any> => ({ code: 200 })),
}))

vi.mock('@/api/platform', () => ({
  getTenantTypePage: mockPage,
  createTenantType: mockCreate,
  updateTenantType: mockUpdate,
  deleteTenantType: mockDelete,
}))
vi.mock('element-plus', async (importOriginal) => {
  const actual: any = await importOriginal()
  return {
    ...actual,
    ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
    ElMessageBox: { ...actual.ElMessageBox, confirm: vi.fn(async () => 'confirm') },
  }
})

import TenantType from '@/views/platform/tenant-type/index.vue'

const RECORDS = [
  { id: 1, typeName: '标准版', durationDays: 365, sortOrder: 1, status: 1 },
  { id: 2, typeName: '企业版', durationDays: 730, sortOrder: 2, status: 1 },
]

let wrapper: any = null
afterEach(() => {
  if (wrapper) { try { wrapper.unmount() } catch { /* 忽略 */ } wrapper = null }
  vi.clearAllMocks()
})

async function mountPage(records = RECORDS) {
  mockPage.mockResolvedValue({ code: 200, data: { records, total: records.length } })
  wrapper = mount(TenantType, { global: { plugins: [ElementPlus] } })
  await flushPromises()
  return wrapper
}

describe('tenant-type/index.vue 租户类型管理', () => {
  it('挂载加载列表渲染行与 total', async () => {
    const w = await mountPage()
    expect(mockPage).toHaveBeenCalled()
    expect(w.findAll('.el-table__row')).toHaveLength(2)
    expect(w.text()).toContain('标准版')
  })

  it('搜索重置 page 并重新查询（page/size 参数口径）', async () => {
    const w = await mountPage()
    const st = w.vm.$.setupState
    st.queryParams.page = 3
    mockPage.mockClear()
    st.handleSearch()
    await flushPromises()
    expect(st.queryParams.page).toBe(1)
    expect((mockPage.mock.calls as any)[0][0]).toMatchObject({ page: 1, size: 10 })
  })

  it('重置清空条件（typeName/status 置 undefined）', async () => {
    const w = await mountPage()
    const st = w.vm.$.setupState
    st.queryParams.typeName = '脏值'
    st.queryParams.status = 0
    st.handleReset()
    await flushPromises()
    expect(st.queryParams).toEqual({ page: 1, size: 10, typeName: undefined, status: undefined })
  })

  it('必填规则配置：类型名称 + 有效期', async () => {
    const w = await mountPage([])
    const msgs = Object.values(w.vm.$.setupState.formRules).flat().map((r: any) => r.message)
    expect(msgs).toContain('请输入类型名称')
    expect(msgs).toContain('请选择有效期')
  })

  it('新增：无 id 走 createTenantType（默认值 durationDays=30/status=1）', async () => {
    const w = await mountPage([])
    const st = w.vm.$.setupState
    st.handleAdd()
    await flushPromises()
    expect(st.dialogVisible).toBe(true)
    expect(st.formData.durationDays).toBe(30) // 默认值钉住
    st.formData.typeName = '试用版'
    await st.handleFormSubmit()
    await flushPromises()
    expect(mockCreate).toHaveBeenCalledTimes(1)
    expect((mockCreate.mock.calls as any)[0][0]).toMatchObject({ typeName: '试用版', status: 1 })
    expect(mockUpdate).not.toHaveBeenCalled()
  })

  it('编辑：回显行数据 + 有 id 走 updateTenantType 双参 (id, formData)', async () => {
    const w = await mountPage()
    const st = w.vm.$.setupState
    st.handleEdit(RECORDS[0])
    await flushPromises()
    expect(st.formData.id).toBe(1)
    expect(st.formData.typeName).toBe('标准版')
    expect(st.dialogTitle).toBe('编辑租户类型')
    await st.handleFormSubmit()
    await flushPromises()
    expect(mockUpdate).toHaveBeenCalledTimes(1)
    expect((mockUpdate.mock.calls as any)[0][0]).toBe(1)
    expect((mockUpdate.mock.calls as any)[0][1]).toMatchObject({ typeName: '标准版' })
    expect(mockCreate).not.toHaveBeenCalled()
  })

  it('删除：确认后调 deleteTenantType 并刷新', async () => {
    const w = await mountPage()
    mockPage.mockClear()
    await w.vm.$.setupState.handleDelete(RECORDS[0])
    await flushPromises()
    expect(mockDelete).toHaveBeenCalledWith(1)
    expect(mockPage).toHaveBeenCalled()
  })
})
