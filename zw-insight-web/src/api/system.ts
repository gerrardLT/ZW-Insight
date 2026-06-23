import request from '@/utils/request'

// ======================== 机构管理 ========================
export function getOrgTree(params?: any) {
  return request.get('/v1/sys/org/tree', { params })
}

export function getOrgDetail(id: number) {
  return request.get(`/v1/sys/org/${id}`)
}

export function createOrg(data: any) {
  return request.post('/v1/sys/org', data)
}

export function updateOrg(data: any) {
  return request.put('/v1/sys/org', data)
}

export function deleteOrg(id: number) {
  return request.delete(`/v1/sys/org/${id}`)
}

export function updateOrgStatus(id: number, status: number) {
  return request.put(`/v1/sys/org/${id}/status`, { status })
}

// ======================== 用户管理 ========================
export function getUserPage(params: any) {
  return request.get('/v1/sys/user/page', { params })
}

export function getUserDetail(id: number) {
  return request.get(`/v1/sys/user/${id}`)
}

export function createUser(data: any) {
  return request.post('/v1/sys/user', data)
}

export function updateUser(data: any) {
  return request.put('/v1/sys/user', data)
}

export function deleteUser(id: number) {
  return request.delete(`/v1/sys/user/${id}`)
}

export function updateUserStatus(id: number, status: number) {
  return request.put(`/v1/sys/user/${id}/status`, { status })
}

export function batchUpdateUserStatus(ids: number[], status: number) {
  return request.put('/v1/sys/user/batch-status', { ids, status })
}

export function assignUserRoles(userId: number, roleIds: number[]) {
  return request.put(`/v1/sys/user/${userId}/roles`, { roleIds })
}

export function resetUserPassword(id: number) {
  return request.put(`/v1/sys/user/${id}/reset-password`)
}

// ======================== 角色管理 ========================
export function getRoleList(params?: any) {
  return request.get('/v1/sys/role/list', { params })
}

export function getRolePage(params: any) {
  return request.get('/v1/sys/role/page', { params })
}

export function getRoleDetail(id: number) {
  return request.get(`/v1/sys/role/${id}`)
}

export function createRole(data: any) {
  return request.post('/v1/sys/role', data)
}

export function updateRole(data: any) {
  return request.put('/v1/sys/role', data)
}

export function deleteRole(id: number) {
  return request.delete(`/v1/sys/role/${id}`)
}

export function getRoleMenuIds(roleId: number) {
  return request.get(`/v1/sys/role/${roleId}/menus`)
}

export function assignRoleMenus(roleId: number, menuIds: number[]) {
  return request.put(`/v1/sys/role/${roleId}/menus`, { menuIds })
}

// ======================== 菜单管理 ========================
export function getMenuTree(params?: any) {
  return request.get('/v1/sys/menu/tree', { params })
}

export function getMenuDetail(id: number) {
  return request.get(`/v1/sys/menu/${id}`)
}

export function createMenu(data: any) {
  return request.post('/v1/sys/menu', data)
}

export function updateMenu(data: any) {
  return request.put('/v1/sys/menu', data)
}

export function deleteMenu(id: number) {
  return request.delete(`/v1/sys/menu/${id}`)
}

// ======================== 数据字典 ========================
export function getDictList(params?: any) {
  return request.get('/v1/sys/dict/list', { params })
}

export function getDictPage(params: any) {
  return request.get('/v1/sys/dict/page', { params })
}

export function createDict(data: any) {
  return request.post('/v1/sys/dict', data)
}

export function updateDict(data: any) {
  return request.put('/v1/sys/dict', data)
}

export function deleteDict(id: number) {
  return request.delete(`/v1/sys/dict/${id}`)
}

// 字典项
export function getDictItemTree(dictId: number) {
  return request.get(`/v1/sys/dict/${dictId}/items/tree`)
}

export function getDictItemList(dictId: number) {
  return request.get(`/v1/sys/dict/${dictId}/items`)
}

export function createDictItem(data: any) {
  return request.post('/v1/sys/dict-item', data)
}

export function updateDictItem(data: any) {
  return request.put('/v1/sys/dict-item', data)
}

export function deleteDictItem(id: number) {
  return request.delete(`/v1/sys/dict-item/${id}`)
}

// 按字典编码获取字典项（通用）
export function getDictItemsByCode(dictCode: string) {
  return request.get(`/v1/sys/dict/items/${dictCode}`)
}

// ======================== 岗位管理 ========================
export function getPostList(params?: any) {
  return request.get('/v1/sys/post/list', { params })
}
