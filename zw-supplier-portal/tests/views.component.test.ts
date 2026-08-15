/**
 * 供应商门户视图组件测试（2026-08-15 P3 方向3 补测）
 *
 * 覆盖三个业务视图的真实渲染与交互逻辑（api 层经 vi.mock 隔离，
 * 被测代码为 src/views/*.vue 真实组件实现）：
 * - InquiryList：加载/空态/状态标签映射/行点击路由
 * - Login：手机号守卫/验证码倒计时/登录 token 落库+跳转/失败提示
 * - MyQuotations：状态映射（SUBMITTED/WON/LOST/未知透传）/空态
 * - InquiryDetail：材料清单加载/截止守卫/报价组装提交（totalPrice 计算/空报价过滤）
 */
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'

const {
  mockGetInquiryList, mockGetMyQuotations, mockSendCode, mockLogin, mockPush,
  mockGetInquiryDetail, mockSubmitQuotation, mockBack, mockRouteParams,
} = vi.hoisted(() => ({
  mockGetInquiryList: vi.fn(async (): Promise<any> => ({ code: 200, data: { records: [] } })),
  mockGetMyQuotations: vi.fn(async (): Promise<any> => ({ code: 200, data: { records: [] } })),
  mockSendCode: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockLogin: vi.fn(async (): Promise<any> => ({ code: 200, data: { token: 'tk-1' } })),
  mockPush: vi.fn(),
  mockGetInquiryDetail: vi.fn(async (): Promise<any> => ({ code: 200, data: {} })),
  mockSubmitQuotation: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockBack: vi.fn(),
  mockRouteParams: { id: '9' } as Record<string, string>,
}))

vi.mock('../src/api', () => ({
  getInquiryList: mockGetInquiryList,
  getMyQuotations: mockGetMyQuotations,
  sendCode: mockSendCode,
  login: mockLogin,
  getInquiryDetail: mockGetInquiryDetail,
  submitQuotation: mockSubmitQuotation,
}))
vi.mock('vue-router', () => ({
  useRouter: () => ({ push: mockPush, back: mockBack }),
  useRoute: () => ({ params: mockRouteParams }),
}))

import InquiryList from '../src/views/InquiryList.vue'
import Login from '../src/views/Login.vue'
import MyQuotations from '../src/views/MyQuotations.vue'
import InquiryDetail from '../src/views/InquiryDetail.vue'

let wrapper: any = null
let alertSpy: any = null

beforeEach(() => {
  vi.clearAllMocks()
  localStorage.clear()
  mockRouteParams.id = '9'
  alertSpy = vi.spyOn(window, 'alert').mockImplementation(() => {})
})
afterEach(() => {
  if (wrapper) { try { wrapper.unmount() } catch { /* 忽略 */ } wrapper = null }
  alertSpy?.mockRestore()
})

// InquiryList 模板用 $router.push（非 useRouter），经 global.mocks 注入；
// Login 脚本用 useRouter，经 vue-router mock 注入——两者共用 mockPush
const mountOpts = { global: { mocks: { $router: { push: mockPush } } } }

describe('InquiryList 询价公告列表', () => {
  it('渲染记录：标题/截止时间/状态标签映射（PUBLISHED=报价中）', async () => {
    mockGetInquiryList.mockResolvedValue({
      code: 200,
      data: {
        records: [
          { id: 1, title: '钢筋询价', status: 'PUBLISHED', deadline: '2026-12-31' },
          { id: 2, title: '水泥询价', status: 'AWARDED', deadline: null },
        ],
      },
    })
    wrapper = mount(InquiryList, mountOpts)
    await flushPromises()
    expect(mockGetInquiryList).toHaveBeenCalledTimes(1)
    expect(wrapper.text()).toContain('钢筋询价')
    expect(wrapper.text()).toContain('报价中')
    expect(wrapper.text()).toContain('已截止') // 非 PUBLISHED 一律显示已截止
    expect(wrapper.text()).toContain('截止时间：2026-12-31')
    expect(wrapper.text()).toContain('未设置') // deadline 为空兜底
  })

  it('空记录显示空态提示', async () => {
    mockGetInquiryList.mockResolvedValue({ code: 200, data: { records: [] } })
    wrapper = mount(InquiryList, mountOpts)
    await flushPromises()
    expect(wrapper.text()).toContain('暂无询价公告')
  })

  it('点击行跳转详情路由', async () => {
    mockGetInquiryList.mockResolvedValue({ code: 200, data: { records: [{ id: 7, title: 'T', status: 'PUBLISHED' }] } })
    wrapper = mount(InquiryList, mountOpts)
    await flushPromises()
    await wrapper.findAll('.cursor-pointer')[0].trigger('click')
    expect(mockPush).toHaveBeenCalledWith('/inquiry/7')
  })
})

