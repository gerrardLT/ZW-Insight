/**
 * WatermarkCompositor — 移动端拍照水印合成器
 *
 * 职责：
 *  - 使用 uni-app Canvas API 在照片底部合成水印
 *  - 半透明背景条（不透明度 ≥ 60%）+ 四行文字信息：
 *      1. 拍照时间          yyyy-MM-dd HH:mm:ss
 *      2. GPS 坐标          经度:xxx.xxxxxx  纬度:xxx.xxxxxx（保留 6 位小数）
 *      3. 当前登录用户姓名
 *      4. 当前选择的项目名称
 *  - 字号：clamp(width * 0.025, 12, 36)
 *  - 底部水印区域高度占照片 10%~15%
 *  - 输出质量 ≥ 90%，保持原始分辨率
 *  - 合成完成返回带水印图片的临时文件路径，原始照片不保留（由调用方丢弃原图）
 *
 * GPS 失败：gpsLat / gpsLng 为 null 时显示「定位未获取」。
 * 未选择项目：projectName 为空时由调用方拦截（阻止拍照），本合成器仅负责绘制。
 *
 * 对应需求：6.1, 6.2, 6.3, 6.4, 6.5, 6.7, 6.8
 *
 * --- Canvas 方案说明 ---
 * uni-app 端跨平台离屏 Canvas（uni.createOffscreenCanvas）在部分平台（尤其 H5/小程序）
 * 支持不一致，因此本实现采用「页面隐藏 <canvas>」+ uni.createCanvasContext 的稳定方案：
 *   - 调用方页面需放置一个隐藏的 <canvas canvas-id="watermarkCanvas">（或自定义 id）。
 *   - compose() 通过 canvasId 参数定位该 canvas（默认 'watermarkCanvas'）。
 * 该方案在 App / 小程序 / H5 上均可用，且可保持原始分辨率（destWidth/destHeight 设为原图尺寸）。
 */

// ---------------------------------------------------------------------------
// 类型定义
// ---------------------------------------------------------------------------

/** 水印信息 */
export interface WatermarkInfo {
  /** 拍照时间 yyyy-MM-dd HH:mm:ss（可预格式化，留空时由合成器按当前时间生成） */
  time: string
  /** GPS 纬度，定位失败为 null */
  gpsLat: number | null
  /** GPS 经度，定位失败为 null */
  gpsLng: number | null
  /** 当前登录用户姓名 */
  userName: string
  /** 当前选择的项目名称 */
  projectName: string
}

// ---------------------------------------------------------------------------
// 纯函数辅助（可被单元测试 / 属性测试复用 —— 见任务 4.5 PBT）
// ---------------------------------------------------------------------------

/**
 * 计算水印字号：clamp(width * 0.025, 12, 36)
 * @param width 照片宽度（px）
 * @returns 字号（px），范围 [12, 36]
 */
export function computeFontSize(width: number): number {
  const raw = width * 0.025
  return Math.min(Math.max(raw, 12), 36)
}

/**
 * 计算底部水印区域高度，占照片高度 10%~15%。
 * 基准取 12%，并钳制在 [10%, 15%] 区间，保证落在需求规定范围内。
 * @param height 照片高度（px）
 * @returns 水印区域高度（px，向上取整）
 */
export function computeBarHeight(height: number): number {
  const ratio = 0.12
  const min = height * 0.1
  const max = height * 0.15
  const clamped = Math.min(Math.max(height * ratio, min), max)
  return Math.ceil(clamped)
}

/** 两位补零 */
function pad2(n: number): string {
  return String(n).padStart(2, '0')
}

/**
 * 格式化拍照时间为 yyyy-MM-dd HH:mm:ss。
 * @param input 已格式化字符串、Date、时间戳（ms）或留空（使用当前时间）
 * @returns yyyy-MM-dd HH:mm:ss 格式字符串
 */
export function formatWatermarkTime(input?: string | number | Date): string {
  // 已经是合法的 yyyy-MM-dd HH:mm:ss 字符串则直接返回
  if (typeof input === 'string') {
    if (/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}$/.test(input)) {
      return input
    }
    // 非空但非标准格式的字符串：尝试解析，失败则回退当前时间
    const parsed = input ? new Date(input) : new Date()
    const d = isNaN(parsed.getTime()) ? new Date() : parsed
    return formatDate(d)
  }
  if (typeof input === 'number') {
    const d = new Date(input)
    return formatDate(isNaN(d.getTime()) ? new Date() : d)
  }
  if (input instanceof Date && !isNaN(input.getTime())) {
    return formatDate(input)
  }
  return formatDate(new Date())
}

