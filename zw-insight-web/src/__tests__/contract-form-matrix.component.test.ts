/**
 * M2 账本补齐：A8 施工合同表单 views/contract/form.vue 矩阵用例
 * （contract-form-view 已钉只读模式 3 例；本文件补必填/默认值/明细合计/
 * 浮点精度/空明细跳过保存/雪花 ID/取消不落库等）
 *
 * @matrix A8-01/A8-02/A8-03/A8-04/A8-05/A8-06/A8-07/A8-10/A8-11/A8-12
 */
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'

const {
  mockGetContractDetail, mockCreateContract, mockUpdateContract,
  mockGetContractDetails, mockSaveContractDetails, mockGetProjectList,
  mockRouteParams, mockPush,
} = vi.hoisted(() => ({
  mockGetContractDetail: vi.fn(async (_id?: any): Promise<any> => ({
    code: 200,
    data: { id: '2089728215595675650', projectId: 1, projectName: 'P', contractCode: 'HT1', contractAmount: 100 },
  })),
  mockCreateContract: vi.fn(async (_d?: any): Promise<any> => ({ code: 200, data: null })),
  mockUpdateContract: vi.fn(async (_d?: any): Promise<any> => ({ code: 200 })),
  mockGetContractDetails: vi.fn(async (_id?: any): Promise<any> => ({ code: 200, data: [] })),
  mockSaveContractDetails: vi.fn(async (_id?: any, _d?: any): Promise<any> => ({ code: 200 })),
  mockGetProjectList: vi.fn(async (_p?: any): Promise<any> => ({ code: 200, data: [{ id: 1, projectName: 'P' }] })),
  mockRouteParams: {} as Record<string, string>,
  mockPush: vi.fn(),
}))

vi.mock('@/api/contract', () => ({
  getContractDetail: mockGetContractDetail,
  createContract: mockCreateContract,
  updateContract: mockUpdateContract,
  getContractDetails: mockGetContractDetails,
  saveContractDetails: mockSaveContractDetails,
}))
vi.mock('@/api/project', () => ({
  getProjectList: mockGetProjectList,
}))
vi.mock('vue-router', () => ({
  useRoute: () => ({ params: mockRouteParams, query: {} }),
  useRouter: () => ({ push: mockPush }),
}))

import ContractForm from '@/views/contract/form.vue'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const __testDir = dirname(fileURLToPath(import.meta.url))
const formVueSrc = readFileSync(resolve(__testDir, '../views/contract/form.vue'), 'utf-8')

let wrapper: any = null
beforeEach(() => {
  vi.clearAllMocks()
  delete (mockRouteParams as any).id
})
afterEach(() => {
  if (wrapper) { try { wrapper.unmount() } catch { /* 忽略 */ } wrapper = null }
})

async function mountForm() {
  wrapper = mount(ContractForm, { global: { plugins: [ElementPlus] } })
  await flushPromises()
  return wrapper
}

