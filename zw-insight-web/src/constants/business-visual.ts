/**
 * ZW 业务域视觉编码常量 (PC 端)
 *
 * 注意：CSS 侧同源 tokens 见 styles/tokens/base.css 的 --zw-domain-* 组，
 * 两侧色值改动必须双向同步（JS 供 ECharts/标签渲染，CSS 供样式引用）。
 */

export interface DomainVisualMeta {
  code: string
  label: string
  color: string
  bgLight: string
  borderColor: string
}

export const BUSINESS_DOMAINS: Record<string, DomainVisualMeta> = {
  project: {
    code: 'PRJ',
    label: '工程项目',
    color: '#ff6b00',
    bgLight: 'rgba(255, 107, 0, 0.1)',
    borderColor: '#ff6b00'
  },
  contract: {
    code: 'CTR',
    label: '合同履约',
    color: '#0ea5e9',
    bgLight: 'rgba(14, 165, 233, 0.1)',
    borderColor: '#0ea5e9'
  },
  cost: {
    code: 'CST',
    label: '成本控制',
    color: '#10b981',
    bgLight: 'rgba(16, 185, 129, 0.1)',
    borderColor: '#10b981'
  },
  workflow: {
    code: 'APR',
    label: '流程审批',
    color: '#8b5cf6',
    bgLight: 'rgba(139, 92, 246, 0.1)',
    borderColor: '#8b5cf6'
  },
  site: {
    code: 'SITE',
    label: '现场管理',
    color: '#f59e0b',
    bgLight: 'rgba(245, 158, 11, 0.1)',
    borderColor: '#f59e0b'
  },
  finance: {
    code: 'FIN',
    label: '财务资金',
    color: '#ec4899',
    bgLight: 'rgba(236, 72, 153, 0.1)',
    borderColor: '#ec4899'
  },
  evidence: {
    code: 'EVD',
    label: '可信凭证',
    color: '#06b6d4',
    bgLight: 'rgba(6, 182, 212, 0.1)',
    borderColor: '#06b6d4'
  }
}
