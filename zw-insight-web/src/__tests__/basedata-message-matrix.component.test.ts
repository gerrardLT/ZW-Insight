/**
 * 基础数据 + 消息域矩阵补盲测试（2026-08-20 账本全量补齐 M9 @matrix D-3/D-4）
 *
 * 覆盖账本缺口（既有 basedata/message 测试未覆盖部分）：
 *   - D-18-3  材料 referencePrice :min="0" :precision="2" 源码钉住
 *   - D-19-3/4 供应商 MATERIAL/MACHINE/LABOR 三类型：查询参数 + 表单默认 MATERIAL
 *   - D-22-4  检查方案无启停入口（差距钉住）；D-22-5 无检查项模板维护 UI（差距钉住）
 *   - D-23-3  评价 score :min="1" :max="100" 边界；D-23-4/6 无编辑入口、仅单一 score（差距钉住）
 *   - D-24-7  黑名单原因无 maxlength 约束（现状钉住）
 *   - D-25-5  通知无编辑/删除入口（现状钉住）；D-25-6 发布取消零副作用
 *   - D-26-5/6 公告四按钮 disabled 规则；D-26-7/8 scope 两选项 + isTop；D-26-9 REVOKED 可再发布；D-26-10 删除取消
 *   - D-27-3/5/6/7/8/9 推送配置：编辑锁定/四渠道默认/模板联动 page:1,size:200/page/size 分页/删除文案/全关可保存
 *   - D-28-2/4/6/7/8/10 消息中心：badge max=99/已读禁标/无未读禁全部已读/异地登录渲染+跳设备页/空态
 *
 * 后端约束类缺口（D-18-4/D-19-5/D-20-4/D-21-3/D-22-7/D-24-4/D-27-4 等重复冲突 409）
 * 与集成类（D-19-6/D-22-6/D-23-9/D-24-6/D-25-8 等）由 L5-API/E2E 层覆盖，vitest 不重复模拟。
 */
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import ElementPlus from 'element-plus'

const {
  mockSupplierPage, mockEvalPage,
  mockNoticePage, mockPublishNotice,
  mockAnnouncePage, mockDeleteAnnouncement,
  mockPushPage, mockTemplatePage, mockDeletePushConfig,
  mockCenterUnread, mockCenterAll, mockCenterCount,
  mockConfirm, mockPush, page,
} = vi.hoisted(() => {
  const ok = () => vi.fn(async (): Promise<any> => ({ code: 200 }))
  const page = () => vi.fn(async (): Promise<any> => ({ code: 200, data: { records: [], total: 0 } }))
  return {
    mockSupplierPage: page(),
    mockEvalPage: page(),
    mockNoticePage: page(),
    mockPublishNotice: ok(),
    mockAnnouncePage: page(),
    mockDeleteAnnouncement: ok(),
    mockPushPage: page(),
    mockTemplatePage: page(),
    mockDeletePushConfig: ok(),
    mockCenterUnread: page(),
    mockCenterAll: page(),
    mockCenterCount: vi.fn(async (): Promise<any> => ({ code: 200, data: 0 })),
    mockConfirm: vi.fn(async (_m: string, _t: string, _o?: any): Promise<string> => 'confirm'),
    mockPush: vi.fn(),
    page,
  }
})