describe('Login 登录页', () => {
  it('手机号不足 11 位点获取验证码 → 提示且不发请求', async () => {
    wrapper = mount(Login)
    await wrapper.find('input[type="tel"]').setValue('1380013')
    await wrapper.findAll('button')[0].trigger('click')
    await flushPromises()
    expect(alertSpy).toHaveBeenCalledWith('请输入正确的手机号')
    expect(mockSendCode).not.toHaveBeenCalled()
  })

  it('合法手机号发送验证码 → 进入 60s 倒计时（按钮禁用）', async () => {
    vi.useFakeTimers()
    try {
      mockSendCode.mockResolvedValue({ code: 200 })
      wrapper = mount(Login)
      await wrapper.find('input[type="tel"]').setValue('13800138000')
      await wrapper.findAll('button')[0].trigger('click')
      await flushPromises()
      expect(mockSendCode).toHaveBeenCalledWith('13800138000')
      const sendBtn = wrapper.findAll('button')[0]
      expect((sendBtn.element as HTMLButtonElement).disabled).toBe(true)
      expect(sendBtn.text()).toContain('60s')
      await vi.advanceTimersByTimeAsync(1000)
      expect(sendBtn.text()).toContain('59s')
    } finally {
      vi.useRealTimers()
    }
  })

  it('登录成功 → token 落库 supplier_token 并跳转 /inquiry', async () => {
    mockLogin.mockResolvedValue({ code: 200, data: { token: 'tk-abc' } })
    wrapper = mount(Login)
    await wrapper.find('input[type="tel"]').setValue('13800138000')
    await wrapper.find('input[type="text"]').setValue('123456')
    await wrapper.findAll('button')[1].trigger('click')
    await flushPromises()
    expect(mockLogin).toHaveBeenCalledWith('13800138000', '123456')
    expect(localStorage.getItem('supplier_token')).toBe('tk-abc')
    expect(mockPush).toHaveBeenCalledWith('/inquiry')
  })

  it('登录失败 → 提示且不落库不跳转', async () => {
    mockLogin.mockRejectedValue(new Error('bad code'))
    wrapper = mount(Login)
    await wrapper.find('input[type="tel"]').setValue('13800138000')
    await wrapper.find('input[type="text"]').setValue('000000')
    await wrapper.findAll('button')[1].trigger('click')
    await flushPromises()
    expect(alertSpy).toHaveBeenCalledWith('登录失败，请检查验证码')
    expect(localStorage.getItem('supplier_token')).toBeNull()
    expect(mockPush).not.toHaveBeenCalled()
  })

  it('未填手机号/验证码点登录 → 守卫提示不发请求', async () => {
    wrapper = mount(Login)
    await wrapper.findAll('button')[1].trigger('click')
    await flushPromises()
    expect(alertSpy).toHaveBeenCalledWith('请填写手机号和验证码')
    expect(mockLogin).not.toHaveBeenCalled()
  })
})

describe('MyQuotations 我的报价', () => {
  it('状态映射：SUBMITTED/WON/LOST/未知透传', async () => {
    mockGetMyQuotations.mockResolvedValue({
      code: 200,
      data: {
        records: [
          { id: 1, inquiryId: 11, status: 'SUBMITTED', totalAmount: 100, submitTime: '2026-01-01' },
          { id: 2, inquiryId: 12, status: 'WON', totalAmount: 200, submitTime: '2026-01-02' },
          { id: 3, inquiryId: 13, status: 'LOST', totalAmount: 300, submitTime: '2026-01-03' },
          { id: 4, inquiryId: 14, status: 'CUSTOM_STATE', totalAmount: 400, submitTime: '2026-01-04' },
        ],
      },
    })
    wrapper = mount(MyQuotations)
    await flushPromises()
    expect(wrapper.text()).toContain('已提交')
    expect(wrapper.text()).toContain('已中标')
    expect(wrapper.text()).toContain('未中标')
    expect(wrapper.text()).toContain('CUSTOM_STATE') // 未知状态透传原值
    expect(wrapper.text()).toContain('¥100')
  })

  it('空记录显示空态提示', async () => {
    mockGetMyQuotations.mockResolvedValue({ code: 200, data: { records: [] } })
    wrapper = mount(MyQuotations)
    await flushPromises()
    expect(wrapper.text()).toContain('暂无报价记录')
  })
})

