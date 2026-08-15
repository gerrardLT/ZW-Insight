/**
 * material/stock.vue 库存查询页组件测试（2026-08-15 P3 方向1 第三批）
 *
 * 只读查询页（无新增/编辑/删除），核心业务逻辑为库存预警状态标签
 * （stockQuantity <= minStock → 库存不足 danger），定制 4 用例。
 */
import { describe, it, expect, vi, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'

const { mockPage } = vi.hoisted(() => ({
  mockPage: vi.fn(async (): Promise<any> => ({ code: 200, data: { records: [], total: 0 } })),
}))

vi.mock('@/api/material', () => ({
  getMaterialStockPage: mockPage,
}))

import Stock from '@/views/material/stock.vue'

let wrapper: any = null
afterEach(() => {
  if (wrapper) { try { wrapper.unmount() } catch { /* 忽略 */ } wrapper = null }
  vi.clearAllMocks()
})

async function mountPage(records: any[]) {
  mockPage.mockResolvedValue({ code: 200, data: { records, total: records.length } })
  wrapper = mount(Stock, { global: { plugins: [ElementPlus] } })
  await flushPromises()
  return wrapper
}

describe('stock.vue 库存查询', () => {
  it('挂载加载并渲染行与库存数据', async () => {
    const w = await mountPage([
      { materialName: '螺纹钢', specification: 'HRB400', unit: '吨', stockQuantity: 100, minStock: 20, projectName: '滨江花园一期', updatedAt: '2026-08-01' },
    ])
    expect(mockPage).toHaveBeenCalled()
    expect(w.text()).toContain('螺纹钢')
    expect(w.text()).toContain('滨江花园一期')
  })

  it('预警状态标签：库存<=最低库存显示「库存不足」，否则「正常」', async () => {
    const w = await mountPage([
      { materialName: '低库存材料', stockQuantity: 5, minStock: 10 },
      { materialName: '充足材料', stockQuantity: 50, minStock: 10 },
      { materialName: '无预警线材料', stockQuantity: 1, minStock: null }, // minStock 为空视为正常
    ])
    expect(w.text()).toContain('库存不足')
    expect(w.text()).toContain('正常')
    const tags = w.findAll('.el-tag')
    expect(tags[0].classes().join(' ')).toContain('el-tag--danger')
    expect(tags[1].classes().join(' ')).toContain('el-tag--success')
    expect(tags[2].classes().join(' ')).toContain('el-tag--success')
  })

  it('搜索重置 pageNum 并带条件重新查询', async () => {
    const w = await mountPage([])
    const st = w.vm.$.setupState
    st.queryParams.materialName = '钢筋'
    st.queryParams.warning = 'LOW'
    st.queryParams.pageNum = 3
    mockPage.mockClear()
    st.handleSearch()
    await flushPromises()
    expect(st.queryParams.pageNum).toBe(1)
    expect((mockPage.mock.calls as any)[0][0]).toMatchObject({ pageNum: 1, materialName: '钢筋', warning: 'LOW' })
  })

  it('重置清空全部条件（含 warning 下拉）', async () => {
    const w = await mountPage([])
    const st = w.vm.$.setupState
    st.queryParams.materialName = '脏值'
    st.queryParams.projectName = '脏项目'
    st.queryParams.warning = 'LOW'
    st.handleReset()
    await flushPromises()
    expect(st.queryParams).toEqual({ pageNum: 1, pageSize: 10, materialName: '', projectName: '', warning: '' })
  })
})
