/**
 * BOQ 上传文件类型校验测试（2026-08-14 P0 盲点 4 钉住 @matrix 盲点 4）
 *
 * 修复前：endsWith('.xlsx') 大小写敏感，.XLSX 被误拒（后端无扩展名校验，本可解析）。
 * 修复后：isXlsxFile 大小写不敏感。本测试钉住该行为防回归。
 */
import { describe, it, expect } from 'vitest'
import { isXlsxFile } from '@/views/contract/boq-utils'

describe('isXlsxFile BOQ 扩展名校验（@matrix 盲点 4）', () => {
  it('小写 .xlsx 接受', () => {
    expect(isXlsxFile('清单.xlsx')).toBe(true)
    expect(isXlsxFile('boq.xlsx')).toBe(true)
  })

  it('大写/混合大小写 .XLSX 接受（修复钉住点）', () => {
    expect(isXlsxFile('清单.XLSX')).toBe(true)
    expect(isXlsxFile('BOQ.Xlsx')).toBe(true)
    expect(isXlsxFile('a.XlSx')).toBe(true)
  })

  it('非 xlsx 拒绝', () => {
    expect(isXlsxFile('清单.xls')).toBe(false)
    expect(isXlsxFile('boq.csv')).toBe(false)
    expect(isXlsxFile('boq.xlsx.bak')).toBe(false)
    expect(isXlsxFile('xlsx')).toBe(false)
  })

  it('空/非法文件名拒绝', () => {
    expect(isXlsxFile('')).toBe(false)
    expect(isXlsxFile(null as any)).toBe(false)
    expect(isXlsxFile(undefined as any)).toBe(false)
  })
})