vi.mock('@/api/basedata', () => ({
  // supplier 页实际导入的函数 + 其余页面共用 mock 时的占位
  getSupplierPage: mockSupplierPage, createSupplier: vi.fn(), updateSupplier: vi.fn(), deleteSupplier: vi.fn(),
  getSupplierEvaluationPage: mockEvalPage, createSupplierEvaluation: vi.fn(), deleteSupplierEvaluation: vi.fn(),
  getMaterialDictPage: page(), createMaterialDict: vi.fn(), updateMaterialDict: vi.fn(), deleteMaterialDict: vi.fn(),
  getOwnerPage: vi.fn(), createOwner: vi.fn(), updateOwner: vi.fn(), deleteOwner: vi.fn(),
  getCompanyPage: vi.fn(), createCompany: vi.fn(), updateCompany: vi.fn(), deleteCompany: vi.fn(),
  getInspectionSchemePage: vi.fn(async (): Promise<any> => ({ code: 200, data: { records: [], total: 0 } })),
  createInspectionScheme: vi.fn(), updateInspectionScheme: vi.fn(), deleteInspectionScheme: vi.fn(),
  getSupplierBlacklistPage: vi.fn(async (): Promise<any> => ({ code: 200, data: { records: [], total: 0 } })),
  createSupplierBlacklist: vi.fn(), deleteSupplierBlacklist: vi.fn(),
}))
vi.mock('@/api/message', () => ({
  getNoticePage: mockNoticePage, createNotice: vi.fn(), publishNotice: mockPublishNotice,
  getAnnouncementPage: mockAnnouncePage, createAnnouncement: vi.fn(), updateAnnouncement: vi.fn(),
  deleteAnnouncement: mockDeleteAnnouncement, publishAnnouncement: vi.fn(), revokeAnnouncement: vi.fn(),
  getPushConfigPage: mockPushPage, createPushConfig: vi.fn(), updatePushConfig: vi.fn(),
  deletePushConfig: mockDeletePushConfig, getTemplatePage: mockTemplatePage,
  getUnreadMessages: mockCenterUnread, getAllMessages: mockCenterAll, markAsRead: vi.fn(),
  markAllAsRead: vi.fn(), getUnreadCount: mockCenterCount,
}))
vi.mock('vue-router', () => ({
  useRouter: () => ({ push: mockPush }),
}))
vi.mock('element-plus', async (importOriginal) => {
  const actual: any = await importOriginal()
  return {
    ...actual,
    ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn(), info: vi.fn() },
    ElMessageBox: { ...actual.ElMessageBox, confirm: mockConfirm },
  }
})

import Supplier from '@/views/basedata/supplier.vue'
import SupplierEvaluation from '@/views/basedata/supplier-evaluation.vue'
import InspectionScheme from '@/views/basedata/inspection-scheme.vue'
import Notice from '@/views/message/notice/index.vue'
import Announcement from '@/views/message/announcement/index.vue'
import PushConfig from '@/views/message/push-config/index.vue'
import MessageCenter from '@/views/message/center/index.vue'

/** 源码守卫：CRLF 归一化后做包含断言 */
function norm(p: string): string {
  return readFileSync(resolve(__dirname, '..', p), 'utf-8').replace(/\r\n/g, '\n')
}

let wrapper: any = null
beforeEach(() => {
  vi.clearAllMocks()
  mockConfirm.mockResolvedValue('confirm')
})
afterEach(() => {
  if (wrapper) { try { wrapper.unmount() } catch { /* 忽略 */ } wrapper = null }
})

describe('basedata/material 单价精度（@matrix D-18-3）', () => {
  it('D-18-3 referencePrice :min="0" :precision="2" 源码钉住', () => {
    const src = norm('views/basedata/material.vue')
    expect(src).toContain('v-model="formData.referencePrice" :min="0" :precision="2"')
  })
})

