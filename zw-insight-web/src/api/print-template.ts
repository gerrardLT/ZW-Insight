import request from '@/utils/request'

/**
 * 打印模板实体
 * 对应后端 SysTemplate（zw-file 模块）
 */
export interface PrintTemplate {
  id?: number
  /** 模板名称 */
  templateName: string
  /** 模板类型：IMPORT / EXPORT / PRINT，打印模板固定为 PRINT */
  templateType?: string
  /** 模块编码（如 material_inbound, finance_invoice），可选 */
  moduleCode?: string
  /** 关联文件ID（PRINT 模板不使用） */
  fileId?: number | null
  /** 模板内容（PRINT 模板的 HTML 内容） */
  templateContent?: string
  /** 是否默认模板（0-否 1-是） */
  isDefault?: number
  /** 渲染引擎：SIMPLE(占位符) / THYMELEAF */
  engineType?: string
  /** 关联业务类型：CONTRACT / BUDGET / MATERIAL 等 */
  businessType?: string
  /** 数据查询配置 JSON */
  dataQueryConfig?: string
  /** 创建时间 */
  createdAt?: string
  /** 更新时间 */
  updatedAt?: string
}

/** 分页查询参数 */
export interface PrintTemplateQuery {
  pageNum?: number
  pageSize?: number
  moduleCode?: string
  businessType?: string
  templateType?: string
}

/** 渲染/导出请求体 */
export interface PrintRenderRequest {
  templateId: number
  businessDataId?: number | string
  variables?: Record<string, any>
}

/**
 * 分页查询打印模板列表
 * GET /api/v1/print-template/list
 * 响应：R<PageResult<SysTemplate>> → res.data.{records,total}
 */
export function getPrintTemplatePage(params: PrintTemplateQuery) {
  return request.get('/v1/print-template/list', { params })
}

/**
 * 查询模板详情（含模板内容）
 * GET /api/v1/print-template/{id}
 */
export function getPrintTemplateDetail(id: number) {
  return request.get(`/v1/print-template/${id}`)
}

/**
 * 创建打印模板
 * POST /api/v1/print-template
 */
export function createPrintTemplate(data: PrintTemplate) {
  return request.post('/v1/print-template', data)
}

/**
 * 更新打印模板
 * PUT /api/v1/print-template/{id}
 */
export function updatePrintTemplate(id: number, data: PrintTemplate) {
  return request.put(`/v1/print-template/${id}`, data)
}

/**
 * 逻辑删除打印模板
 * DELETE /api/v1/print-template/{id}
 */
export function deletePrintTemplate(id: number) {
  return request.delete(`/v1/print-template/${id}`)
}

/**
 * 渲染模板为 HTML
 * POST /api/v1/print-template/render
 */
export function renderPrintTemplate(data: PrintRenderRequest) {
  return request.post('/v1/print-template/render', data)
}

/**
 * 渲染模板并导出 PDF，返回可下载的二进制流
 * POST /api/v1/print-template/export-pdf
 */
export function exportPrintTemplatePdf(data: PrintRenderRequest) {
  return request.post('/v1/print-template/export-pdf', data, { responseType: 'blob' })
}
