/**
 * useColumnSetting composable 测试
 * 覆盖：持久化往返、reset、默认可见性
 */
import { describe, it, expect, beforeEach } from 'vitest'
import { useColumnSetting, type ColumnDef } from '@/composables/useColumnSetting'

const TEST_COLUMNS: ColumnDef[] = [
  { key: 'projectName', label: '项目名称' },
  { key: 'contractAmount', label: '合同金额' },
  { key: 'status', label: '状态', defaultVisible: false },
  { key: 'createdAt', label: '创建时间' },
]

describe('useColumnSetting', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  it('默认全可见（defaultVisible:false 除外）', () => {
    const { visible } = useColumnSetting('test-table', TEST_COLUMNS)
    expect(visible.value).toEqual([true, true, false, true])
  })

  it('toggle 后自动持久化到 localStorage', () => {
    const { visible, toggle } = useColumnSetting('test-table', TEST_COLUMNS)
    toggle(0) // 隐藏第 0 列
    expect(visible.value[0]).toBe(false)
    // localStorage 已持久化
    const saved = JSON.parse(localStorage.getItem('zw-col-setting:test-table') || '[]')
    expect(saved).toEqual([false, true, false, true])
  })

  it('新实例从 localStorage 恢复状态（持久化往返）', () => {
    const first = useColumnSetting('test-table', TEST_COLUMNS)
    first.toggle(0)
    first.toggle(2) // 显示状态列

    // 模拟重新挂载
    const second = useColumnSetting('test-table', TEST_COLUMNS)
    expect(second.visible.value).toEqual([false, true, true, true])
  })

  it('reset 恢复默认可见性', () => {
    const { visible, toggle, reset } = useColumnSetting('test-table', TEST_COLUMNS)
    toggle(0)
    toggle(2)
    reset()
    expect(visible.value).toEqual([true, true, false, true])
  })

  it('visibleColumns computed 只包含可见列', () => {
    const { visibleColumns, toggle } = useColumnSetting('test-table', TEST_COLUMNS)
    toggle(1) // 隐藏合同金额
    expect(visibleColumns.value.map(c => c.key)).toEqual(['projectName', 'createdAt'])
  })

  it('localStorage 数据损坏时静默回退默认值', () => {
    localStorage.setItem('zw-col-setting:broken-table', '{invalid json')
    const { visible } = useColumnSetting('broken-table', TEST_COLUMNS)
    expect(visible.value).toEqual([true, true, false, true])
  })
})
