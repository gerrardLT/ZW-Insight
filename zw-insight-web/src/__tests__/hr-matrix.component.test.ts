/**
 * 行政人事五页矩阵组件测试（账本全量补齐 M7 C-3，2026-08）
 *
 * 覆盖账本 C20 人事统计（增量空数据态）/ C21 入职申请 / C22 办公用品领用 /
 * C23 车辆管理 / C24 离职申请 的纯前端守卫增量。与既有测试边界（不重复）：
 *   - hr-statistics.component.test.ts：C-20-1~7/9/10 单请求/卡片/四图/失败提示/resize/dispose
 *   - hr-entry-crud.component.test.ts：entry 基础 CRUD + 必填首项 + detail 回显 + DRAFT 提交
 *
 * @matrix C-20-8
 * @matrix C-21-1/C-21-3/C-21-4/C-21-5/C-21-8/C-21-9
 * @matrix C-22-1/C-22-2/C-22-4/C-22-6
 * @matrix C-23-1/C-23-3/C-23-5/C-23-8
 * @matrix C-24-1/C-24-2/C-24-3/C-24-4/C-24-5/C-24-6
 */
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'

const norm = (s: string) => s.replace(/\r\n/g, '\n')
const entrySrc = norm(readFileSync(resolve(__dirname, '../views/hr/entry.vue'), 'utf-8'))
const supplySrc = norm(readFileSync(resolve(__dirname, '../views/hr/office-supply.vue'), 'utf-8'))
const vehicleSrc = norm(readFileSync(resolve(__dirname, '../views/hr/vehicle.vue'), 'utf-8'))
const resignSrc = norm(readFileSync(resolve(__dirname, '../views/hr/resign-apply.vue'), 'utf-8'))

const mocks = vi.hoisted(() => {
  const instances: any[] = []
  const chartInit = vi.fn(() => {
    const c = { setOption: vi.fn(), resize: vi.fn(), dispose: vi.fn(), isDisposed: vi.fn(() => false) }
    instances.push(c)
    return c
  })
  return {
    chartInstances: instances,
    chartInit,
    mockOverview: vi.fn(async (): Promise<any> => ({ code: 200, data: {} })),
    mockEntryPage: vi.fn(async (): Promise<any> => ({ code: 200, data: { records: [], total: 0 } })),
    mockEntryDetail: vi.fn(async (): Promise<any> => ({ code: 200, data: {} })),
    mockEntryCreate: vi.fn(async (): Promise<any> => ({ code: 200 })),
    mockEntryUpdate: vi.fn(async (): Promise<any> => ({ code: 200 })),
    mockEntryDelete: vi.fn(async (): Promise<any> => ({ code: 200 })),
    mockEntrySubmit: vi.fn(async (): Promise<any> => ({ code: 200 })),
    mockSupplyPage: vi.fn(async (): Promise<any> => ({ code: 200, data: { records: [], total: 0 } })),
    mockSupplyCreate: vi.fn(async (): Promise<any> => ({ code: 200 })),
    mockSupplyUpdate: vi.fn(async (): Promise<any> => ({ code: 200 })),
    mockSupplyDelete: vi.fn(async (): Promise<any> => ({ code: 200 })),
    mockVehiclePage: vi.fn(async (): Promise<any> => ({ code: 200, data: { records: [], total: 0 } })),
    mockVehicleCreate: vi.fn(async (): Promise<any> => ({ code: 200 })),
    mockVehicleUpdate: vi.fn(async (): Promise<any> => ({ code: 200 })),
    mockVehicleDelete: vi.fn(async (): Promise<any> => ({ code: 200 })),
    mockResignPage: vi.fn(async (): Promise<any> => ({ code: 200, data: { records: [], total: 0 } })),
    mockResignCreate: vi.fn(async (): Promise<any> => ({ code: 200 })),
    mockResignSubmit: vi.fn(async (): Promise<any> => ({ code: 200 })),
    mockSuccess: vi.fn(),
    mockError: vi.fn(),
    mockWarning: vi.fn(),
    mockConfirm: vi.fn(async () => 'confirm'),
  }
})

