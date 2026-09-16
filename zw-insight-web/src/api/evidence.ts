import request from '@/utils/request'

export interface EvidenceSubmitParams {
  businessType: string
  businessId: number
  projectId?: number
  fileUrl: string
  clientSha256?: string
  capturedAtDevice?: number
  longitude?: number
  latitude?: number
  watermarkPayload?: string
}

export interface EvidenceVO {
  evidenceId: number
  businessType: string
  businessId: number
  projectId?: number
  fileUrl?: string
  verifiedSha256?: string
  capturedAtDevice?: number
  receivedAtServer: string
  verificationStatus: 'VERIFIED' | 'PENDING' | 'HASH_MISMATCH'
  operatorUserId: number
  longitude?: number
  latitude?: number
}

/**
 * 提交现场证据链登记
 */
export function submitEvidence(data: EvidenceSubmitParams) {
  return request.post<EvidenceVO>('/api/v1/site/evidence', data)
}

/**
 * 查询单据证据核验
 */
export function getEvidenceVerification(businessType: string, businessId: number) {
  return request.get<EvidenceVO>('/api/v1/site/evidence/verify', {
    params: { businessType, businessId }
  })
}
