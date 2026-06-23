const BASE_URL = '/api'

interface RequestOptions {
  url: string
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE'
  data?: any
  header?: Record<string, string>
}

export function request<T = any>(options: RequestOptions): Promise<T> {
  const token = uni.getStorageSync('token')
  const header: Record<string, string> = {
    'Content-Type': 'application/json',
    ...options.header
  }
  if (token) {
    header['Authorization'] = `Bearer ${token}`
  }

  return new Promise((resolve, reject) => {
    uni.request({
      url: BASE_URL + options.url,
      method: options.method || 'GET',
      data: options.data,
      header,
      success: (res: any) => {
        if (res.statusCode === 200) {
          const data = res.data
          if (data.code === 200) {
            resolve(data as T)
          } else if (data.code === 401) {
            uni.removeStorageSync('token')
            uni.reLaunch({ url: '/pages/login/index' })
            reject(new Error('登录已过期'))
          } else {
            uni.showToast({ title: data.message || '请求失败', icon: 'none' })
            reject(new Error(data.message))
          }
        } else {
          uni.showToast({ title: '网络错误', icon: 'none' })
          reject(new Error('网络错误'))
        }
      },
      fail: (err) => {
        uni.showToast({ title: '网络异常', icon: 'none' })
        reject(err)
      }
    })
  })
}

export default request
