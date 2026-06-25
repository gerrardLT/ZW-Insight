/**
 * 拍照水印工具
 * 使用 Canvas 在照片底部叠加水印信息
 */

interface WatermarkInfo {
  time: string       // 拍照时间 yyyy-MM-dd HH:mm:ss
  userName: string   // 拍照人姓名
  location: string   // GPS 位置/地址
  projectName: string // 项目名称
}

/**
 * 为照片添加水印
 * @param imagePath 原始照片路径
 * @param info 水印信息
 * @returns 合成后的图片路径
 */
export async function addWatermark(imagePath: string, info: WatermarkInfo): Promise<string> {
  return new Promise((resolve, reject) => {
    // 获取图片信息
    uni.getImageInfo({
      src: imagePath,
      success: (imageInfo) => {
        const canvasWidth = imageInfo.width
        const canvasHeight = imageInfo.height
        const watermarkHeight = Math.floor(canvasHeight * 0.12) // 水印区域占 12%

        // 创建离屏 canvas（使用 uni.createOffscreenCanvas 或 type="2d"）
        const ctx = uni.createCanvasContext('watermarkCanvas')

        // 绘制原图
        ctx.drawImage(imagePath, 0, 0, canvasWidth, canvasHeight)

        // 绘制水印背景（半透明黑色条）
        ctx.setFillStyle('rgba(0, 0, 0, 0.6)')
        ctx.fillRect(0, canvasHeight - watermarkHeight, canvasWidth, watermarkHeight)

        // 绘制水印文字
        const fontSize = Math.floor(watermarkHeight / 5)
        const padding = Math.floor(canvasWidth * 0.03)
        let y = canvasHeight - watermarkHeight + fontSize * 1.5

        ctx.setFillStyle('#FFFFFF')
        ctx.setFontSize(fontSize)

        // 第一行：时间 + 姓名
        ctx.fillText(`📅 ${info.time}  👤 ${info.userName}`, padding, y)
        y += fontSize * 1.4

        // 第二行：位置
        ctx.fillText(`📍 ${info.location}`, padding, y)
        y += fontSize * 1.4

        // 第三行：项目名称
        ctx.fillText(`🏗️ ${info.projectName}`, padding, y)

        // 导出图片
        ctx.draw(false, () => {
          uni.canvasToTempFilePath({
            canvasId: 'watermarkCanvas',
            width: canvasWidth,
            height: canvasHeight,
            destWidth: canvasWidth,
            destHeight: canvasHeight,
            fileType: 'jpg',
            quality: 0.9,
            success: (res) => resolve(res.tempFilePath),
            fail: (err) => reject(err)
          })
        })
      },
      fail: (err) => reject(err)
    })
  })
}

/**
 * 获取当前水印信息
 */
export function getWatermarkInfo(userName: string, projectName: string): Promise<WatermarkInfo> {
  return new Promise((resolve) => {
    const now = new Date()
    const time = `${now.getFullYear()}-${String(now.getMonth()+1).padStart(2,'0')}-${String(now.getDate()).padStart(2,'0')} ${String(now.getHours()).padStart(2,'0')}:${String(now.getMinutes()).padStart(2,'0')}:${String(now.getSeconds()).padStart(2,'0')}`

    uni.getLocation({
      type: 'gcj02',
      success: (loc) => {
        resolve({
          time,
          userName,
          location: `${loc.latitude.toFixed(6)}, ${loc.longitude.toFixed(6)}`,
          projectName
        })
      },
      fail: () => {
        resolve({ time, userName, location: '定位不可用', projectName })
      }
    })
  })
}
