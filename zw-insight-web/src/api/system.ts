import request from '@/utils/request'

// ======================== 机构管理 ========================
export function getOrgTree(params?: any) {
  return request.get('/v1/system/org/tree', { params })
}

export function getOrgDetail(id: number) {
  return request.get(`/v1/system/org/${id}`)
}

export function createOrg(data: any) {
  return request.post('/v1/system/org', data)
}

export function updateOrg(data: any) {
  return request.put('/v1/system/org', data)
}

export function deleteOrg(id: number) {
  return request.delete(`/v1/system/org/${id}`)
}

export function updateOrgStatus(id: number, status: number) {
  return request.put(`/v1/system/org/${id}/status`, { status })
}

// ======================== 用户管理 ========================
export function getUserPage(params: any) {
  return request.get('/v1/system/user/page', { params })
}

export function getUserDetail(id: number) {
  return request.get(`/v1/system/user/${id}`)
}

export function createUser(data: any) {
  return request.post('/v1/system/user', data)
}

export function updateUser(data: any) {
  return request.put('/v1/system/user', data)
}

export function deleteUser(id: number) {
  return request.delete(`/v1/system/user/${id}`)
}

export function updateUserStatus(id: number, status: number) {
  return request.put(`/v1/system/user/${id}/status`, { status })
}

export function batchUpdateUserStatus(ids: number[], status: number) {
  return request.put('/v1/system/user/batch-status', { ids, status })
}

export function assignUserRoles(userId: number, roleIds: number[]) {
  return request.put(`/v1/system/user/${userId}/roles`, { roleIds })
}

export function resetUserPassword(id: number) {
  return request.put(`/v1/system/user/${id}/reset-password`)
}

// ======================== 角色管理 ========================
export function getRoleList(params?: any) {
  return request.get('/v1/system/role/list', { params })
}

export function getRolePage(params: any) {
  return request.get('/v1/system/role/page', { params })
}

export function getRoleDetail(id: number) {
  return request.get(`/v1/system/role/${id}`)
}

export function createRole(data: any) {
  return request.post('/v1/system/role', data)
}

export function updateRole(data: any) {
  return request.put('/v1/system/role', data)
}

export function deleteRole(id: number) {
  return request.delete(`/v1/system/role/${id}`)
}

export function getRoleMenuIds(roleId: number) {
  return request.get(`/v1/system/role/${roleId}/menus`)
}

export function assignRoleMenus(roleId: number, menuIds: number[]) {
  return request.put(`/v1/system/role/${roleId}/menus`, { menuIds })
}

export function updateRoleDataScope(roleId: number, dataScope: string) {
  return request.put(`/v1/system/role/${roleId}/data-scope`, { dataScope })
}

// ======================== 菜单管理 ========================
export function getMenuTree(params?: any) {
  return request.get('/v1/system/menu/tree', { params })
}

export function getMenuDetail(id: number) {
  return request.get(`/v1/system/menu/${id}`)
}

export function createMenu(data: any) {
  return request.post('/v1/system/menu', data)
}

export function updateMenu(data: any) {
  return request.put('/v1/system/menu', data)
}

export function deleteMenu(id: number) {
  return request.delete(`/v1/system/menu/${id}`)
}

// ======================== 数据字典 ========================
export function getDictList(params?: any) {
  return request.get('/v1/system/dict/list', { params })
}

export function getDictPage(params: any) {
  return request.get('/v1/system/dict/page', { params })
}

export function createDict(data: any) {
  return request.post('/v1/system/dict', data)
}

export function updateDict(data: any) {
  return request.put('/v1/system/dict', data)
}

export function deleteDict(id: number) {
  return request.delete(`/v1/system/dict/${id}`)
}

// 字典项
export function getDictItemTree(dictId: number) {
  return request.get(`/v1/system/dict/${dictId}/items/tree`)
}

export function getDictItemList(dictId: number) {
  return request.get(`/v1/system/dict/${dictId}/items`)
}

export function createDictItem(data: any) {
  return request.post('/v1/system/dict-item', data)
}

export function updateDictItem(data: any) {
  return request.put('/v1/system/dict-item', data)
}

export function deleteDictItem(id: number) {
  return request.delete(`/v1/system/dict-item/${id}`)
}

// 按字典编码获取字典项（通用）
export function getDictItemsByCode(dictCode: string) {
  return request.get(`/v1/system/dict/items/${dictCode}`)
}

// ======================== 岗位管理 ========================
export function getPostList(params?: any) {
  return request.get('/v1/system/post/list', { params })
}

export function getPostPage(params: any) {
  return request.get('/v1/system/post/page', { params })
}

export function getPostDetail(id: number) {
  return request.get(`/v1/system/post/${id}`)
}

export function createPost(data: any) {
  return request.post('/v1/system/post', data)
}

export function updatePost(data: any) {
  return request.put('/v1/system/post', data)
}

export function deletePost(id: number) {
  return request.delete(`/v1/system/post/${id}`)
}

export function updatePostStatus(id: number, status: number) {
  return request.put(`/v1/system/post/${id}/status`, { status })
}

// ======================== 日志管理 ========================
export function getOperLogPage(params: any) {
  return request.get('/v1/system/log/oper/page', { params })
}

export function getLoginLogPage(params: any) {
  return request.get('/v1/system/log/login/page', { params })
}

export function getExceptionLogPage(params: any) {
  return request.get('/v1/system/log/exception/page', { params })
}

// ======================== 系统设置 ========================
export interface SysConfigItem {
  id: number
  configKey: string
  configValue: string
  configName: string
  configGroup: string
  valueType: 'STRING' | 'NUMBER' | 'BOOLEAN' | 'JSON'
  defaultValue: string
  valueRange: string
  remark: string
}

export function getConfigByGroup(group: string) {
  return request.get<any, any>(`/v1/system/config/group/${group}`)
}

export function updateConfig(data: { configKey: string; configValue: string }) {
  return request.put('/v1/system/config', data)
}

export function batchUpdateConfig(data: { configKey: string; configValue: string }[]) {
  return request.put('/v1/system/config/batch', data)
}

export function resetConfigToDefault(key: string) {
  return request.post(`/v1/system/config/${key}/reset`)
}
