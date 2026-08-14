/**
 * BOQ 上传工具函数（2026-08-14 P0 盲点 4 修复时提取，便于 L1 钉住）
 */

/**
 * BOQ 文件扩展名校验：仅接受 .xlsx（大小写不敏感）。
 * 2026-08-14 P0 修复盲点 4：原 endsWith('.xlsx') 大小写敏感误拒 .XLSX；
 * 后端 BoqService 无扩展名校验，大写文件本就能正常解析。
 */
export function isXlsxFile(fileName: string): boolean {
  return !!fileName && fileName.toLowerCase().endsWith('.xlsx')
}
