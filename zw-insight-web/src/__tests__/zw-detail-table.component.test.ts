/**
 * ZwDetailTable 组件测试（3.4：自 budget/index 上提的可编辑明细表容器）。
 *
 * 覆盖：添加行/模板导入/重置事件、行合计展示、插槽列透传、
 * importable=false 时不渲染导入按钮（新增态无预算 id 的业务约束）。
 */
import { describe, it, expect, vi, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus, { ElTableColumn } from 'element-plus'
import { h } from 'vue'
import ZwDetailTable from '@/components/ZwDetailTable.vue'

const ROWS = [
  { itemName: '钢材', amount: 100 },
  { itemName: '混凝土', amount: 50 },
]

let wrapper: any = null
afterEach(() => {
  if (wrapper) { try { wrapper.unmount() } catch { /* 忽略 */ } wrapper = null }
  vi.clearAllMocks()
})

function mountTable(props: Record<string, any> = {}, withColumn = true) {
  wrapper = mount(ZwDetailTable, {
    props: { modelValue: ROWS, ...props },
    slots: withColumn ? {
      default: () => h(ElTableColumn, { prop: 'itemName', label: '名称' }),
    } : {},
    global: { plugins: [ElementPlus] },
  })
  return wrapper
}

describe('ZwDetailTable 明细表容器', () => {
  it('默认渲染添加行与重置按钮，不渲染模板导入', () => {
    const w = mountTable()
    const texts = w.findAll('button').map((b: any) => b.text())
    expect(texts).toContain('添加明细行')
    expect(texts).toContain('重置')
    expect(texts).not.toContain('模板导入')
  })

  it('importable=true 时渲染模板导入并触发 import 事件', async () => {
    const w = mountTable({ importable: true })
    const importBtn = w.findAll('button').find((b: any) => b.text() === '模板导入')
    expect(importBtn).toBeTruthy()
    await importBtn!.trigger('click')
    expect(w.emitted('import')).toHaveLength(1)
  })

  it('添加行只发事件，不自行构造行（行模板由页面决定）', async () => {
    const w = mountTable()
    const addBtn = w.findAll('button').find((b: any) => b.text() === '添加明细行')
    await addBtn!.trigger('click')
    expect(w.emitted('add')).toHaveLength(1)
    expect(w.emitted('update:modelValue')).toBeUndefined()
  })

  it('重置清空 v-model 并触发 reset（页面可做二次清理）', async () => {
    const w = mountTable()
    const resetBtn = w.findAll('button').find((b: any) => b.text() === '重置')
    await resetBtn!.trigger('click')
    expect(w.emitted('update:modelValue')?.[0]).toEqual([[]])
    expect(w.emitted('reset')).toHaveLength(1)
  })

  it('行合计按页面传入文案展示，无文案时不渲染合计区', () => {
    const w1 = mountTable({ summaryText: '150 元（提交后总额以明细合计为准）' })
    expect(w1.find('.zw-detail-table__summary').text()).toContain('明细合计：150 元')
    const w2 = mountTable()
    expect(w2.find('.zw-detail-table__summary').exists()).toBe(false)
  })

  it('业务列通过默认插槽注入并渲染行数据', async () => {
    const w = mountTable()
    // el-table 的列注册在 mounted 后异步完成，需等一次微任务再断言表体
    await flushPromises()
    expect(w.text()).toContain('钢材')
    expect(w.text()).toContain('混凝土')
  })
})
