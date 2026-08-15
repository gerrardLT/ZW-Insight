/**
 * 通用 CRUD 列表页组件测试工厂（2026-08-15 P3 长尾补测基建）
 *
 * 适用于同构 CRUD 页（搜索+新增/编辑弹窗+删除确认，supplier/material/team 等）。
 * 各测试文件自行 vi.mock 自己的 api 模块与 element-plus（ElMessageBox.confirm resolve），
 * 将 mock fn 传入本工厂生成 6 个标准用例：
 *   1 挂载加载列表渲染行+total
 *   2 搜索重置 pageNum 重新查询
 *   3 重置清空条件
 *   4 新增必填守卫（不触发 create）
 *   5 编辑回显 formData
 *   6 删除确认后调 delete 并刷新
 *
 * 纪律：afterEach unmount（happy-dom DOM 累积退化实证）；ElMessage/ElMessageBox partial mock。
 */
import { describe, it, expect, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'

export interface CrudSuiteOpts {
  title: string
  component: any
  pageMock: any
  createMock: any
  updateMock: any
  deleteMock: any
  addButtonText: string
  requiredError: string
  requiredField?: string
  /** 删除 API 的期望实参（默认 [row.id]；部分页为双参如 certificate 的 (type, id)） */
  deleteExpectedArgs?: (row: any) => any[]
  records: any[]
  total?: number
}

export function crudPageSuite(o: CrudSuiteOpts) {
  let wrapper: any = null

  async function mountPage() {
    wrapper = mount(o.component, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    return wrapper
  }
  const st = () => wrapper.vm.$.setupState

  afterEach(() => {
    if (wrapper) {
      try { wrapper.unmount() } catch { /* 忽略 */ }
      wrapper = null
    }
  })

  describe(`${o.title}（CRUD 页标准用例）`, () => {
    it('挂载加载列表并渲染行与 total', async () => {
      o.pageMock.mockResolvedValue({ code: 200, data: { records: o.records, total: o.total ?? o.records.length } })
      const w = await mountPage()
      expect(o.pageMock).toHaveBeenCalled()
      const rows = w.findAll('.el-table__row')
      expect(rows.length).toBe(o.records.length)
      expect(w.text()).toContain(String(o.total ?? o.records.length))
    })

    it('搜索重置 pageNum 并重新查询', async () => {
      o.pageMock.mockResolvedValue({ code: 200, data: { records: o.records, total: o.records.length } })
      const w = await mountPage()
      o.pageMock.mockClear()
      st().queryParams.pageNum = 3
      st().handleSearch()
      await flushPromises()
      expect(st().queryParams.pageNum).toBe(1)
      expect(o.pageMock).toHaveBeenCalled()
      const arg = o.pageMock.mock.calls[0][0]
      expect(arg.pageNum).toBe(1)
    })

    it('重置清空搜索条件', async () => {
      o.pageMock.mockResolvedValue({ code: 200, data: { records: [], total: 0 } })
      const w = await mountPage()
      const keys = Object.keys(st().queryParams)
      // 制造脏条件
      for (const k of keys) {
        if (typeof st().queryParams[k] === 'string') st().queryParams[k] = '脏值'
      }
      st().handleReset()
      await flushPromises()
      for (const k of keys) {
        if (typeof st().queryParams[k] === 'string') expect(st().queryParams[k]).toBe('')
      }
      expect(st().queryParams.pageNum).toBe(1)
    })

    it('必填守卫配置 + 提交组装 formData 调 create', async () => {
      o.pageMock.mockResolvedValue({ code: 200, data: { records: [], total: 0 } })
      const w = await mountPage()
      // happy-dom 下 el-form.validate 恒 resolve（字段未注册实证），必填守卫以 rules 配置层断言
      const rules = st().formRules
      const requiredRules = Object.values(rules).flat() as any[]
      const requiredMsgs = requiredRules.filter((r: any) => r.required).map((r: any) => r.message)
      expect(requiredMsgs, '应配置必填规则').toContain(o.requiredError)
      // 正向：提交组装 formData 调 create
      o.createMock.mockResolvedValue({ code: 200 })
      st().handleAdd()
      await flushPromises()
      st().formData[o.requiredField ?? 'id'] = undefined
      await st().handleFormSubmit()
      await flushPromises()
      expect(o.createMock).toHaveBeenCalledTimes(1)
      expect(o.createMock.mock.calls[0][0]).toMatchObject(st().formData)
    })

    it('编辑回显 formData 与行一致', async () => {
      o.pageMock.mockResolvedValue({ code: 200, data: { records: o.records, total: o.records.length } })
      const w = await mountPage()
      st().handleEdit(o.records[0])
      await flushPromises()
      expect(st().isEdit).toBe(true)
      expect(st().formData.id).toBe(o.records[0].id)
      expect(st().dialogVisible).toBe(true)
    })

    it('删除确认后调 delete 并刷新', async () => {
      o.pageMock.mockResolvedValue({ code: 200, data: { records: o.records, total: o.records.length } })
      o.deleteMock.mockResolvedValue({ code: 200 })
      const w = await mountPage()
      o.pageMock.mockClear()
      await st().handleDelete(o.records[0])
      await flushPromises()
      const expectedArgs = o.deleteExpectedArgs ? o.deleteExpectedArgs(o.records[0]) : [o.records[0].id]
      expect(o.deleteMock).toHaveBeenCalledWith(...expectedArgs)
      expect(o.pageMock).toHaveBeenCalled()
    })
  })
}
