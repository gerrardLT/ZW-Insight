import request from '@/utils/request'

// ======================== 站内消息 ========================
// MessageController: /api/v1/message/msg

export function getUnreadMessages(params: any) {
  return request.get('/v1/message/msg/unread', { params })
}

export function getAllMessages(params: any) {
  return request.get('/v1/message/msg/all', { params })
}

export function markAsRead(id: number) {
  return request.put(`/v1/message/msg/${id}/read`)
}

export function markAllAsRead() {
  return request.put('/v1/message/msg/read-all')
}

export function getUnreadCount() {
  return request.get('/v1/message/msg/unread-count')
}

// ======================== 公告管理 ========================
// AnnouncementController: /api/v1/message/announcement

export function getAnnouncementPage(params: any) {
  return request.get('/v1/message/announcement', { params })
}

export function getAnnouncementDetail(id: number) {
  return request.get(`/v1/message/announcement/${id}`)
}

export function createAnnouncement(data: any) {
  return request.post('/v1/message/announcement', data)
}

export function updateAnnouncement(id: number, data: any) {
  return request.put(`/v1/message/announcement/${id}`, data)
}

export function deleteAnnouncement(id: number) {
  return request.delete(`/v1/message/announcement/${id}`)
}

export function publishAnnouncement(id: number) {
  return request.post(`/v1/message/announcement/${id}/publish`)
}

export function revokeAnnouncement(id: number) {
  return request.post(`/v1/message/announcement/${id}/revoke`)
}

// ======================== 通知管理 ========================
// NoticeController: /api/v1/message/notice

export function getNoticePage(params: any) {
  return request.get('/v1/message/notice', { params })
}

export function getNoticeDetail(id: number) {
  return request.get(`/v1/message/notice/${id}`)
}

export function createNotice(data: any) {
  return request.post('/v1/message/notice', data)
}

export function publishNotice(id: number) {
  return request.post(`/v1/message/notice/${id}/publish`)
}

// ======================== 消息模板 ========================
// TemplateController: /api/v1/message/template

export function getTemplatePage(params: any) {
  return request.get('/v1/message/template', { params })
}

export function getTemplateDetail(id: number) {
  return request.get(`/v1/message/template/${id}`)
}

export function createTemplate(data: any) {
  return request.post('/v1/message/template', data)
}

export function updateTemplate(id: number, data: any) {
  return request.put(`/v1/message/template/${id}`, data)
}

export function deleteTemplate(id: number) {
  return request.delete(`/v1/message/template/${id}`)
}

// ======================== 快捷入口 ========================
// UserShortcutController: /api/v1/message/shortcut

export function getShortcutList() {
  return request.get('/v1/message/shortcut')
}

export function createShortcut(data: any) {
  return request.post('/v1/message/shortcut', data)
}

export function deleteShortcut(id: number) {
  return request.delete(`/v1/message/shortcut/${id}`)
}

export function updateShortcutSort(data: any[]) {
  return request.put('/v1/message/shortcut/sort', data)
}


// ======================== 推送渠道配置 ========================
// PushConfigController: /api/v1/message/push-config

export function getPushConfigPage(params: any) {
  return request.get('/v1/message/push-config', { params })
}

export function getPushConfigDetail(id: number) {
  return request.get(`/v1/message/push-config/${id}`)
}

export function createPushConfig(data: any) {
  return request.post('/v1/message/push-config', data)
}

export function updatePushConfig(id: number, data: any) {
  return request.put(`/v1/message/push-config/${id}`, data)
}

export function deletePushConfig(id: number) {
  return request.delete(`/v1/message/push-config/${id}`)
}

export function getPushConfigByType(businessType: string) {
  return request.get(`/v1/message/push-config/by-type/${businessType}`)
}
