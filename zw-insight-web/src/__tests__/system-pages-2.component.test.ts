/**
 * system 域配置/备份/编号规则页组件测试（2026-08-15 P3 收尾批 8b）
 */
import { describe, it, expect, vi, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'

const {
  mockConfigByGroup, mockBatchUpdate, mockResetDefault,
  mockBackupPage, mockExecuteBackup, mockDownload, mockDeleteBackup, mockRestore,
  mockSnList, mockSnCreate, mockSnUpdate, mockSnDelete, mockGenerate,
  mockInfo,
} = vi.hoisted(() => ({
  mockConfigByGroup: vi.fn(async (): Promise<any> => ({ code: 200, data: [] })),
  mockBatchUpdate: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockResetDefault: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockBackupPage: vi.fn(async (): Promise<any> => ({ code: 200, data: { records: [], total: 0 } })),
  mockExecuteBackup: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockDownload: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockDeleteBackup: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockRestore: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockSnList: vi.fn(async (): Promise<any> => ({ code: 200, data: [] })),
  mockSnCreate: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockSnUpdate: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockSnDelete: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockGenerate: vi.fn(async (): Promise<any> => ({ code: 200, data: 'PRJ-20260815-0001' })),
  mockInfo: vi.fn(),
}))

vi.mock('@/api/system', () => ({
  getConfigByGroup: mockConfigByGroup, batchUpdateConfig: mockBatchUpdate, resetConfigToDefault: mockResetDefault,
}))
vi.mock('@/api/backup', () => ({
  getBackupPage: mockBackupPage, executeBackup: mockExecuteBackup, downloadBackup: mockDownload,
  deleteBackup: mockDeleteBackup, restoreBackup: mockRestore,
}))
vi.mock('@/api/file', () => ({
  getSerialNumberList: mockSnList, createSerialNumber: mockSnCreate, updateSerialNumber: mockSnUpdate,
  deleteSerialNumber: mockSnDelete, generateSerialNumber: mockGenerate,
}))
vi.mock('element-plus', async (importOriginal) => {
  const actual: any = await importOriginal()
  return {
    ...actual,
    ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn(), info: mockInfo },
    ElMessageBox: { ...actual.ElMessageBox, confirm: vi.fn(async () => 'confirm') },
  }
})

import Config from '@/views/system/config/index.vue'
import Backup from '@/views/system/backup/index.vue'
import SerialNumber from '@/views/system/serial-number/index.vue'

let wrapper: any = null
afterEach(() => {
  if (wrapper) { try { wrapper.unmount() } catch { /* 忽略 */ } wrapper = null }
  vi.clearAllMocks()
})

const CONFIG_ITEMS = [
  { configKey: 'login.max-fail', configName: '最大失败次数', configValue: '5', valueType: 'NUMBER', valueRange: '1-10' },
  { configKey: 'captcha.enabled', configName: '验证码开关', configValue: 'true', valueType: 'BOOLEAN', valueRange: null },
  { configKey: 'site.name', configName: '站点名称', configValue: 'ZW', valueType: 'STRING', valueRange: null },
]

