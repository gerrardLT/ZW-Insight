/**
 * tender/register.vue + certificate.vue 账本补测（2026-08 账本全量补齐 M1）
 *
 * @matrix A5-01 四态标签+保证金千分位 / A5-02 项目筛选搜索重置 / A5-03 必填守卫 /
 *   A5-05 保证金 min=0 precision=2 / A5-07+13 仅 REGISTERED 显示编辑提交删除 /
 *   A5-08 提交取消不发请求 / A5-11 openDate<registerDate 前端校验（2026-08-21 缺陷#4 修复后翻正向）/
 *   A6-02 personName/certificateType 筛选 page/size 口径 / A6-03 必填三文案 /
 *   A6-08 到期早于发证前端无校验（源码实证钉住）/ A6-09 person 类型恒定（契约对齐后钉住）/
 *   A6-10 分页 [10,20,50]
 *
 * 分层纪律：纯前端行为断言（api 层 mock）；真实写路径/状态流转（A5-04/06/09/10/14、
 * A6-04/05/06/07）由 e2e-real a2-tender.spec.ts 覆盖。
 * 既有覆盖见 tender-register-crud / certificate-crud（crudPageSuite 六用例 + detail 回显）。
 */
import { describe, it, expect, vi, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const __testDir = dirname(fileURLToPath(import.meta.url))

const { mockPage, mockCreate, mockUpdate, mockDelete, mockSubmit, mockDetail, mockConfirm } = vi.hoisted(() => ({
  mockPage: vi.fn(async (): Promise<any> => ({ code: 200, data: { records: [], total: 0 } })),
  mockCreate: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockUpdate: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockDelete: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockSubmit: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockDetail: vi.fn(async (): Promise<any> => ({ code: 200, data: {} })),
  mockConfirm: vi.fn(async () => 'confirm'),
}))

vi.mock('@/api/tender', () => ({
  getTenderRegisterPage: mockPage, getTenderRegisterDetail: mockDetail, createTenderRegister: mockCreate,
  updateTenderRegister: mockUpdate, deleteTenderRegister: mockDelete, submitTenderRegister: mockSubmit,
  getCertificatePage: mockPage, createCertificate: mockCreate, updateCertificate: mockUpdate, deleteCertificate: mockDelete,
}))
vi.mock('@/api/project', () => ({
  getProjectList: vi.fn(async (): Promise<any> => ({ code: 200, data: [] })),
}))
vi.mock('element-plus', async (importOriginal) => {
  const actual: any = await importOriginal()
  return {
    ...actual,
    ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
    ElMessageBox: { ...actual.ElMessageBox, confirm: mockConfirm },
  }
})

import TenderRegister from '@/views/tender/register.vue'
import Certificate from '@/views/tender/certificate.vue'

let wrapper: any = null
afterEach(() => {
  if (wrapper) { try { wrapper.unmount() } catch { /* 忽略 */ } wrapper = null }
  vi.clearAllMocks()
})

describe('tender/register.vue 账本补测（@matrix A5）', () => {
  it('@matrix A5-01 四态标签渲染 + 保证金千分位', async () => {
    mockPage.mockResolvedValue({ code: 200, data: { records: [
      { id: 1, ownerCompany: '甲', status: 'REGISTERED', depositAmount: 123456 },
      { id: 2, ownerCompany: '乙', status: 'SUBMITTED', depositAmount: 0 },
      { id: 3, ownerCompany: '丙', status: 'WON', depositAmount: 50000 },
      { id: 4, ownerCompany: '丁', status: 'LOST', depositAmount: 50000 },
    ], total: 4 } })
    wrapper = mount(TenderRegister, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    const tags = wrapper.findAll('.el-table .el-tag').map((t: any) => t.text())
    expect(tags).toEqual(['报名中', '已投标', '中标', '未中标'])
    expect(wrapper.text()).toContain('123,456')
  })

  it('@matrix A5-02 搜索重置 page=1，重置清空 projectId', async () => {
    wrapper = mount(TenderRegister, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    const st = wrapper.vm.$.setupState
    st.queryParams.page = 3
    st.queryParams.projectId = 88
    mockPage.mockClear()
    st.handleSearch()
    await flushPromises()
    expect(mockPage).toHaveBeenCalledWith(expect.objectContaining({ page: 1, projectId: 88 }))
    mockPage.mockClear()
    st.handleReset()
    await flushPromises()
    expect(st.queryParams).toEqual({ page: 1, size: 10, projectId: undefined })
    expect(mockPage).toHaveBeenCalledWith(expect.objectContaining({ page: 1, projectId: undefined }))
  })

  it('@matrix A5-03 必填文案钉住 + 校验失败不发创建请求', async () => {
    wrapper = mount(TenderRegister, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    const st = wrapper.vm.$.setupState
    expect(st.formRules.projectId[0].message).toBe('请选择项目')
    expect(st.formRules.ownerCompany[0].message).toBe('请输入业主单位')
    st.handleAdd()
    await flushPromises()
    st.formRef = { validate: vi.fn(async () => { throw new Error('validation failed') }) }
    mockCreate.mockClear()
    await st.handleFormSubmit().catch(() => { /* validate reject 向外抛 */ })
    expect(mockCreate).not.toHaveBeenCalled()
    expect(st.submitLoading).toBe(false)
  })

  it('@matrix A5-05 保证金 input-number min=0 precision=2', async () => {
    wrapper = mount(TenderRegister, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    wrapper.vm.$.setupState.handleAdd()
    await flushPromises()
    const num = wrapper.findComponent({ name: 'ElInputNumber' })
    expect(num.exists()).toBe(true)
    expect(num.props('min')).toBe(0)
    expect(num.props('precision')).toBe(2)
  })

  it('@matrix A5-07/A5-13 仅 REGISTERED 行显示编辑/提交/删除，SUBMITTED 行三按钮均不渲染（2026-08-21 缺陷#3 编辑守卫修复后翻转）', async () => {
    mockPage.mockResolvedValue({ code: 200, data: { records: [
      { id: 1, ownerCompany: '甲', status: 'REGISTERED' },
      { id: 2, ownerCompany: '乙', status: 'SUBMITTED' },
    ], total: 2 } })
    wrapper = mount(TenderRegister, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    const rows = wrapper.findAll('.el-table__body-wrapper tbody tr')
    expect(rows).toHaveLength(2)
    const row0Btns = rows[0].findAll('button').map((b: any) => b.text())
    const row1Btns = rows[1].findAll('button').map((b: any) => b.text())
    expect(row0Btns).toContain('编辑')
    expect(row0Btns).toContain('提交')
    expect(row0Btns).toContain('删除')
    expect(row1Btns).not.toContain('编辑')
    expect(row1Btns).not.toContain('提交')
    expect(row1Btns).not.toContain('删除')
  })

  it('@matrix A5-08 提交确认取消不发 PUT submit', async () => {
    mockPage.mockResolvedValue({ code: 200, data: { records: [{ id: 71, status: 'REGISTERED' }], total: 1 } })
    wrapper = mount(TenderRegister, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    mockConfirm.mockRejectedValueOnce(new Error('cancel'))
    mockSubmit.mockClear()
    await wrapper.vm.$.setupState.handleSubmitApply({ id: 71, status: 'REGISTERED' }).catch(() => { /* confirm reject 向外抛 */ })
    expect(mockSubmit).not.toHaveBeenCalled()
  })

  it('@matrix A5-11 openDate<registerDate 前端校验生效（2026-08-21 缺陷#4 修复后翻正向：validator + disabled-date 双通道）', async () => {
    wrapper = mount(TenderRegister, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    const st = wrapper.vm.$.setupState
    const src = readFileSync(resolve(__testDir, '../views/tender/register.vue'), 'utf-8')
    expect(src).toContain(':disabled-date="disableBeforeRegisterDate"')
    // validator 行为：倒挂报错、正序/空值放行
    const rule = st.formRules.openDate[0]
    expect(rule.validator).toBeTypeOf('function')
    st.handleAdd()
    st.formData.registerDate = '2026-03-10'
    expect(await new Promise((res) => rule.validator(null, '2026-03-01', (e: any) => res(e)))).toBeInstanceOf(Error)
    expect(await new Promise((res) => rule.validator(null, '2026-03-20', (e: any) => res(e)))).toBeUndefined()
    expect(await new Promise((res) => rule.validator(null, '', (e: any) => res(e)))).toBeUndefined()
    // disabled-date：早于报名日期禁选（本地时区构造，避免 ISO 串按 UTC 解析偏移）
    expect(st.disableBeforeRegisterDate(new Date(2026, 2, 1))).toBe(true)
    expect(st.disableBeforeRegisterDate(new Date(2026, 2, 20))).toBe(false)
  })
})

describe('tender/certificate.vue 账本补测（@matrix A6）', () => {
  it('@matrix A6-02 personName/certificateType 筛选下发且页码重置（2026-08-21 契约对齐：请求口径 page/size，derivedStatus 客户端过滤不下发）', async () => {
    wrapper = mount(Certificate, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    const st = wrapper.vm.$.setupState
    st.queryParams.pageNum = 2
    st.queryParams.certificateType = '建造师'
    st.queryParams.personName = '张三'
    st.queryParams.derivedStatus = 'EXPIRING'
    mockPage.mockClear()
    st.handleSearch()
    await flushPromises()
    expect(mockPage).toHaveBeenCalledWith({ type: 'person', page: 1, size: 10, personName: '张三', certificateType: '建造师' })
  })

  it('@matrix A6-03 三条必填文案钉住 + 校验失败不发请求', async () => {
    wrapper = mount(Certificate, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    const st = wrapper.vm.$.setupState
    expect(st.formRules.personName[0].message).toBe('请输入持证人')
    expect(st.formRules.certificateType[0].message).toBe('请输入证件类型')
    expect(st.formRules.certificateNo[0].message).toBe('请输入证件编号')
    st.handleAdd()
    st.formRef = { validate: vi.fn(async () => { throw new Error('validation failed') }) }
    mockCreate.mockClear()
    await st.handleFormSubmit().catch(() => { /* validate reject 向外抛 */ })
    expect(mockCreate).not.toHaveBeenCalled()
  })

  it('@matrix A6-10 分页 page-sizes=[10,20,50]', async () => {
    wrapper = mount(Certificate, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    const pager = wrapper.findComponent({ name: 'ElPagination' })
    expect(pager.props('pageSizes')).toEqual([10, 20, 50])
  })

  it('@matrix A6-07 展示三态由前端基于 expireDate 派生：已过期/即将过期/有效 + 无到期日视为长期（2026-08-21 缺口#1 契约对齐后翻正向）', async () => {
    wrapper = mount(Certificate, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    const st = wrapper.vm.$.setupState
    const past = new Date(Date.now() - 86400000)
    const soon = new Date(Date.now() + 10 * 86400000)
    const far = new Date(Date.now() + 365 * 86400000)
    const fmt = (d: Date) => d.toISOString().slice(0, 10)
    expect(st.deriveStatus({ expireDate: fmt(past) })).toBe('EXPIRED')
    expect(st.deriveStatus({ expireDate: fmt(soon) })).toBe('EXPIRING')
    expect(st.deriveStatus({ expireDate: fmt(far) })).toBe('VALID')
    expect(st.deriveStatus({ expireDate: '' })).toBe('VALID')
    expect(st.derivedStatusTag({ expireDate: fmt(past) }).label).toBe('已过期')
    expect(st.derivedStatusTag({ expireDate: fmt(soon) }).label).toBe('即将过期')
    // derivedStatus 客户端过滤：仅渲染派生态命中的行
    st.tableData = [{ id: 1, expireDate: fmt(past) }, { id: 2, expireDate: fmt(far) }]
    st.queryParams.derivedStatus = 'EXPIRED'
    expect(st.filteredData).toHaveLength(1)
    expect(st.filteredData[0].id).toBe(1)
  })

  it('@matrix A6-08 到期早于发证前端无校验（源码实证现状钉住）', () => {
    const src = readFileSync(resolve(__testDir, '../views/tender/certificate.vue'), 'utf-8')
    expect(src).not.toMatch(/expireDate.*(>|<|before|after).*issueDate/)
    expect(src).not.toContain('validator')
  })

  it('@matrix A6-09 person 类型恒定：无 type 表单项，删除恒 deleteCertificate(\'person\', id)（2026-08-21 契约对齐后钉住）', () => {
    const src = readFileSync(resolve(__testDir, '../views/tender/certificate.vue'), 'utf-8')
    expect(src).toContain("deleteCertificate('person', row.id)")
    expect(src).not.toMatch(/prop="type"/)
  })
})
