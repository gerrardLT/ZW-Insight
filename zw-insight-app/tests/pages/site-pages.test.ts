// @vitest-environment happy-dom
/**
 * site 域页面级组件测试（2026-08-16 P3 方向2 批 7 收官）
 *
 * 覆盖 progress-feedback / quality-check / safety-check（saveInspection
 * type=quality|safety 分流钉住）、construction-log（拍照项目守卫+提交载荷
 * 人数转 Number）、inspection-detail（schemeSnapshot 解析合并已有结果、
 * 未检项提交二次确认、结果载荷）。
 * 豁免：construction-log 水印合成拍照流（依赖 uni.chooseImage/getImageInfo/
 * canvas 绘制，happy-dom 无真实画布），守卫与提交分支已覆盖。
 */
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'

vi.mock('@/api/common', () => ({
  getProjectList: vi.fn(),
  saveProgressFeedback: vi.fn(),
  saveInspection: vi.fn(),
  saveConstructionLog: vi.fn(),
  getInspectionDetail: vi.fn(),
  submitInspectionResults: vi.fn(),
  getRectifications: vi.fn(),
  submitRectification: vi.fn(),
  approveRectification: vi.fn(),
  uploadRectificationPhoto: vi.fn(),
}))

import ProgressFeedback from '@/pages/site/progress-feedback.vue'
import QualityCheck from '@/pages/site/quality-check.vue'
import SafetyCheck from '@/pages/site/safety-check.vue'
import ConstructionLog from '@/pages/site/construction-log.vue'
import InspectionDetail from '@/pages/site/inspection-detail.vue'
import {
  getProjectList, saveProgressFeedback, saveInspection,
  saveConstructionLog, getInspectionDetail, submitInspectionResults,
  getRectifications, submitRectification, approveRectification, uploadRectificationPhoto,
} from '@/api/common'
import { resetUniStorage, getUni } from '../setup'

beforeEach(() => {
  resetUniStorage()
  setActivePinia(createPinia())
  vi.clearAllMocks()
  vi.mocked(getProjectList).mockResolvedValue({ code: 200, data: { records: [{ id: 1, projectName: 'P1' }] } })
  vi.mocked(getRectifications).mockResolvedValue({ code: 200, data: [] })
  ;(getUni() as any).navigateBack = vi.fn()
})

describe('site/progress-feedback.vue 进度反馈页', () => {
  it('两段校验；提交载荷进度字段转 Number', async () => {
    const toast = vi.fn()
    ;(getUni() as any).showToast = toast
    vi.mocked(saveProgressFeedback).mockResolvedValue({ code: 200 })
    const wrapper = mount(ProgressFeedback)
    await flushPromises()

    await wrapper.vm.handleSubmit()
    expect(toast).toHaveBeenCalledWith(expect.objectContaining({ title: '请选择项目' }))
    wrapper.vm.selectProject({ id: 1, projectName: 'P1' })
    await wrapper.vm.handleSubmit()
    expect(toast).toHaveBeenCalledWith(expect.objectContaining({ title: '请填写实际进度' }))

    Object.assign(wrapper.vm.form, { plannedProgress: '60', actualProgress: '55' })
    await wrapper.vm.handleSubmit()
    await flushPromises()
    expect(vi.mocked(saveProgressFeedback)).toHaveBeenCalledWith(expect.objectContaining({
      projectId: 1, plannedProgress: 60, actualProgress: 55,
    }))
    wrapper.unmount()
  })
})

describe('site/quality-check.vue 质量检查页', () => {
  it('提交 inspectionType=QUALITY 钉住；默认结果合格 hasProblem=0', async () => {
    vi.mocked(saveInspection).mockResolvedValue({ code: 200 })
    const wrapper = mount(QualityCheck)
    await flushPromises()

    expect(wrapper.vm.form.result).toBe('合格')
    wrapper.vm.selectProject({ id: 1, projectName: 'P1' })
    wrapper.vm.form.checkPart = '三层剪力墙'
    await wrapper.vm.handleSubmit()
    await flushPromises()

    expect(vi.mocked(saveInspection)).toHaveBeenCalledWith(expect.objectContaining({
      projectId: 1, inspectionType: 'QUALITY', hasProblem: 0,
    }))
    const payload = vi.mocked(saveInspection).mock.calls[0][0] as any
    expect(payload.inspectionContent).toContain('三层剪力墙')
    wrapper.unmount()
  })

  it('不合格检查 hasProblem=1 且返回 id 时弹出去整改入口', async () => {
    const navigateTo = vi.fn()
    ;(getUni() as any).navigateTo = navigateTo
    const modalCalls: any[] = []
    ;(getUni() as any).showModal = (options: any) => modalCalls.push(options)
    vi.mocked(saveInspection).mockResolvedValue({ code: 200, data: 555 })
    const wrapper = mount(QualityCheck)
    await flushPromises()
    vi.useFakeTimers()

    wrapper.vm.selectProject({ id: 1, projectName: 'P1' })
    wrapper.vm.form.checkPart = '三层剪力墙'
    wrapper.vm.form.result = '不合格'
    wrapper.vm.form.rectification = '重新绑扎'
    await wrapper.vm.handleSubmit()
    await flushPromises()

    expect(vi.mocked(saveInspection)).toHaveBeenCalledWith(expect.objectContaining({
      projectId: 1, inspectionType: 'QUALITY', hasProblem: 1, problemDescription: '重新绑扎',
    }))
    vi.advanceTimersByTime(900)
    expect(modalCalls.length).toBeGreaterThan(0)
    modalCalls[0].success({ confirm: true })
    expect(navigateTo).toHaveBeenCalledWith(expect.objectContaining({ url: '/pages/site/inspection-detail?id=555' }))
    vi.useRealTimers()
    wrapper.unmount()
  })
})