vi.mock('@/api/hr', () => ({
  getHrStatisticsOverview: mocks.mockOverview,
  getHrEntryPage: mocks.mockEntryPage, getHrEntryDetail: mocks.mockEntryDetail,
  createHrEntry: mocks.mockEntryCreate, updateHrEntry: mocks.mockEntryUpdate,
  deleteHrEntry: mocks.mockEntryDelete, submitHrEntry: mocks.mockEntrySubmit,
  getOfficeSupplyPage: mocks.mockSupplyPage, createOfficeSupply: mocks.mockSupplyCreate,
  updateOfficeSupply: mocks.mockSupplyUpdate, deleteOfficeSupply: mocks.mockSupplyDelete,
  getVehiclePage: mocks.mockVehiclePage, createVehicle: mocks.mockVehicleCreate,
  updateVehicle: mocks.mockVehicleUpdate, deleteVehicle: mocks.mockVehicleDelete,
  getResignApplyPage: mocks.mockResignPage, createResignApply: mocks.mockResignCreate,
  submitResignApply: mocks.mockResignSubmit,
}))
vi.mock('echarts', () => ({ init: mocks.chartInit }))
vi.mock('element-plus', async (importOriginal) => {
  const actual: any = await importOriginal()
  return {
    ...actual,
    ElMessage: { success: mocks.mockSuccess, error: mocks.mockError, warning: mocks.mockWarning, info: vi.fn() },
    ElMessageBox: { ...actual.ElMessageBox, confirm: mocks.mockConfirm },
  }
})

// 暗色联动引入 useAppStore 后：无 pinia 环境的组件测试统一 mock，防 getActivePinia
vi.mock('@/stores/app', () => ({
  useAppStore: () => ({ isDark: false }),
}))
import HrStatistics from '@/views/hr/statistics.vue'
import HrEntry from '@/views/hr/entry.vue'
import OfficeSupply from '@/views/hr/office-supply.vue'
import Vehicle from '@/views/hr/vehicle.vue'
import ResignApply from '@/views/hr/resign-apply.vue'
import ElementPlus from 'element-plus'

const globalCfg = {
  plugins: [ElementPlus],
  stubs: { OrgSelector: true, PostSelector: true },
}

// 统一登记 wrapper，afterEach 卸载并清空 body，防 element-plus 弹层累积拖慢后续挂载
const liveWrappers: any[] = []
function track(w: any) { liveWrappers.push(w); return w }
afterEach(() => {
  while (liveWrappers.length) liveWrappers.pop()!.unmount()
  document.body.innerHTML = ''
})

beforeEach(() => {
  vi.clearAllMocks()
  mocks.chartInstances.length = 0
  mocks.mockOverview.mockResolvedValue({ code: 200, data: {} })
})

async function mountOf(comp: any) {
  const w = track(mount(comp, { global: globalCfg }))
  await flushPromises()
  return w
}

describe('hr/statistics.vue 空数据态增量（C20）', () => {
  it('@matrix C-20-8 空数据：三卡片 0 值 + 四图空系列，不报错不显假数据', async () => {
    const w = await mountOf(HrStatistics)
    const st: any = w.vm.$.setupState
    expect(st.overview).toEqual({ totalActive: 0, monthlyEntry: 0, monthlyResign: 0 })
    expect(w.text()).toContain('在职总人数')
    expect(mocks.mockError).not.toHaveBeenCalled()
    // 四图均以空数组 setOption（柱图 xAxis/series data 为空，趋势双系列仍建）
    expect(mocks.chartInstances).toHaveLength(4)
    const deptOpt = mocks.chartInstances[0].setOption.mock.calls[0][0]
    expect(deptOpt.xAxis.data).toEqual([])
    expect(deptOpt.series[0].data).toEqual([])
    const trendOpt = mocks.chartInstances[3].setOption.mock.calls[0][0]
    expect(trendOpt.series).toHaveLength(2)
    expect(trendOpt.series[0].data).toEqual([])
  })
})

