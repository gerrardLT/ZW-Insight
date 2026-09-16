import axios, { AxiosInstance, AxiosResponse, InternalAxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'
import { requestSecondaryConfirm } from '@/utils/secondaryConfirm'

/** HTTP 449：后端 @SecondaryConfirm 拦截器要求二次确认 */
const HTTP_SECONDARY_CONFIRM = 449

const service: AxiosInstance = axios.create({
  baseURL: '/api',
  timeout: 30000
})

// 请求拦截器
service.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const token = localStorage.getItem('token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    // 分页参数映射：前端 pageNum/pageSize → 后端 page/size
    if (config.params) {
      if (config.params.pageNum !== undefined) {
        config.params.page = config.params.pageNum
        delete config.params.pageNum
      }
      if (config.params.pageSize !== undefined) {
        config.params.size = config.params.pageSize
        delete config.params.pageSize
      }
    }
    return config
  },
  (error) => Promise.reject(error)
)

// 响应拦截器
service.interceptors.response.use(
  (response: AxiosResponse) => {
    // 二进制流（文件下载/PDF 导出）直接返回原始数据，不做 R 包装解析
    if (response.config?.responseType === 'blob' || response.data instanceof Blob) {
      return response.data
    }
    const res = response.data
    // 后端 JacksonConfig 全局 Long→String（雪花 ID 防 JS 精度丢失），PageResult.total
    // 也被序列化为字符串（如 "311"）；element-plus ElPagination 要求 total 为 number，
    // 否则判废弃用法渲染 null 致全系统列表页分页器消失（2026-08-17 真实浏览器实测修复）。
    // 仅对数字型字符串 total 归一，records 内雪花 ID 字符串不受影响。
    if (res?.data && typeof res.data === 'object' && !Array.isArray(res.data)
      && typeof res.data.total === 'string' && /^\d+$/.test(res.data.total)) {
      res.data.total = Number(res.data.total)
    }
    if (res.code !== 200) {
      ElMessage.error(res.message || '请求失败')
      if (res.code === 401) {
        localStorage.removeItem('token')
        router.push('/login')
      }
      return Promise.reject(new Error(res.message || '请求失败'))
    }
    // 重置失败计数（成功请求后清空状态）
    localStorage.removeItem('request-failure-count')
    localStorage.removeItem('last-failure-timestamp')
    
    return res
  },
  async (error: any) => {
    const response = error.response
    const responseData = response?.data
    const config = error.config || {}
  
    // 二次确认（449）：弹出密码输入框，携带 X-Confirm-Password 重发原请求
    if (response?.status === HTTP_SECONDARY_CONFIRM && !config._secondaryConfirmRetried) {
      const tip = responseData?.message || '此操作需要二次确认，请输入登录密码'
      const password = await requestSecondaryConfirm(tip)
      if (password) {
        config._secondaryConfirmRetried = true
        config.headers = config.headers || {}
        config.headers['X-Confirm-Password'] = password
        return service.request(config)
      }
      // 用户取消：终止操作，不重新发起请求，也不弹全局错误提示
      return Promise.reject(new Error('已取消二次确认'))
    }

    // 引用校验异常：不显示全局提示，交由业务层处理
    if (response?.status === 400 && responseData?.data?.references) {
      return Promise.reject(error)
    }

    // 幂等性重试纪律：仅 GET 请求且无 response（纯网络错误）+ 未重试过
    // 注：axios config.method 实际为小写 'get'，必须大小写不敏感判断（2026-09-15 实证）
    const method = (config.method || 'get').toUpperCase()
    if (!response && method === 'GET' && !config._networkRetried) {
      console.warn('[Retry] Network error, retrying GET request')
      config._networkRetried = true
      return service.request(config)
    }

    // 保存失败记录到队列（仅 GET），上限 3 条
    if (method === 'GET' && response) {
      const queueKey = 'failed-get-requests'
      const now = Date.now()
      const lastFailure = Number(localStorage.getItem('last-failure-timestamp') || '0')
      const queue = JSON.parse(localStorage.getItem(queueKey) || '[]')
      
      // 防抖：同一条错误 3 秒内不去重
      if (now - lastFailure > 3000) {
        queue.unshift({ url: config.url, method, params: config.params, timestamp: now })
        localStorage.setItem(queueKey, JSON.stringify(queue.slice(0, 3)))
        localStorage.setItem('last-failure-timestamp', String(now))
        
        // 更新失败计数（只统计新增的）
        let count = Number(localStorage.getItem('request-failure-count') || '0')
        count++
        localStorage.setItem('request-failure-count', String(count))
        
        // ≥3 次触发顶部 Hazard 告警条
        if (count >= 3) {
          // 使用 ElMessage 替代简易版本，后续可升级为专用 topbar 组件
          ElMessage.warning('连续失败多次，请检查网络连接后重试')
        }
      }
    }

    const message = responseData?.message || error.message || '网络异常'
    ElMessage.error(message)
    if (response?.status === 401) {
      localStorage.removeItem('token')
      router.push('/login')
    }
    // 成功后的在线监听注册：网络恢复时自动重试最近失败的 GET 请求（全局一次）
    if (!(window as any).__zwNetworkRecoveryBound) {
      ;(window as any).__zwNetworkRecoveryBound = true
      window.addEventListener('online', handleNetworkRecovery)
    }
    return Promise.reject(error)
  }
)

/** 网络恢复后自动重试最近失败的 GET 请求 */
const handleNetworkRecovery = () => {
  const queueKey = 'failed-get-requests'
  const queue = JSON.parse(localStorage.getItem(queueKey) || '[]')
  if (queue.length === 0) return
  
  console.log('[NetworkRecovery] Attempting to retry failed requests:', queue.length)
  Promise.allSettled(
    queue.map((item: any) => 
      item.method === 'GET' ? service({ ...item, method: 'GET', baseURL: '/api' }) : Promise.resolve()
    )
  ).then(results => {
    const successCount = results.filter(r => r.status === 'fulfilled').length
    if (successCount > 0) {
      localStorage.removeItem(queueKey)
      localStorage.removeItem('request-failure-count')
      ElMessage.success(`同步成功 ${successCount}/${queue.length}`)
    }
  }).catch(() => {})
}

export default service
