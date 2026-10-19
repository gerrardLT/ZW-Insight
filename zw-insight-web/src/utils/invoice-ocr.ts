/**
 * 发票 OCR 识别工具（tesseract.js v5，本地 WASM，免费离线）。
 *
 * 策略：前端加载 chi_sim 语言包 → OCR 全文 → 正则提取常见发票字段。
 * 准确率受照片质量/角度/光照影响，提取结果仅供人工确认后填入表单，
 * 不直接入账（合规要求：OCR 结果必须经人工核对）。
 *
 * 语言包：首次使用自动从 CDN 下载 chi_sim（~10MB），后续浏览器缓存。
 * 内网部署可将 langPath 指向本地静态资源。
 */
import { createWorker, type Worker } from 'tesseract.js'

let workerPromise: Promise<Worker> | null = null

async function getWorker(): Promise<Worker> {
  if (!workerPromise) {
    workerPromise = createWorker('chi_sim+eng', 1, {
      logger: () => {} // 静默进度
    })
  }
  return workerPromise
}

/** OCR 提取的发票字段（全部可选，由人工确认后填入） */
export interface OcrInvoiceResult {
  /** 发票号码 */
  invoiceNumber?: string
  /** 发票代码 */
  invoiceCode?: string
  /** 开票日期（YYYY-MM-DD） */
  invoiceDate?: string
  /** 含税金额 */
  amount?: number
  /** 不含税金额 */
  amountWithoutTax?: number
  /** 税额 */
  taxAmount?: number
  /** 税率（如 13） */
  taxRate?: number
  /** 供应方名称 */
  supplierName?: string
  /** 原始 OCR 全文（调试/人工校对用） */
  rawText: string
}

/** 从 OCR 全文中提取发票字段（正则匹配，容错） */
export function parseInvoiceText(text: string): OcrInvoiceResult {
  const result: OcrInvoiceResult = { rawText: text }

  // 发票号码：20 位数电票或 8 位传统票
  const numMatch = text.match(/发票号码[：:\s]*(\d{8,20})/) || text.match(/(?:号码|NO\.?)[：:\s]*(\d{8,20})/i)
  if (numMatch) result.invoiceNumber = numMatch[1]

  // 发票代码：10-12 位
  const codeMatch = text.match(/发票代码[：:\s]*(\d{10,12})/)
  if (codeMatch) result.invoiceCode = codeMatch[1]

  // 开票日期：YYYY年MM月DD日 或 YYYY-MM-DD
  const dateMatch = text.match(/(\d{4})[年\-\/](\d{1,2})[月\-\/](\d{1,2})[日]?/)
  if (dateMatch) {
    result.invoiceDate = `${dateMatch[1]}-${dateMatch[2].padStart(2, '0')}-${dateMatch[3].padStart(2, '0')}`
  }

  // 价税合计（大写后面通常跟小写金额）
  const totalMatch = text.match(/(?:价税合计|合\s*计)[（(]?小写[）)]?[（:\s]*[¥￥]?\s*([\d,]+\.?\d*)/)
  if (totalMatch) result.amount = parseFloat(totalMatch[1].replace(/,/g, ''))

  // 税额
  const taxMatch = text.match(/税\s*额[（:\s]*[¥￥]?\s*([\d,]+\.?\d*)/)
  if (taxMatch) result.taxAmount = parseFloat(taxMatch[1].replace(/,/g, ''))

  // 不含税金额
  const noTaxMatch = text.match(/不含税[金額额][（:\s]*[¥￥]?\s*([\d,]+\.?\d*)/)
  if (noTaxMatch) result.amountWithoutTax = parseFloat(noTaxMatch[1].replace(/,/g, ''))

  // 如果没有直接提取到不含税金额但有总额和税额，派生计算
  if (result.amountWithoutTax == null && result.amount != null && result.taxAmount != null) {
    result.amountWithoutTax = Math.round((result.amount - result.taxAmount) * 100) / 100
  }

  // 税率：如 13% 或 9%
  const rateMatch = text.match(/税率[：:\s]*(\d{1,2})\s*%/)
  if (rateMatch) result.taxRate = parseInt(rateMatch[1])

  // 销售方名称（带"名称："前缀的第一行）
  const sellerMatch = text.match(/(?:销售方|销\s*售)[^名]*名\s*称[：:\s*]*([^\n\d]{4,30})/)
  if (sellerMatch) result.supplierName = sellerMatch[1].trim()

  return result
}

/**
 * 对图片文件执行 OCR 并提取发票字段。
 * @param file 图片文件（jpg/png/bmp）
 * @param onProgress 进度回调（0-100）
 */
export async function ocrInvoiceImage(
  file: File,
  onProgress?: (progress: number) => void
): Promise<OcrInvoiceResult> {
  const worker = await getWorker()
  const { data } = await worker.recognize(file, {}, { blocks: false })
  if (onProgress) onProgress(100)
  return parseInvoiceText(data.text ?? '')
}

/** 释放 OCR Worker（页面卸载时调用，可选） */
export async function terminateOcr(): Promise<void> {
  if (workerPromise) {
    const worker = await workerPromise
    await worker.terminate()
    workerPromise = null
  }
}
