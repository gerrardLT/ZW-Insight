import axios from 'axios'

const request = axios.create({ baseURL: '/api/v1/supplier-portal', timeout: 15000 })

request.interceptors.request.use(config => {
  const token = localStorage.getItem('supplier_token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

request.interceptors.response.use(
  res => res.data,
  err => { if (err.response?.status === 401) { localStorage.removeItem('supplier_token'); window.location.href = '/login' }; return Promise.reject(err) }
)

export function sendCode(phone: string) { return request.post('/auth/send-code', { phone }) }
export function login(phone: string, code: string) { return request.post('/auth/login', { phone, code }) }
export function getInquiryList(params?: any) { return request.get('/inquiry/list', { params }) }
export function getInquiryDetail(id: number) { return request.get(`/inquiry/${id}`) }
export function submitQuotation(data: any) { return request.post('/quotation', data) }
export function getMyQuotations(params?: any) { return request.get('/quotation/mine', { params }) }
