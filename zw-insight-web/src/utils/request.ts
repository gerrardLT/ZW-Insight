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
    if (res.code !== 200) {
      ElMessage.error(res.message || '请求失败')
      if (res.code === 401) {
        localStorage.removeItem('token')
        router.push('/login')
      }
      return Promise.reject(new Error(res.message || '请求失败'))
    }
    return res
  },
  async (error) => {
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

    // 引用校验异常（ReferenceExistsException）：不显示全局错误提示，交由业务层处理
    if (response?.status === 400 && responseData?.data?.references) {
      return Promise.reject(error)
    }

    const message = responseData?.message || error.message || '网络异常'
    ElMessage.error(message)
    if (response?.status === 401) {
      localStorage.removeItem('token')
      router.push('/login')
    }
    return Promise.reject(error)
  }
)

export default service
