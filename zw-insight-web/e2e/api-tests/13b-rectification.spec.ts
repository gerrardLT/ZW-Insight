/* eslint-disable @typescript-eslint/no-explicit-any */
/**
 * 13b - 整改闭环端到端（P1 补测，租户 9999）
 *
 * @matrix C-SITE-X2 整改闭环 | tests/frontend-test-case-matrix.md 附录二
 *   创建有问题检查 → 指派责任人 → 提交整改 → 验收通过：
 *   rectificationStatus PENDING → SUBMITTED → APPROVED（RectificationService 实证：
 *   无流程模式，2026-08-13 P1 修复已移除对不存在的 rectification_approval 流程的依赖）
 *
 * 已知产品缺口（台账登记）：
 *   - 前端 inspection 页面未接线 submitRectification/approveRectification（API 已定义无 UI 调用），
 *     本 spec 钉住 API 层闭环；UI 接线后应补 UI 用例
 *   - approve 需要 rectificationId 但无任何查询端点（submit 返回 R<Void>），
 *     测试经 SSH 查 biz_rectification 取 id（合法验证路径，同 redis-probe 模式）
 *
 * 数据纪律：E2E_TEST_ 前缀 + cleaner 逆序回收 + 禁止容忍断言
 */
import { describe, it, expect, beforeAll, afterAll } from 'vitest'
import { ApiClient } from './api-client'
import { TestDataCleaner, PREFIX } from './test-data'
import { queryMysql, execMysql } from './helpers/redis-probe'

const TENANT = '9999'

