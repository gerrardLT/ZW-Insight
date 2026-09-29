import request from '@/utils/request'

/** 品牌配置（系统名称/副标题/Logo/登录页版权信息），免登录可读 */
export interface BrandConfig {
  systemName: string
  systemSub: string
  logoUrl: string
  logoLightUrl?: string
  faviconUrl?: string
  copyright: string
}

/**
 * 获取当前部署环境的品牌配置
 */
export function getBrandConfig() {
  return request.get<any, { code: number; data: BrandConfig }>('/v1/public/brand')
}

/**
 * 上传品牌图片（Logo / 图标）
 */
export function uploadBrandImage(file: File, logoType: 'logo' | 'logoLight' | 'favicon' = 'logo') {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('logoType', logoType)
  return request.post<any, { code: number; data: string }>('/v1/system/config/brand/upload', formData, {
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  })
}
