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
}))

import ProgressFeedback from '@/pages/site/progress-feedback.vue'
import QualityCheck from '@/pages/site/quality-check.vue'
import SafetyCheck from '@/pages/site/safety-check.vue'
import ConstructionLog from '@/pages/site/construction-log.vue'
import InspectionDetail from '@/pages/site/inspection-detail.vue'
import {
  getProjectList, saveProgressFeedback, saveInspection,
  saveConstructionLog, getInspectionDetail, submitInspectionResults,
} from '@/api/common'
import { resetUniStorage, getUni } from '../setup'

beforeEach(() => {
  resetUniStorage()
  setActivePinia(createPinia())
  vi.clearAllMocks()
  vi.mocked(getProjectList).mockResolvedValue({ code: 200, data: { records: [{ id: 1, projectName: 'P1' }] } })
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
  it('提交 type=quality 钉住；默认结果合格', async () => {
    vi.mocked(saveInspection).mockResolvedValue({ code: 200 })
    const wrapper = mount(QualityCheck)
    await flushPromises()

    expect(wrapper.vm.form.result).toBe('合格')
    wrapper.vm.selectProject({ id: 1, projectName: 'P1' })
    wrapper.vm.form.checkPart = '三层剪力墙'
    await wrapper.vm.handleSubmit()
    await flushPromises()

    expect(vi.mocked(saveInspection)).toHaveBeenCalledWith(expect.objectContaining({
      projectId: 1, type: 'quality', checkPart: '三层剪力墙', result: '合格',
    }))
    wrapper.unmount()
  })
})

describe('site/safety-check.vue 安全检查页', () => {
  it('检查区域守卫；提交 type=safety 钉住', async () => {
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
      projectId: 1, type: 'safety', checkArea: '塔吊作业区',
    }))
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
})