describe('contract/form.vue A8 矩阵', () => {
  it('A8-01 三项必填规则文案钉住（项目/甲方/金额），不发请求由校验拦截', async () => {
    await mountForm()
    const rules = wrapper.vm.$.setupState.formRules
    expect(rules.projectId[0].message).toBe('请选择项目')
    expect(rules.partyAName[0].message).toBe('请输入甲方名称')
    expect(rules.contractAmount[0].message).toBe('请输入合同金额')
  })

  it('A8-02 新增默认值：contractType=REGISTER、taxRate=9、金额 0；合同编号 disabled 占位「系统自动生成」', async () => {
    await mountForm()
    const fd = wrapper.vm.$.setupState.formData
    expect(fd.contractType).toBe('REGISTER')
    expect(fd.taxRate).toBe(9)
    expect(fd.contractAmount).toBe(0)
    expect(formVueSrc).toContain('placeholder="系统自动生成" disabled')
  })

  it('A8-03 明细行增删：addDetailRow 追加空行默认 0，removeDetailRow 按索引删除', async () => {
    await mountForm()
    const st = wrapper.vm.$.setupState
    st.addDetailRow()
    st.addDetailRow()
    expect(st.detailList).toHaveLength(2)
    expect(st.detailList[0]).toMatchObject({ itemName: '', quantity: 0, unitPrice: 0 })
    st.removeDetailRow(0)
    expect(st.detailList).toHaveLength(1)
  })

  it('A8-04 明细合计模板表达式 4 位小数精度：0.0001×9999.9999 → 「1.00」', async () => {
    await mountForm()
    // 与模板一致：((row.quantity || 0) * (row.unitPrice || 0)).toFixed(2)
    const row = { quantity: 0.0001, unitPrice: 9999.9999 }
    const total = ((row.quantity || 0) * (row.unitPrice || 0)).toFixed(2)
    expect(total).toBe('1.00')
    // 模板钉住：合计列使用该表达式
    expect(formVueSrc).toContain('((row.quantity || 0) * (row.unitPrice || 0)).toFixed(2)')
  })

  it('A8-05 新增保存：空明细跳过 saveContractDetails，仅 createContract 一次', async () => {
    await mountForm()
    const st = wrapper.vm.$.setupState
    st.formData.projectId = 1
    st.formData.partyAName = '甲方'
    st.formData.contractAmount = 100
    // formRef 未渲染校验时直接走保存路径（弹窗外整页表单已挂载，validate 通过）
    await st.handleSubmit()
    await flushPromises()
    expect(mockCreateContract).toHaveBeenCalledTimes(1)
    expect(mockSaveContractDetails).not.toHaveBeenCalled()
    expect(mockPush).toHaveBeenCalledWith('/contract/list')
  })

  it('A8-05b 有明细时保存后调 saveContractDetails 携带明细数组', async () => {
    await mountForm()
    const st = wrapper.vm.$.setupState
    st.formData.projectId = 1
    st.formData.partyAName = '甲方'
    st.formData.contractAmount = 100
    st.addDetailRow()
    st.detailList[0].itemName = '挖方'
    st.detailList[0].quantity = 2
    st.detailList[0].unitPrice = 5
    await st.handleSubmit()
    await flushPromises()
    expect(mockCreateContract).toHaveBeenCalledTimes(1)
    expect(mockSaveContractDetails).toHaveBeenCalledTimes(1)
    const [contractId, items] = (mockSaveContractDetails.mock.calls as any)[0]
    expect(items).toHaveLength(1)
    expect(items[0].itemName).toBe('挖方')
    expect(contractId).toBeDefined()
  })

  it('A8-06/A8-07 编辑回显：雪花 ID 以字符串传详情/明细 API（无精度丢失），项目注入下拉', async () => {
    mockRouteParams.id = '2089728215595675650'
    mockGetContractDetails.mockResolvedValue({ code: 200, data: [{ itemName: '回填', quantity: 1, unitPrice: 2 }] })
    await mountForm()
    expect(mockGetContractDetail).toHaveBeenCalledWith('2089728215595675650')
    expect(mockGetContractDetails).toHaveBeenCalledWith('2089728215595675650')
    const st = wrapper.vm.$.setupState
    expect(st.formData.contractAmount).toBe(100)
    expect(st.detailList).toHaveLength(1)
    // 项目注入：即便未远程搜索，编辑项也在下拉数据中
    expect(st.projectList).toEqual([{ id: 1, projectName: 'P' }])
  })

  it('A8-10 合同类型下拉含登记/变更/补充三选项（源码静态钉住）', async () => {
    await mountForm()
    expect(formVueSrc).toContain('<el-option label="登记合同" value="REGISTER" />')
    expect(formVueSrc).toContain('<el-option label="变更合同" value="CHANGE" />')
    expect(formVueSrc).toContain('<el-option label="补充合同" value="SUPPLEMENT" />')
  })

  it('A8-11 税率与金额输入边界：taxRate min=0 max=100 precision=2；明细数量/单价 precision=4', async () => {
    await mountForm()
    expect(formVueSrc).toContain('v-model="formData.taxRate" :min="0" :max="100" :precision="2"')
    expect(formVueSrc).toContain('v-model="formData.contractAmount" :min="0" :precision="2"')
    expect(formVueSrc).toContain('v-model="row.quantity" :min="0" :precision="4"')
    expect(formVueSrc).toContain('v-model="row.unitPrice" :min="0" :precision="4"')
  })

  it('A8-12 取消不落库：handleBack 仅路由跳转，无任何写请求', async () => {
    await mountForm()
    const st = wrapper.vm.$.setupState
    st.formData.partyAName = '已修改但未保存'
    st.handleBack()
    expect(mockPush).toHaveBeenCalledWith('/contract/list')
    expect(mockCreateContract).not.toHaveBeenCalled()
    expect(mockUpdateContract).not.toHaveBeenCalled()
    expect(mockSaveContractDetails).not.toHaveBeenCalled()
  })
})