describe('13b - 整改闭环端到端（租户 9999）', () => {
  let client: ApiClient
  let cleaner: TestDataCleaner
  let projectId: number
  let inspectionId: number
  let inspectionNoProblemId: number

  async function getInspection(id: number): Promise<any> {
    const resp = await client.get(`/api/v1/site/inspection/${id}`)
    expect(resp.code).toBe(200)
    return resp.data
  }

  async function createInspection(seq: number, content: string): Promise<number> {
    const resp = await client.post('/api/v1/site/inspection', {
      projectId,
      inspectionType: 'SAFETY',
      inspectionContent: content,
      details: [{
        itemName: `${PREFIX}_检查项${seq}`,
        checkStandard: 'JGJ59-2011',
        checkMethod: '现场检查',
        checkResult: 'NOT_CHECKED',
      }],
    })
    expect(resp.code, `创建检查记录：${resp.message}`).toBe(200)
    const page = await client.get('/api/v1/site/inspection/page', {
      page: 1, size: 20, projectId,
    })
    const found = (page.data?.records || []).find((r: any) => r.inspectionContent === content)
    expect(found, '检查记录应可查到').toBeDefined()
    cleaner.add(`删除检查记录${seq}`, () => client.delete(`/api/v1/site/inspection/${found.id}`))
    return found.id
  }

  beforeAll(async () => {
    client = new ApiClient()
    const login = await client.login('t9999admin', '123456')
    expect(String(login.tenantId)).toBe(TENANT)
    cleaner = new TestDataCleaner()

    const prjName = `${PREFIX}_整改闭环`
    const prjResp = await client.post('/api/v1/project', {
      projectName: prjName, projectType: 'BUILDING', projectAddress: 'P1整改闭环测试', needTender: 0,
    })
    expect(prjResp.code, '创建整改闭环测试项目').toBe(200)
    const prjPage = await client.get('/api/v1/project/page', { page: 1, size: 10, projectName: prjName })
    const prj = (prjPage.data?.records || []).find((p: any) => p.projectName === prjName)
    expect(prj).toBeDefined()
    projectId = prj.id
    cleaner.add('删除整改闭环测试项目', () => client.delete(`/api/v1/project/${projectId}`))
    // 整改记录清理（最后注册 = LIFO 最先执行，先于检查记录删除；
    // 实证：InspectionService.delete 不级联删 biz_rectification，残留会孤儿化）
    cleaner.add('清理整改记录', async () => {
      const ids = [inspectionId, inspectionNoProblemId].filter(Boolean).join(',')
      if (ids) {
        execMysql(`DELETE FROM biz_rectification WHERE tenant_id=9999 AND inspection_id IN (${ids})`)
      }
    })
  }, 120_000)

  afterAll(async () => {
    if (!cleaner) return
    await cleaner.cleanup(client)
  }, 120_000)

  // @matrix C-SITE-X2
  it('创建检查并上报问题：submitResults hasProblem=1', async () => {
    inspectionId = await createInspection(1, `${PREFIX}_整改闭环_有问题`)
    const resp = await client.post(`/api/v1/site/inspection/${inspectionId}/results`, {
      hasProblem: 1,
      problemDescription: 'P1测试：临边防护缺失',
    })
    expect(resp.code, `上报问题：${resp.message}`).toBe(200)
    const detail = await getInspection(inspectionId)
    expect(Number(detail.hasProblem), 'hasProblem 应为 1').toBe(1)
    expect(detail.problemDescription).toContain('临边防护缺失')
  })

  // @matrix C-SITE-X2
  it('指派整改责任人：rectificationStatus=PENDING', async () => {
    // 责任人用 admin（userId=1，SUPER_ADMIN；指派仅为记录字段）
    const resp = await client.post(`/api/v1/site/inspection/${inspectionId}/assign`, {
      responsiblePersonId: 1,
      rectificationDeadline: '2026-08-31',
    })
    expect(resp.code, `指派整改：${resp.message}`).toBe(200)
    const detail = await getInspection(inspectionId)
    expect(detail.rectificationStatus, '指派后应为 PENDING').toBe('PENDING')
    expect(String(detail.responsiblePersonId)).toBe('1')
    expect(detail.rectificationDeadline).toBe('2026-08-31')
  })

  // @matrix C-SITE-X2
  it('提交整改：SUBMITTED + 整改记录落库', async () => {
    const resp = await client.post(`/api/v1/site/rectification/${inspectionId}/submit`, {
      rectificationContent: 'P1测试：已补设临边防护',
      rectificationResult: '整改完成',
    })
    expect(resp.code, `提交整改：${resp.message}`).toBe(200)
    const detail = await getInspection(inspectionId)
    expect(detail.rectificationStatus, '提交后应为 SUBMITTED').toBe('SUBMITTED')
    expect(detail.rectificationDate, '应记录整改完成日期').toBeTruthy()
  })

  // @matrix C-SITE-X2
  it('整改验收通过：APPROVED（经 SSH 取 rectificationId）', async () => {
    // approve 端点需要 rectificationId 但无查询端点（产品缺口，台账登记）：
    // 经 SSH 查 biz_rectification 取 id（合法验证路径）。
    // 注意：雪花 ID 19 位超 Number.MAX_SAFE_INTEGER，必须以字符串传递防精度丢失
    const raw = queryMysql(
      `SELECT id FROM biz_rectification WHERE inspection_id=${inspectionId} AND deleted=0 ORDER BY id DESC LIMIT 1`
    )
    const rectificationId = (raw || '').trim().split(/\s+/)[0]
    expect(rectificationId, '整改记录应真实落库').toMatch(/^\d+$/)

    const resp = await client.post(`/api/v1/site/rectification/${rectificationId}/approve`)
    expect(resp.code, `整改验收：${resp.message}`).toBe(200)
    const detail = await getInspection(inspectionId)
    expect(detail.rectificationStatus, '验收后应为 APPROVED').toBe('APPROVED')
  })

  // @matrix C-SITE-X2（负向：无问题检查不可指派整改）
  it('负向：无问题检查指派整改被拒', async () => {
    inspectionNoProblemId = await createInspection(2, `${PREFIX}_整改闭环_无问题`)
    const resp = await client.post(`/api/v1/site/inspection/${inspectionNoProblemId}/assign`, {
      responsiblePersonId: 1,
      rectificationDeadline: '2026-08-31',
    })
    expect(resp.code, '无问题检查不可指派整改').not.toBe(200)
    expect(String(resp.message)).toContain('无需整改')
  })

  // @matrix C-SITE-X2（负向：状态机守卫——APPROVED 后不可重复提交）
  it('负向：APPROVED 状态重复提交整改被拒', async () => {
    const resp = await client.post(`/api/v1/site/rectification/${inspectionId}/submit`, {
      rectificationContent: 'P1测试：重复提交',
    })
    expect(resp.code, 'APPROVED 状态不允许提交').not.toBe(200)
    expect(String(resp.message)).toContain('当前状态不允许提交整改')
  })
})