describe('hr/entry.vue 入职申请增量（C21）', () => {
  it('@matrix C-21-1 必填五条 realName/username/phone/orgId/postId + C-21-9 现状钉住：手机号无格式校验', async () => {
    const w = await mountOf(HrEntry)
    const st: any = w.vm.$.setupState
    expect(Object.keys(st.formRules)).toEqual(['realName', 'username', 'phone', 'orgId', 'postId'])
    expect(st.formRules.orgId[0].message).toBe('请选择部门')
    expect(st.formRules.postId[0].message).toBe('请选择岗位')
    // C-21-9 现状钉住：phone 仅 required，无 pattern/validator 格式边界校验
    expect(st.formRules.phone).toHaveLength(1)
    expect(st.formRules.phone[0]).not.toHaveProperty('pattern')
    expect(st.formRules.phone[0]).not.toHaveProperty('validator')
  })

  it('@matrix C-21-3 状态两态渲染 APPROVED→已通过 success / 否则草稿 info 源码钉住', () => {
    expect(entrySrc).toContain("row.status === 'APPROVED' ? '已通过' : '草稿'")
    expect(entrySrc).toContain(":type=\"row.status === 'APPROVED' ? 'success' : 'info'\"")
  })

  it('@matrix C-21-4 提交 confirm 文案「通过后自动创建系统账号」+ 调 submitHrEntry', async () => {
    const w = await mountOf(HrEntry)
    const st: any = w.vm.$.setupState
    await st.handleSubmitApply({ id: 5 })
    await flushPromises()
    expect(mocks.mockConfirm).toHaveBeenCalledWith(
      '提交后将发起审批，通过后自动创建系统账号，确定提交？', '提示', expect.objectContaining({ type: 'warning' }))
    expect(mocks.mockEntrySubmit).toHaveBeenCalledWith(5)
  })

  it('@matrix C-21-5/8 删除与提交按钮仅 DRAFT 可见（APPROVED 无入口）源码钉住', () => {
    expect(entrySrc).toContain('<el-button v-if="row.status === \'DRAFT\'" link type="success" @click="handleSubmitApply(row)">提交</el-button>')
    expect(entrySrc).toContain('<el-button v-if="row.status === \'DRAFT\'" link type="danger" @click="handleDelete(row)">删除</el-button>')
  })
})

describe('hr/office-supply.vue 办公用品领用（C22）', () => {
  it('@matrix C-22-1 必填两条 itemName/quantity + C-22-2 quantity min=1（默认值 1）', async () => {
    const w = await mountOf(OfficeSupply)
    const st: any = w.vm.$.setupState
    expect(Object.keys(st.formRules)).toEqual(['itemName', 'quantity'])
    expect(st.formRules.itemName[0].message).toBe('请输入物品名称')
    expect(st.formRules.quantity[0].message).toBe('请输入数量')
    expect(st.formData.quantity).toBe(1)
    expect(supplySrc).toContain('v-model="formData.quantity" :min="1"')
  })

  it('@matrix C-22-4 状态三元翻译 APPROVED 已领用/PENDING 审批中/其他草稿 + C-22-6 applyNo 列源码钉住', () => {
    expect(supplySrc).toContain("row.status === 'APPROVED' ? '已领用' : row.status === 'PENDING' ? '审批中' : '草稿'")
    expect(supplySrc).toContain("row.status === 'APPROVED' ? 'success' : row.status === 'PENDING' ? 'warning' : 'info'")
    expect(supplySrc).toContain('<el-table-column prop="applyNo" label="申请单号" width="150" />')
  })
})