function formatDate(d: Date): string {
  return (
    `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())} ` +
    `${pad2(d.getHours())}:${pad2(d.getMinutes())}:${pad2(d.getSeconds())}`
  )
}

/**
 * 生成 GPS 水印文字。
 * @param lat 纬度（可为 null）
 * @param lng 经度（可为 null）
 * @returns 定位成功："经度:xxx.xxxxxx  纬度:xxx.xxxxxx"（保留 6 位小数）；失败："定位未获取"
 */
export function formatGpsText(lat: number | null | undefined, lng: number | null | undefined): string {
  if (
    lat === null ||
    lat === undefined ||
    lng === null ||
    lng === undefined ||
    Number.isNaN(lat) ||
    Number.isNaN(lng)
  ) {
    return '定位未获取'
  }
  return `经度:${lng.toFixed(6)}  纬度:${lat.toFixed(6)}`
}

// ---------------------------------------------------------------------------
// WatermarkCompositor
// ---------------------------------------------------------------------------

export class WatermarkCompositor {
  /** 默认 canvas-id（调用方页面需放置同名隐藏 <canvas>） */
  private readonly defaultCanvasId = 'watermarkCanvas'

  /** 背景条不透明度（需求 6.3：不低于 0.6） */
  private readonly BAR_OPACITY = 0.6

  /** 输出质量（需求 6.8：不低于 0.9） */
  private readonly OUTPUT_QUALITY = 0.9

  /**
   * 合成水印到照片。
   * @param imagePath 原始照片临时路径
   * @param info      水印信息
   * @param canvasId  页面中隐藏 <canvas> 的 canvas-id（默认 'watermarkCanvas'）
   * @returns 带水印图片的临时文件路径（原图由调用方丢弃，不保留）
   */
  async compose(imagePath: string, info: WatermarkInfo, canvasId: string = this.defaultCanvasId): Promise<string> {
    const imageInfo = await this.getImageInfo(imagePath)
    const width = imageInfo.width
    const height = imageInfo.height

    const fontSize = computeFontSize(width)
    const barHeight = computeBarHeight(height)

    // 组装四行文字
    const lines = [
      formatWatermarkTime(info.time),
      formatGpsText(info.gpsLat, info.gpsLng),
      info.userName || '',
      info.projectName || ''
    ]

    const ctx = uni.createCanvasContext(canvasId)

    // 1. 绘制原图（保持原始分辨率）
    ctx.drawImage(imagePath, 0, 0, width, height)

    // 2. 绘制半透明背景条（不透明度 ≥ 0.6）
    ctx.setFillStyle(`rgba(0, 0, 0, ${this.BAR_OPACITY})`)
    ctx.fillRect(0, height - barHeight, width, barHeight)

    // 3. 绘制四行白色文字
    const padding = Math.max(Math.floor(width * 0.02), 8)
    // 行高：在背景条内均匀排布四行
    const lineGap = (barHeight - padding) / lines.length
    ctx.setFillStyle('#FFFFFF')
    ctx.setFontSize(fontSize)
    ctx.setTextBaseline('top')

    let y = height - barHeight + padding / 2
    for (const text of lines) {
      ctx.fillText(text, padding, y)
      y += lineGap
    }

    // 4. 提交绘制后导出图片（保持原始分辨率 + 质量 ≥ 0.9）
    await this.draw(ctx)
    return this.canvasToTempFilePath(canvasId, width, height)
  }

  // -------------------------------------------------------------------------
  // uni API Promise 封装
  // -------------------------------------------------------------------------

  private getImageInfo(src: string): Promise<{ width: number; height: number }> {
    return new Promise((resolve, reject) => {
      uni.getImageInfo({
        src,
        success: (res) => resolve({ width: res.width, height: res.height }),
        fail: (err) => reject(err)
      })
    })
  }

  private draw(ctx: UniApp.CanvasContext): Promise<void> {
    return new Promise((resolve) => {
      // 第二参数 reserve=false：覆盖式绘制；回调确保绘制完成后再导出
      ctx.draw(false, () => resolve())
    })
  }

  private canvasToTempFilePath(canvasId: string, width: number, height: number): Promise<string> {
    return new Promise((resolve, reject) => {
      uni.canvasToTempFilePath({
        canvasId,
        // 保持原始分辨率
        width,
        height,
        destWidth: width,
        destHeight: height,
        fileType: 'jpg',
        quality: this.OUTPUT_QUALITY,
        success: (res) => resolve(res.tempFilePath),
        fail: (err) => reject(err)
      })
    })
  }
}

/** 单例实例 */
export const watermarkCompositor = new WatermarkCompositor()

export default watermarkCompositor