describe('site/safety-check.vue 安全检查页', () => {
  it('检查区域守卫；提交 inspectionType=SAFETY 钉住', async () => {
    const toast = vi.fn()
    ;(getUni() as any).showToast = toast
    vi.mocked(saveInspection).mockResolvedValue({ code: 200 })
    const wrapper = mount(SafetyCheck)
    await flushPromises()

    wrapper.vm.selectProject({ id: 1, projectName: 'P1' })
    await wrapper.vm.handleSubmit()
    expect(toast).toHaveBeenCalledWith(expect.objectContaining({ title: '请输入检查区域' }))

    wrapper.vm.form.checkArea = '塔吊作业区'
    await wrapper.vm.handleSubmit()
    await flushPromises()
    expect(vi.mocked(saveInspection)).toHaveBeenCalledWith(expect.objectContaining({
      projectId: 1, inspectionType: 'SAFETY', hasProblem: 0,
    }))
    const payload = vi.mocked(saveInspection).mock.calls[0][0] as any
    expect(payload.inspectionContent).toContain('塔吊作业区')
    wrapper.unmount()
  })
})

describe('site/construction-log.vue 施工日志页', () => {
  it('未选项目阻止拍照；两段校验；提交载荷人数转 Number 且带照片列表', async () => {
    const toast = vi.fn()
    ;(getUni() as any).showToast = toast
    vi.mocked(saveConstructionLog).mockResolvedValue({ code: 200 })
    const wrapper = mount(ConstructionLog)
    await flushPromises()

    // 未选项目拍照守卫（需求 6.6）
    await wrapper.vm.takePhoto()
    expect(toast).toHaveBeenCalledWith(expect.objectContaining({ title: '请先选择项目' }))

    await wrapper.vm.handleSubmit()
    expect(toast).toHaveBeenCalledWith(expect.objectContaining({ title: '请选择项目' }))
    wrapper.vm.selectProject({ id: 1, projectName: 'P1' })
    await wrapper.vm.handleSubmit()
    expect(toast).toHaveBeenCalledWith(expect.objectContaining({ title: '请填写施工内容' }))

    wrapper.vm.form.todayWork = '混凝土浇筑'
    wrapper.vm.form.attendanceCount = '12'
    wrapper.vm.photos = ['file://a.jpg']
    await wrapper.vm.handleSubmit()
    await flushPromises()
    expect(vi.mocked(saveConstructionLog)).toHaveBeenCalledWith(expect.objectContaining({
      projectId: 1, todayWork: '混凝土浇筑', attendanceCount: 12, photos: ['file://a.jpg'],
    }))
    wrapper.unmount()
  })
})

