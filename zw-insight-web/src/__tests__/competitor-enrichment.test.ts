import { describe, it, expect, vi } from 'vitest'
import { readFileSync } from 'node:fs'
const { get, post } = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn() }))
vi.mock('@/utils/request', () => ({ default: { get, post } }))
import { getMyInitiatedProcesses } from '@/api/workflow'
import { importData } from '@/api/batch'

describe('competitor enrichment contracts', () => {
  it('preserves legacy import and uses strict confirmation only for preview receipts', () => {
    const file = new File(['data'], 'test.xlsx')
    importData('MACHINE_LEDGER', file)
    expect(post.mock.lastCall?.[0]).toBe('/v1/batch/import')
    importData('MACHINE_LEDGER', file, { previewToken: 'receipt' })
    expect(post.mock.lastCall?.[0]).toBe('/v1/batch/import/confirm')
    expect((post.mock.lastCall?.[1] as FormData).get('previewToken')).toBe('receipt')
  })
  it('uses a code-independent digital identity in both migration tracks', () => {
    const sql = readFileSync('../zw-insight-server/zw-app/src/main/resources/db/migration/V2026_103__invoice_received_identity_tax.sql', 'utf8')
    expect(sql).toContain("CHAR_LENGTH(TRIM(invoice_number))=20 THEN CONCAT(''D:'',TRIM(invoice_number))")
    expect(sql).toContain('(tenant_id, active_invoice_identity)')
    expect(sql).toContain('deleted=0')
    expect(readFileSync('../deploy/db-init/106_V2026_103__invoice_received_identity_tax.sql', 'utf8')).toBe(sql)
  })
  it('uses the existing initiated-process endpoint with pagination', () => {
    getMyInitiatedProcesses({ page: 2, size: 20 })
    expect(get).toHaveBeenCalledWith('/v1/workflow/approval/my-initiated', { params: { page: 2, size: 20 } })
  })
  it('does not expose task actions for process rows', () => {
    const source = readFileSync('src/views/workflow/approval/index.vue', 'utf8')
    expect(source).toContain('name="initiated"')
    expect(source).toContain('v-if="activeTab !== \'initiated\'" label="操作"')
    expect(source).toContain('row.processName')
    expect(source).toContain('getApprovalTrace(row.processInstanceId)')
    expect(source).toContain("activeTab === 'initiated' ? 'processInstanceId' : 'taskId'")
  })
  it('persists part and previews without using the business listener', () => {
    const output = readFileSync('src/views/contract/output-report.vue', 'utf8')
    expect(output).toContain('part: row.part?.trim()')
    const service = readFileSync('../zw-insight-server/zw-file/src/main/java/com/zwinsight/file/batch/service/BatchImportExportServiceImpl.java', 'utf8')
    const preview = service.slice(service.indexOf('public Map<String, Object> previewImport'), service.indexOf('public Long asyncExport'))
    expect(preview).not.toContain('createImportListener')
    expect(service).toContain('getAndDelete(')
    expect(service).toContain('SecurityContextHolder.getTenantId()')
    expect(service).toContain('SHA-256')
  })
  it('labels contract totals as current page and derives unreceived amount', () => {
    const source = readFileSync('src/views/contract/index.vue', 'utf8')
    expect(source).toContain('本页合计（非全部）')
    expect(source).toContain('Number(row.contractAmount || 0) - Number(row.cumulativeReceivedAmount || 0)')
    expect(source).toContain('cumulativeInvoiceAmount')
    expect(source).toContain(':summary-method="pageSummary"')
  })
})
