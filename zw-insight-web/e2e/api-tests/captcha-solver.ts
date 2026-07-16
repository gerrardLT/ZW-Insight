/**
 * 验证码 OCR 解码工具
 * 使用 Tesseract.js 解码 base64 验证码图片
 */

/** 使用 Tesseract.js OCR 解码验证码图片，返回 4 位字母数字 */
export async function solveCaptcha(imageBase64: string): Promise<string> {
  const { createWorker } = await import('tesseract.js')
  const b64 = imageBase64.replace('data:image/png;base64,', '')
  const buf = Buffer.from(b64, 'base64')
  const worker = await createWorker('eng')
  const { data } = await worker.recognize(buf)
  await worker.terminate()
  // 提取字母数字，取前 4 位
  return data.text.trim().replace(/[^a-zA-Z0-9]/g, '').substring(0, 4)
}