describe('basedata/supplier 三类型（@matrix D-19-3/4）', () => {
  it('D-19-3 类型筛选三选项：MATERIAL/MACHINE/LABOR 随查询参数提交', async () => {
    mockSupplierPage.mockResolvedValue({ code: 200, data: { records: [{ id: 1, supplierName: '甲', supplierType: 'LABOR' }], total: 1 } })
    wrapper = mount(Supplier, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    const st = wrapper.vm.$.setupState
    mockSupplierPage.mockClear()
    st.queryParams.supplierType = 'LABOR'
    st.handleSearch()
    await flushPromises()
    expect(mockSupplierPage).toHaveBeenCalledWith(expect.objectContaining({ supplierType: 'LABOR', pageNum: 1 }))
    const src = norm('views/basedata/supplier.vue')
    ;['MATERIAL', 'MACHINE', 'LABOR'].forEach(t => expect(src).toContain(`value="${t}"`))
  })

  it('D-19-4 新增表单默认 supplierType=MATERIAL（源码默认值钉住）', async () => {
    wrapper = mount(Supplier, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    const st = wrapper.vm.$.setupState
    st.handleAdd()
    expect(st.formData.supplierType).toBe('MATERIAL')
  })
})

describe('basedata/inspection-scheme 差距钉住（@matrix D-22-4/5）', () => {
  it('D-22-4 无启用/停用切换入口（状态仅展示 tag，差距现状钉住）', () => {
    const src = norm('views/basedata/inspection-scheme.vue')
    expect(src).not.toContain('handleToggle')
    expect(src).not.toMatch(/updateInspectionSchemeStatus|enableInspectionScheme|disableInspectionScheme/)
    expect(src).not.toMatch(/@click="[^"]*(启|停)/)
  })

  it('D-22-5 无检查项模板维护 UI（差距现状钉住）', () => {
    const src = norm('views/basedata/inspection-scheme.vue')
    expect(src).not.toContain('检查项')
    expect(src).not.toContain('itemCount')
  })
})

describe('basedata/supplier-evaluation 边界与差距（@matrix D-23-3/4/6）', () => {
  async function mountEval() {
    mockEvalPage.mockResolvedValue({ code: 200, data: { records: [{ id: 1, supplierName: '甲', score: 90 }], total: 1 } })
    wrapper = mount(SupplierEvaluation, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    return wrapper
  }

  it('D-23-3 score :min="1" :max="100" + 新增默认 80', async () => {
    const w = await mountEval()
    const src = norm('views/basedata/supplier-evaluation.vue')
    expect(src).toContain('v-model="formData.score" :min="1" :max="100" :step="1"')
    const st = w.vm.$.setupState
    st.handleAdd()
    expect(st.formData.score, '默认评分 80').toBe(80)
  })

  it('D-23-4/6 页面仅新增+删除无编辑入口、仅单一 score（差距现状钉住）', () => {
    const src = norm('views/basedata/supplier-evaluation.vue')
    expect(src).not.toContain('handleEdit')
    expect(src).not.toMatch(/(qualityScore|priceScore|deliveryScore|serviceScore|五维)/)
    expect(src).not.toContain('updateSupplierEvaluation')
    // API 层 updateSupplierEvaluation 存在但页面未导入消费（闲置 API 实证）
    expect(norm('api/basedata.ts')).toContain('export function updateSupplierEvaluation')
  })
})

describe('basedata/supplier-blacklist 原因约束现状（@matrix D-24-7）', () => {
  it('D-24-7 reason textarea 无 maxlength/show-word-limit（现状钉住，超长不拦截）', () => {
    const src = norm('views/basedata/supplier-blacklist.vue')
    expect(src).toContain('v-model="formData.reason" type="textarea"')
    expect(src).not.toMatch(/formData\.reason[\s\S]{0,120}maxlength/)
  })
})

describe('message/notice 现状与取消（@matrix D-25-5/6）', () => {
  it('D-25-5 无编辑/删除入口（源码仅新增+发布，现状钉住）', () => {
    const src = norm('views/message/notice/index.vue')
    expect(src).not.toContain('handleEdit')
    expect(src).not.toContain('handleDelete')
    expect(src).not.toMatch(/updateNotice|deleteNotice/)
  })

  it('D-25-6 发布取消：confirm 拒绝后不调 publishNotice 不刷新', async () => {
    mockNoticePage.mockResolvedValue({ code: 200, data: { records: [{ id: 1, title: '通知', status: 'DRAFT' }], total: 1 } })
    wrapper = mount(Notice, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    mockConfirm.mockRejectedValueOnce(new Error('cancel'))
    mockNoticePage.mockClear()
    // 组件 handlePublish 无 try/catch：取消时 reject 向上抛（现状钉住），捕获后断言副作用为零
    await wrapper.vm.$.setupState.handlePublish({ id: 1 }).catch(() => {})
    await flushPromises()
    expect(mockPublishNotice).not.toHaveBeenCalled()
    expect(mockNoticePage, '取消后不重新拉列表').not.toHaveBeenCalled()
  })
})

describe('message/announcement 状态机守卫（@matrix D-26-5~10）', () => {
  async function mountAnnounce(records: any[]) {
    mockAnnouncePage.mockResolvedValue({ code: 200, data: { records, total: records.length } })
    wrapper = mount(Announcement, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    return wrapper
  }

  it('D-26-5/6 四按钮 disabled 规则源码钉住（编辑/发布/删除禁 PUBLISHED，撤回仅 PUBLISHED 可用）', () => {
    const src = norm('views/message/announcement/index.vue')
    expect(src).toContain('@click="handleEdit(row)" :disabled="row.status === \'PUBLISHED\'"')
    expect(src).toContain('@click="handlePublish(row)" :disabled="row.status === \'PUBLISHED\'"')
    expect(src).toContain('@click="handleRevoke(row)" :disabled="row.status !== \'PUBLISHED\'"')
    expect(src).toContain('@click="handleDelete(row)" :disabled="row.status === \'PUBLISHED\'"')
  })

  it('D-26-9 REVOKED 可再发布：disabled 规则仅排除 PUBLISHED（现状钉住）', () => {
    const src = norm('views/message/announcement/index.vue')
    // 发布按钮禁用条件仅 PUBLISHED，故 REVOKED/DRAFT 均可点发布
    expect(src).toContain('@click="handlePublish(row)" :disabled="row.status === \'PUBLISHED\'"')
    expect(src).not.toMatch(/handlePublish[\s\S]{0,80}REVOKED/)
  })

  it('D-26-7/8 表单 scope 两选项 ALL/DEPARTMENT + isTop switch 默认 false', async () => {
    const w = await mountAnnounce([])
    const st = w.vm.$.setupState
    st.handleAdd()
    expect(st.formData.scope).toBe('ALL')
    expect(st.formData.isTop).toBe(false)
    const src = norm('views/message/announcement/index.vue')
    expect(src).toContain('<el-option label="全部" value="ALL" />')
    expect(src).toContain('<el-option label="指定部门" value="DEPARTMENT" />')
    expect(src).toContain('<el-switch v-model="formData.isTop" />')
  })

  it('D-26-10 删除取消：confirm 拒绝后不调 deleteAnnouncement', async () => {
    await mountAnnounce([{ id: 2, title: '公告', status: 'DRAFT' }])
    mockConfirm.mockRejectedValueOnce(new Error('cancel'))
    // 组件 handleDelete 无 try/catch：取消时 reject 向上抛（现状钉住），捕获后断言零副作用
    await wrapper.vm.$.setupState.handleDelete({ id: 2 }).catch(() => {})
    await flushPromises()
    expect(mockDeleteAnnouncement).not.toHaveBeenCalled()
  })
})

describe('message/push-config 配置守卫（@matrix D-27-3/5/6/7/8/9）', () => {
  async function mountPush(records: any[] = []) {
    mockPushPage.mockResolvedValue({ code: 200, data: { records, total: records.length } })
    mockTemplatePage.mockResolvedValue({ code: 200, data: { records: [{ id: 1, templateName: 'T1' }], total: 1 } })
    wrapper = mount(PushConfig, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    return wrapper
  }

  it('D-27-7 分页参数用 page/size（与全局 pageNum/pageSize 不一致，现状钉住）', async () => {
    await mountPush()
    expect(mockPushPage).toHaveBeenCalledWith(expect.objectContaining({ page: 1, size: 10 }))
    expect(wrapper.vm.$.setupState.queryParams).toEqual({ page: 1, size: 10, businessType: '' })
  })

  it('D-27-6 挂载拉模板下拉：getTemplatePage({page:1,size:200}) + 模板区 v-show 联动钉住', async () => {
    await mountPush()
    expect(mockTemplatePage).toHaveBeenCalledWith({ page: 1, size: 200 })
    expect(wrapper.vm.$.setupState.templateList).toEqual([{ id: 1, templateName: 'T1' }])
    const src = norm('views/message/push-config/index.vue')
    expect(src).toContain('label="站内信模板" v-show="formData.enableInApp"')
    expect(src).toContain('label="短信模板" v-show="formData.enableSms"')
    expect(src).toContain('label="邮件模板" v-show="formData.enableEmail"')
  })

  it('D-27-3/5 编辑态 businessType 锁定 + 四渠道默认仅站内信开启', async () => {
    await mountPush()
    const st = wrapper.vm.$.setupState
    st.handleAdd()
    expect(st.formData.enableInApp).toBe(true)
    expect(st.formData.enableSms).toBe(false)
    expect(st.formData.enableEmail).toBe(false)
    expect(st.formData.enableAppPush).toBe(false)
    st.handleEdit({ id: 9, businessType: 'APPROVAL', businessTypeName: '审批', enableSms: true })
    expect(st.isEdit).toBe(true)
    expect(st.formData.businessType).toBe('APPROVAL')
    expect(norm('views/message/push-config/index.vue')).toContain('v-model="formData.businessType" placeholder="请输入业务类型编码" :disabled="isEdit"')
  })

  it('D-27-8 删除确认文案含业务类型名称', async () => {
    await mountPush([{ id: 3, businessType: 'X', businessTypeName: '审批提醒' }])
    await wrapper.vm.$.setupState.handleDelete({ id: 3, businessTypeName: '审批提醒' })
    await flushPromises()
    expect(mockConfirm).toHaveBeenCalledWith(expect.stringContaining('审批提醒'), '提示', expect.anything())
    expect(mockDeletePushConfig).toHaveBeenCalledWith(3)
  })

  it('D-27-9 四渠道全关无前端校验（现状：允许保存，仅 businessType/Name 必填）', async () => {
    await mountPush()
    const rules = wrapper.vm.$.setupState.formRules
    expect(Object.keys(rules).sort()).toEqual(['businessType', 'businessTypeName'])
    const src = norm('views/message/push-config/index.vue')
    expect(src).not.toMatch(/enable(InApp|Sms|Email|AppPush)[\s\S]{0,60}(required|validator)/)
  })
})

describe('message/center 安全提醒与守卫（@matrix D-28-2/4/6/7/8/10）', () => {
  async function mountCenter(records: any[] = [], count = 0) {
    mockCenterUnread.mockResolvedValue({ code: 200, data: { records, total: records.length } })
    mockCenterCount.mockResolvedValue({ code: 200, data: count })
    wrapper = mount(MessageCenter, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    return wrapper
  }

  it('D-28-2 badge :max="99" 源码钉住（未读>99 显示 99+）', () => {
    const src = norm('views/message/center/index.vue')
    expect(src).toContain(':value="unreadCount" :max="99"')
  })

  it('D-28-4/6 已读禁再标记 + 无未读禁全部已读（disabled 规则钉住）', () => {
    const src = norm('views/message/center/index.vue')
    expect(src).toContain('@click="handleMarkRead(row)" :disabled="row.isRead"')
    expect(src).toContain('@click="handleMarkAllRead" :disabled="unreadCount === 0"')
  })

  it('D-28-7 异地登录消息：LOGIN_LOCATION/SECURITY 判定 + alert-row 行高亮', async () => {
    const w = await mountCenter([
      { id: 1, title: '异地登录提醒', businessType: 'LOGIN_LOCATION', isRead: false },
      { id: 2, title: '普通消息', businessType: 'OTHER', isRead: false },
    ])
    const st = w.vm.$.setupState
    expect(st.isRemoteLoginMsg({ businessType: 'LOGIN_LOCATION' })).toBe(true)
    expect(st.isRemoteLoginMsg({ messageType: 'SECURITY' })).toBe(true)
    expect(st.isRemoteLoginMsg({ businessType: 'OTHER' })).toBe(false)
    expect(st.rowClassName({ row: { messageType: 'SECURITY' } })).toBe('alert-row')
    expect(st.rowClassName({ row: {} })).toBe('')
    expect(w.text()).toContain('安全提醒')
  })

  it('D-28-8 查看设备跳转 /user/devices', async () => {
    await mountCenter([{ id: 1, title: '异地登录', businessType: 'LOGIN_LOCATION', isRead: false }])
    wrapper.vm.$.setupState.goToDevices()
    expect(mockPush).toHaveBeenCalledWith('/user/devices')
  })

  it('D-28-10 空消息：表格空态渲染 No Data + total=0', async () => {
    const w = await mountCenter([])
    expect(w.find('.el-table').exists()).toBe(true)
    expect(w.text()).toContain('No Data')
    expect(wrapper.vm.$.setupState.total).toBe(0)
  })
})