describe('InquiryDetail 询价详情与报价提交', () => {
  const FUTURE = '2099-12-31T00:00:00'
  const DETAIL = {
    inquiry: { id: 9, title: '钢筋询价', deadline: FUTURE, publishTime: '2026-01-01' },
    materials: [
      { id: 1, materialName: '螺纹钢', specification: 'HRB400', quantity: 10, unit: '吨' },
      { id: 2, materialName: '盘圆', specification: 'HPB300', quantity: 5, unit: '吨' },
    ],
  }

  it('加载详情：标题/材料清单渲染，单价输入框初始为空', async () => {
    mockGetInquiryDetail.mockResolvedValue({ code: 200, data: DETAIL })
    wrapper = mount(InquiryDetail)
    await flushPromises()
    expect(mockGetInquiryDetail).toHaveBeenCalledWith(9)
    expect(wrapper.text()).toContain('钢筋询价')
    expect(wrapper.text()).toContain('螺纹钢')
    expect(wrapper.text()).toContain('HRB400 × 10吨')
    const inputs = wrapper.findAll('input[type="number"]')
    expect(inputs).toHaveLength(2)
    expect(inputs[0].element.getAttribute('value')).toBeFalsy()
  })

  it('加载失败 → 提示（不静默）', async () => {
    mockGetInquiryDetail.mockRejectedValue(new Error('down'))
    wrapper = mount(InquiryDetail)
    await flushPromises()
    expect(alertSpy).toHaveBeenCalledWith('加载失败')
  })

  it('截止已过 → 提交按钮禁用且文案「报价已截止」', async () => {
    mockGetInquiryDetail.mockResolvedValue({
      code: 200,
      data: { ...DETAIL, inquiry: { ...DETAIL.inquiry, deadline: '2020-01-01T00:00:00' } },
    })
    wrapper = mount(InquiryDetail)
    await flushPromises()
    expect(wrapper.text()).toContain('报价已截止')
    const submitBtn = wrapper.findAll('button').find((b: any) => b.text().includes('截止'))!
    expect((submitBtn.element as HTMLButtonElement).disabled).toBe(true)
  })

  it('未填任何单价提交 → 守卫提示不发请求', async () => {
    mockGetInquiryDetail.mockResolvedValue({ code: 200, data: DETAIL })
    wrapper = mount(InquiryDetail)
    await flushPromises()
    await wrapper.findAll('button').find((b: any) => b.text().includes('提交报价'))!.trigger('click')
    await flushPromises()
    expect(alertSpy).toHaveBeenCalledWith('请至少填写一项报价')
    expect(mockSubmitQuotation).not.toHaveBeenCalled()
  })

  it('提交报价 → 空报价过滤 + totalPrice 计算 + 跳转 /quotation', async () => {
    mockGetInquiryDetail.mockResolvedValue({ code: 200, data: DETAIL })
    wrapper = mount(InquiryDetail)
    await flushPromises()
    // 仅第一项填单价，第二项留空应被过滤
    await wrapper.findAll('input[type="number"]')[0].setValue('100')
    await wrapper.findAll('button').find((b: any) => b.text().includes('提交报价'))!.trigger('click')
    await flushPromises()
    expect(mockSubmitQuotation).toHaveBeenCalledWith({
      inquiryId: 9,
      details: [{ materialId: 1, unitPrice: 100, quantity: 10, totalPrice: 1000 }],
    })
    expect(alertSpy).toHaveBeenCalledWith('报价提交成功！')
    expect(mockPush).toHaveBeenCalledWith('/quotation')
  })

  it('提交失败 → 提示后端 message', async () => {
    mockGetInquiryDetail.mockResolvedValue({ code: 200, data: DETAIL })
    mockSubmitQuotation.mockRejectedValue({ response: { data: { message: '询价已截止' } } })
    wrapper = mount(InquiryDetail)
    await flushPromises()
    await wrapper.findAll('input[type="number"]')[0].setValue('100')
    await wrapper.findAll('button').find((b: any) => b.text().includes('提交报价'))!.trigger('click')
    await flushPromises()
    expect(alertSpy).toHaveBeenCalledWith('询价已截止')
    expect(mockPush).not.toHaveBeenCalled()
  })

  it('返回列表按钮调 router.back', async () => {
    mockGetInquiryDetail.mockResolvedValue({ code: 200, data: DETAIL })
    wrapper = mount(InquiryDetail, { global: { mocks: { $router: { back: mockBack } } } })
    await flushPromises()
    await wrapper.findAll('button')[0].trigger('click')
    expect(mockBack).toHaveBeenCalled()
  })
})
