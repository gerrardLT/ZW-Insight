import axios, { AxiosInstance, AxiosResponse, InternalAxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'

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
  (error) => {
    // 引用校验异常（ReferenceExistsException）：不显示全局错误提示，交由业务层处理
    const responseData = error.response?.data
    if (error.response?.status === 400 && responseData?.data?.references) {
      return Promise.reject(error)
    }

    const message = responseData?.message || error.message || '网络异常'
    ElMessage.error(message)
    if (error.response?.status === 401) {
      localStorage.removeItem('token')
      router.push('/login')
    }
    return Promise.reject(error)
  }
)

export default service