describe('hr/vehicle.vue 车辆管理（C23）', () => {
  it('@matrix C-23-1 仅 plateNumber 必填（其余字段可空）', async () => {
    const w = await mountOf(Vehicle)
    const st: any = w.vm.$.setupState
    expect(Object.keys(st.formRules)).toEqual(['plateNumber'])
    expect(st.formRules.plateNumber[0].message).toBe('请输入车牌号')
  })

  it('@matrix C-23-3 vehicleStatus IN_USE→使用中 warning / 其他→闲置 success + C-23-5 八列一致性源码钉住', () => {
    expect(vehicleSrc).toContain("row.vehicleStatus === 'IN_USE' ? '使用中' : '闲置'")
    expect(vehicleSrc).toContain(":type=\"row.vehicleStatus === 'IN_USE' ? 'warning' : 'success'\"")
    for (const prop of ['plateNumber', 'vehicleType', 'brand', 'driver', 'department', 'insuranceExpiry', 'inspectionExpiry']) {
      expect(vehicleSrc).toContain(`prop="${prop}"`)
    }
    expect(vehicleSrc).toContain('label="状态"')
    expect(vehicleSrc).toContain('label="操作"')
  })

  it('@matrix C-23-8 现状钉住：无保险/年检到期临近提醒逻辑（盲点，待产品决策）', () => {
    expect(vehicleSrc).not.toContain('提醒')
    expect(vehicleSrc).not.toContain('临近')
    expect(vehicleSrc).not.toContain('expireWarn')
  })
})

describe('hr/resign-apply.vue 离职申请（C24）', () => {
  it('@matrix C-24-1 必填三条 userId/userName/resignDate + userId min=1 + C-24-2 isHandover switch 1/0 默认 0', async () => {
    const w = await mountOf(ResignApply)
    const st: any = w.vm.$.setupState
    expect(Object.keys(st.formRules)).toEqual(['userId', 'userName', 'resignDate'])
    expect(st.formRules.userId[0].message).toBe('请输入用户ID')
    expect(st.formData.isHandover).toBe(0)
    expect(resignSrc).toContain('v-model="formData.userId" :min="1"')
    expect(resignSrc).toContain('v-model="formData.isHandover" :active-value="1" :inactive-value="0"')
  })

  it('@matrix C-24-3 仅新增+提交：无编辑/删除入口 + C-24-4 提交仅 DRAFT + confirm 文案', async () => {
    expect(resignSrc).not.toContain('handleEdit')
    expect(resignSrc).not.toContain('handleDelete')
    expect(resignSrc).toContain('<el-button v-if="row.status === \'DRAFT\'" link type="success" @click="handleSubmitRow(row)">提交</el-button>')
    const w = await mountOf(ResignApply)
    const st: any = w.vm.$.setupState
    await st.handleSubmitRow({ id: 3 })
    await flushPromises()
    expect(mocks.mockConfirm).toHaveBeenCalledWith('确定要提交该离职申请吗？', '提示', expect.objectContaining({ type: 'warning' }))
    expect(mocks.mockResignSubmit).toHaveBeenCalledWith(3)
  })

  it('@matrix C-24-5 statusMap 三态 + 未知透传 + C-24-6 isHandover 1/0→是/否源码钉住', async () => {
    const w = await mountOf(ResignApply)
    const st: any = w.vm.$.setupState
    expect(st.getStatusLabel('DRAFT')).toBe('草稿')
    expect(st.getStatusType('DRAFT')).toBe('info')
    expect(st.getStatusLabel('APPROVING')).toBe('审批中')
    expect(st.getStatusType('APPROVING')).toBe('warning')
    expect(st.getStatusLabel('APPROVED')).toBe('已通过')
    expect(st.getStatusType('APPROVED')).toBe('success')
    expect(st.getStatusLabel('XYZ')).toBe('XYZ')
    expect(st.getStatusType('XYZ')).toBe('info')
    expect(resignSrc).toContain("{{ row.isHandover === 1 ? '是' : '否' }}")
  })
})
