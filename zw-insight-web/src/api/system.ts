import request from '@/utils/request'

// ======================== 机构管理 ========================
// 后端：列表在根 GET /org（机构为树），更新为 PUT /org/{id}
export function getOrgTree(params?: any) {
  return request.get('/v1/system/org', { params })
}

export function getOrgDetail(id: number) {
  return request.get(`/v1/system/org/${id}`)
}

export function createOrg(data: any) {
  return request.post('/v1/system/org', data)
}

export function updateOrg(data: any) {
  return request.put(`/v1/system/org/${data.id}`, data)
}

export function deleteOrg(id: number) {
  return request.delete(`/v1/system/org/${id}`)
}

export function updateOrgStatus(id: number, status: number) {
  return request.put(`/v1/system/org/${id}/status`, { status })
}

// ======================== 用户管理 ========================
// 后端：分页/列表在根 GET /user，更新为 PUT /user/{id}
export function getUserPage(params: any) {
  return request.get('/v1/system/user', { params })
}

export function getUserDetail(id: number) {
  return request.get(`/v1/system/user/${id}`)
}

export function createUser(data: any) {
  return request.post('/v1/system/user', data)
}

export function updateUser(data: any) {
  return request.put(`/v1/system/user/${data.id}`, data)
}

export function deleteUser(id: number) {
  return request.delete(`/v1/system/user/${id}`)
}

export function updateUserStatus(id: number, status: number) {
  // 后端无 PUT /{id}/status，使用批量状态更新接口（PUT /status）传单个 id 实现
  return request.put('/v1/system/user/status', { ids: [id], status })
}

export function batchUpdateUserStatus(ids: number[], status: number) {
  return request.put('/v1/system/user/status', { ids, status })
}

export function assignUserRoles(userId: number, roleIds: number[]) {
  return request.put(`/v1/system/user/${userId}/roles`, { roleIds })
}

export function resetUserPassword(id: number) {
  return request.put(`/v1/system/user/${id}/reset-password`)
}

// ======================== 角色管理 ========================
// 后端：列表/分页在根 GET /role，更新为 PUT /role/{id}
export function getRoleList(params?: any) {
  return request.get('/v1/system/role', { params })
}

export function getRolePage(params: any) {
  return request.get('/v1/system/role', { params })
}

export function getRoleDetail(id: number) {
  return request.get(`/v1/system/role/${id}`)
}

export function createRole(data: any) {
  return request.post('/v1/system/role', data)
}

export function updateRole(data: any) {
  return request.put(`/v1/system/role/${data.id}`, data)
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
// 后端：列表/树在根 GET /menu，更新为 PUT /menu/{id}
export function getMenuTree(params?: any) {
  return request.get('/v1/system/menu', { params })
}

export function getMenuDetail(id: number) {
  return request.get(`/v1/system/menu/${id}`)
}

export function createMenu(data: any) {
  return request.post('/v1/system/menu', data)
}

export function updateMenu(data: any) {
  return request.put(`/v1/system/menu/${data.id}`, data)
}

export function deleteMenu(id: number) {
  return request.delete(`/v1/system/menu/${id}`)
}

// ======================== 数据字典 ========================
// 后端：列表/分页在根 GET /dict，更新为 PUT /dict/{id}
export function getDictList(params?: any) {
  return request.get('/v1/system/dict', { params })
}

export function getDictPage(params: any) {
  return request.get('/v1/system/dict', { params })
}

export function createDict(data: any) {
  return request.post('/v1/system/dict', data)
}

export function updateDict(data: any) {
  return request.put(`/v1/system/dict/${data.id}`, data)
}

export function deleteDict(id: number) {
  return request.delete(`/v1/system/dict/${id}`)
}

// 字典项（独立控制器 /dict-item；按字典ID查询为 GET /dict-item/{dictId}）
export function getDictItemTree(dictId: number) {
  return request.get(`/v1/system/dict-item/${dictId}`)
}

export function getDictItemList(dictId: number) {
  return request.get(`/v1/system/dict-item/${dictId}`)
}

export function createDictItem(data: any) {
  return request.post('/v1/system/dict-item', data)
}

export function updateDictItem(data: any) {
  return request.put(`/v1/system/dict-item/${data.id}`, data)
}

export function deleteDictItem(id: number) {
  return request.delete(`/v1/system/dict-item/${id}`)
}

// 按字典编码获取字典项（通用）
export function getDictItemsByCode(dictCode: string) {
  return request.get(`/v1/system/dict/items/${dictCode}`)
}

// ======================== 岗位管理 ========================
// 后端：列表/分页在根 GET /post，更新为 PUT /post/{id}
export function getPostList(params?: any) {
  return request.get('/v1/system/post', { params })
}

export function getPostPage(params: any) {
  return request.get('/v1/system/post', { params })
}

export function getPostDetail(id: number) {
  return request.get(`/v1/system/post/${id}`)
}

export function createPost(data: any) {
  return request.post('/v1/system/post', data)
}

export function updatePost(data: any) {
  return request.put(`/v1/system/post/${data.id}`, data)
}

export function deletePost(id: number) {
  return request.delete(`/v1/system/post/${id}`)
}

export function updatePostStatus(id: number, status: number) {
  return request.put(`/v1/system/post/${id}/status`, { status })
}

// ======================== 日志管理 ========================
// 后端：操作日志 GET /log/oper，登录日志 GET /log/login
export function getOperLogPage(params: any) {
  return request.get('/v1/system/log/oper', { params })
}

export function getLoginLogPage(params: any) {
  return request.get('/v1/system/log/login', { params })
}

export function getExceptionLogPage(params: any) {
  // 后端暂无独立异常日志接口，复用操作日志查询
  return request.get('/v1/system/log/oper', { params })
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