describe('system/config/index.vue 系统配置', () => {
  async function mountPage() {
    mockConfigByGroup.mockResolvedValue({ code: 200, data: CONFIG_ITEMS })
    wrapper = mount(Config, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    return wrapper
  }

  it('挂载按默认分组加载并解析值类型（NUMBER→数/BOOLEAN→布尔/STRING→串）', async () => {
    const w = await mountPage()
    expect(mockConfigByGroup).toHaveBeenCalledWith('security')
    const st = w.vm.$.setupState
    expect(st.formModel['login.max-fail']).toBe(5)
    expect(st.formModel['captcha.enabled']).toBe(true)
    expect(st.formModel['site.name']).toBe('ZW')
  })

  it('保存仅提交变更项（diff 原始值）', async () => {
    const w = await mountPage()
    const st = w.vm.$.setupState
    st.formModel['login.max-fail'] = 8
    mockBatchUpdate.mockClear()
    await st.handleSave()
    await flushPromises()
    expect(mockBatchUpdate).toHaveBeenCalledTimes(1)
    expect((mockBatchUpdate.mock.calls as any)[0][0]).toEqual([
      { configKey: 'login.max-fail', configValue: '8' },
    ])
  })

  it('无修改保存 → 提示且不发请求', async () => {
    const w = await mountPage()
    mockBatchUpdate.mockClear()
    mockInfo.mockClear()
    await w.vm.$.setupState.handleSave()
    await flushPromises()
    expect(mockInfo).toHaveBeenCalledWith('没有需要保存的修改')
    expect(mockBatchUpdate).not.toHaveBeenCalled()
  })

  it('tab 切换重新加载对应分组', async () => {
    const w = await mountPage()
    mockConfigByGroup.mockClear()
    await w.vm.$.setupState.handleTabChange('budget')
    await flushPromises()
    expect(mockConfigByGroup).toHaveBeenCalledWith('budget')
  })
})

describe('system/backup/index.vue 备份管理', () => {
  async function mountPage(records: any[] = []) {
    mockBackupPage.mockResolvedValue({ code: 200, data: { records, total: records.length } })
    wrapper = mount(Backup, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    return wrapper
  }

  it('挂载加载备份记录', async () => {
    await mountPage([{ id: 1, fileName: 'bk1.sql', fileSize: 2048 }])
    expect(mockBackupPage).toHaveBeenCalled()
    expect(wrapper.vm.$.setupState.tableData).toHaveLength(1)
  })

  it('formatSize：单位换算与空值兜底', async () => {
    await mountPage()
    const st = wrapper.vm.$.setupState
    expect(st.formatSize(512)).toBe('512 B')
    expect(st.formatSize(2048)).toBe('2.0 KB')
    expect(st.formatSize(5 * 1024 * 1024)).toBe('5.0 MB')
    expect(st.formatSize(0)).toBe('-')
    expect(st.formatSize(undefined)).toBe('-')
  })

  it('formatDuration：毫秒/秒/分钟分级格式化', async () => {
    await mountPage()
    const st = wrapper.vm.$.setupState
    expect(st.formatDuration(500)).toBe('500 ms')
    expect(st.formatDuration(2500)).toBe('2.5 s')
    expect(st.formatDuration(125000)).toBe('2m 5s')
    expect(st.formatDuration(0)).toBe('-')
  })

  it('手动备份：确认后调 executeBackup 并刷新', async () => {
    await mountPage()
    mockBackupPage.mockClear()
    await wrapper.vm.$.setupState.handleBackup()
    await flushPromises()
    expect(mockExecuteBackup).toHaveBeenCalled()
    expect(mockBackupPage).toHaveBeenCalled()
  })

  it('恢复：确认后调 restoreBackup 并清理 restoringId', async () => {
    await mountPage()
    const st = wrapper.vm.$.setupState
    await st.handleRestore({ id: 7, fileName: 'bk7.sql' })
    await flushPromises()
    expect(mockRestore).toHaveBeenCalledWith(7)
    expect(st.restoringId).toBeNull()
  })
})

describe('system/serial-number/index.vue 编号规则', () => {
  async function mountPage(rules: any[] = []) {
    mockSnList.mockResolvedValue({ code: 200, data: rules })
    wrapper = mount(SerialNumber, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    return wrapper
  }

  it('挂载加载规则列表', async () => {
    await mountPage([{ id: 1, businessType: 'PROJECT', rulePrefix: 'PRJ' }])
    expect(mockSnList).toHaveBeenCalled()
    expect(wrapper.vm.$.setupState.tableData).toHaveLength(1)
  })

  it('新增走 create、编辑走 update 双参 (id, formData)', async () => {
    await mountPage()
    const st = wrapper.vm.$.setupState
    st.handleAdd()
    await flushPromises()
    st.formData.businessType = 'PROJECT'
    st.formData.rulePrefix = 'PRJ'
    st.formData.dateFormat = 'yyyyMMdd'
    await st.handleSubmit()
    await flushPromises()
    expect(mockSnCreate).toHaveBeenCalledTimes(1)
    expect(mockSnUpdate).not.toHaveBeenCalled()
    st.handleEdit({ id: 3, businessType: 'CONTRACT', rulePrefix: 'HT', dateFormat: 'yyyyMMdd', seqLength: 4, resetPeriod: '', description: '' })
    await flushPromises()
    await st.handleSubmit()
    await flushPromises()
    expect(mockSnUpdate).toHaveBeenCalledTimes(1)
    expect((mockSnUpdate.mock.calls as any)[0][0]).toBe(3)
  })

  it('预览：调 generateSerialNumber(businessType)', async () => {
    await mountPage()
    await wrapper.vm.$.setupState.handlePreview({ businessType: 'PROJECT' })
    await flushPromises()
    expect(mockGenerate).toHaveBeenCalledWith('PROJECT')
  })

  it('删除：确认后调 deleteSerialNumber 并刷新', async () => {
    await mountPage()
    mockSnList.mockClear()
    await wrapper.vm.$.setupState.handleDelete({ id: 9 })
    await flushPromises()
    expect(mockSnDelete).toHaveBeenCalledWith(9)
    expect(mockSnList).toHaveBeenCalled()
  })
})