describe('site/inspection-detail.vue 检查详情页', () => {
  function stubCurrentPage(id: number) {
    ;(globalThis as any).getCurrentPages = () => [{ $page: { options: { id: String(id) } } }]
  }

  it('解析 schemeSnapshot 构建检查项并合并已有结果；统计计数', async () => {
    stubCurrentPage(77)
    vi.mocked(getInspectionDetail).mockResolvedValue({
      code: 200,
      data: {
        schemeSnapshot: JSON.stringify({
          schemeId: 9, schemeName: '主体检查方案',
          items: [
            { itemName: '钢筋间距', checkStandard: '±10mm', checkMethod: '尺量' },
            { itemName: '保护层厚度', checkStandard: '±5mm', checkMethod: '仪测' },
          ],
        }),
        details: [{ result: 'PASS' }],
      },
    })

    const wrapper = mount(InspectionDetail)
    await flushPromises()

    expect(vi.mocked(getInspectionDetail)).toHaveBeenCalledWith(77)
    expect(wrapper.vm.schemeInfo.schemeName).toBe('主体检查方案')
    expect(wrapper.vm.checkItems[0].result).toBe('PASS')
    expect(wrapper.vm.checkItems[1].result).toBe('UNCHECKED')
    expect(wrapper.vm.passCount).toBe(1)
    expect(wrapper.vm.uncheckedCount).toBe(1)
    wrapper.unmount()
  })

  it('存在未检项提交需二次确认；确认后载荷含逐项 index/itemName/result', async () => {
    stubCurrentPage(78)
    vi.mocked(getInspectionDetail).mockResolvedValue({
      code: 200,
      data: {
        schemeSnapshot: JSON.stringify({ schemeId: 9, schemeName: 'X', items: [{ itemName: '项A' }, { itemName: '项B' }] }),
        details: [],
      },
    })
    vi.mocked(submitInspectionResults).mockResolvedValue({ code: 200 })
    const modalCalls: any[] = []
    ;(getUni() as any).showModal = (options: any) => modalCalls.push(options)

    const wrapper = mount(InspectionDetail)
    await flushPromises()

    wrapper.vm.markResult(0, 'PASS')
    await wrapper.vm.handleSubmit()
    // 项B 未检 → 弹确认框，未确认前不提交
    expect(modalCalls).toHaveLength(1)
    expect(modalCalls[0].content).toContain('1 项未检查')
    expect(vi.mocked(submitInspectionResults)).not.toHaveBeenCalled()

    modalCalls[0].success({ confirm: true })
    await flushPromises()
    expect(vi.mocked(submitInspectionResults)).toHaveBeenCalledWith(78, {
      results: [
        { index: 0, itemName: '项A', result: 'PASS' },
        { index: 1, itemName: '项B', result: 'UNCHECKED' },
      ],
    })
    wrapper.unmount()
  })

  // ── P0 差距收口：整改闭环（提交/照片/复查/失败分支）──
  function mountPendingRectPage(id: number) {
    ;(globalThis as any).getCurrentPages = () => [{ $page: { options: { id: String(id) } } }]
    vi.mocked(getInspectionDetail).mockResolvedValue({
      code: 200,
      data: {
        hasProblem: 1,
        rectificationStatus: 'PENDING',
        schemeSnapshot: JSON.stringify({ schemeId: 9, schemeName: 'S', items: [{ itemName: '项A' }] }),
        details: [],
      },
    })
    return mount(InspectionDetail)
  }

  it('整改提交：空内容拦截；照片逐张上传后载荷带 attachmentIds 并刷新', async () => {
    const wrapper = mountPendingRectPage(80)
    await flushPromises()
    const toast = vi.fn()
    ;(getUni() as any).showToast = toast

    // 空内容拦截
    await wrapper.vm.handleSubmitRectification()
    expect(toast).toHaveBeenCalledWith(expect.objectContaining({ title: '请填写整改内容' }))
    expect(vi.mocked(submitRectification)).not.toHaveBeenCalled()

    // 选照片（chooseImage success）+ 预览 + 删除
    ;(getUni() as any).chooseImage = (opts: any) => opts.success({ tempFilePaths: ['file://r1.jpg', 'file://r2.jpg'] })
    ;(getUni() as any).previewImage = vi.fn()
    wrapper.vm.takeRectPhoto()
    expect(wrapper.vm.rectPhotos).toHaveLength(2)
    wrapper.vm.previewRectPhoto(0)
    expect((getUni() as any).previewImage).toHaveBeenCalledWith(expect.objectContaining({ current: 0 }))
    wrapper.vm.removeRectPhoto(1)
    expect(wrapper.vm.rectPhotos).toHaveLength(1)

    // 提交：逐张上传收集附件 ID
    vi.mocked(uploadRectificationPhoto).mockResolvedValue(321 as any)
    vi.mocked(submitRectification).mockResolvedValue({ code: 200 })
    wrapper.vm.rectForm.rectificationContent = ' 已重新绑扎 '
    await wrapper.vm.handleSubmitRectification()
    await flushPromises()

    expect(vi.mocked(uploadRectificationPhoto)).toHaveBeenCalledWith('file://r1.jpg', 80)
    expect(vi.mocked(submitRectification)).toHaveBeenCalledWith(80, {
      rectificationContent: '已重新绑扎',
      attachmentIds: '321',
    })
    expect(toast).toHaveBeenCalledWith(expect.objectContaining({ title: '整改提交成功' }))
    expect(wrapper.vm.rectForm.rectificationContent).toBe('')
    expect(wrapper.vm.rectPhotos).toHaveLength(0)
    wrapper.unmount()
  })

  it('选照取消不提示、选照失败明示；上传失败不静默提交', async () => {
    const wrapper = mountPendingRectPage(81)
    await flushPromises()
    const toast = vi.fn()
    ;(getUni() as any).showToast = toast

    // 用户取消：不 toast
    ;(getUni() as any).chooseImage = (opts: any) => opts.fail({ errMsg: 'chooseImage:fail cancel' })
    wrapper.vm.takeRectPhoto()
    expect(toast).not.toHaveBeenCalled()

    // 其他错误：明示
    ;(getUni() as any).chooseImage = (opts: any) => opts.fail({ errMsg: 'chooseImage:fail auth deny' })
    wrapper.vm.takeRectPhoto()
    expect(toast).toHaveBeenCalledWith(expect.objectContaining({ title: '选取照片失败' }))

    // 上传失败 → 不提交整改（upload 层已 toast）
    ;(getUni() as any).chooseImage = (opts: any) => opts.success({ tempFilePaths: ['file://x.jpg'] })
    wrapper.vm.takeRectPhoto()
    vi.mocked(uploadRectificationPhoto).mockRejectedValue(new Error('上传失败') as any)
    wrapper.vm.rectForm.rectificationContent = '内容'
    await wrapper.vm.handleSubmitRectification()
    await flushPromises()
    expect(vi.mocked(submitRectification)).not.toHaveBeenCalled()
    expect(wrapper.vm.rectSubmitting).toBe(false)
    wrapper.unmount()
  })

  it('复查通过：确认框取消不提交；确认后 approveRectification 并刷新', async () => {
    ;(globalThis as any).getCurrentPages = () => [{ $page: { options: { id: '82' } } }]
    vi.mocked(getInspectionDetail).mockResolvedValue({
      code: 200,
      data: { hasProblem: 1, rectificationStatus: 'SUBMITTED', schemeSnapshot: null, details: [] },
    })
    vi.mocked(getRectifications).mockResolvedValue({
      code: 200,
      data: [{ id: 901, status: 'SUBMITTED', rectificationContent: '已整改', createdAt: '2026-08-22' }],
    })
    vi.mocked(approveRectification).mockResolvedValue({ code: 200 })
    const modalCalls: any[] = []
    ;(getUni() as any).showModal = (options: any) => modalCalls.push(options)

    const wrapper = mount(InspectionDetail)
    await flushPromises()
    expect(wrapper.vm.rectifications).toHaveLength(1)
    expect(wrapper.vm.rectStatusText('SUBMITTED')).toBe('待复查')
    expect(wrapper.vm.rectStatusText(undefined)).toBe('待整改')

    // 取消确认 → 不调 approve
    wrapper.vm.handleApprove({ id: 901 })
    expect(modalCalls).toHaveLength(1)
    modalCalls[0].success({ confirm: false })
    await flushPromises()
    expect(vi.mocked(approveRectification)).not.toHaveBeenCalled()

    // 确认 → approve 并刷新详情与记录
    wrapper.vm.handleApprove({ id: 901 })
    modalCalls[1].success({ confirm: true })
    await flushPromises()
    expect(vi.mocked(approveRectification)).toHaveBeenCalledWith(901)
    wrapper.unmount()
  })

  it('详情加载失败 toast 提示；整改记录加载失败回落空列表（不静默不卡 loading）', async () => {
    ;(globalThis as any).getCurrentPages = () => [{ $page: { options: { id: '83' } } }]
    const toast = vi.fn()
    ;(getUni() as any).showToast = toast
    vi.mocked(getInspectionDetail).mockRejectedValue(new Error('服务异常'))
    vi.mocked(getRectifications).mockRejectedValue(new Error('服务异常'))

    const wrapper = mount(InspectionDetail)
    await flushPromises()

    expect(toast).toHaveBeenCalledWith(expect.objectContaining({ title: '加载检查详情失败' }))
    expect(wrapper.vm.pageLoading).toBe(false)
    expect(wrapper.vm.rectifications).toHaveLength(0)
    expect(wrapper.vm.rectLoading).toBe(false)
    wrapper.unmount()
  })

  it('无 id 参数不加载直接结束 loading', async () => {
    ;(globalThis as any).getCurrentPages = () => [{ $page: { options: {} } }]
    const wrapper = mount(InspectionDetail)
    await flushPromises()
    expect(vi.mocked(getInspectionDetail)).not.toHaveBeenCalled()
    expect(wrapper.vm.pageLoading).toBe(false)
    wrapper.unmount()
  })
})
