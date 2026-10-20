/**
 * 登录后角色落地页解析（V2026_124）。
 *
 * 现状：所有角色登录后统一进入 /dashboard，而首页的公司经营卡片、项目看板、
 * 新建项目、审批工作台对多数角色不可用（无权限码 → 403）。本函数按角色/权限
 * 选择该角色真正可用的首屏；无法识别时回落到 /dashboard。
 *
 * 约束：返回的路径必须被当前用户的权限码放行，否则路由守卫仍会跳 /403。
 * 因此兜底分支只按已确认的 view 码判断，不猜测菜单是否可见。
 */

/** 角色优先级：一人多角色时取最靠前的一个 */
const ROLE_PRIORITY = [
  'SUPER_ADMIN',
  'COMPANY_LEADER',
  'AUDITOR',
  'FINANCE_STAFF',
  'MATERIAL_STAFF',
  'PROJECT_MANAGER',
  'COMMERCE_STAFF',
  'COST_BUDGET_STAFF',
  'STAFF'
] as const

/** 明确角色 → 落地页（query 的 tab 与 HubPage 的 key 对齐） */
const ROLE_HOME: Record<string, string> = {
  SUPER_ADMIN: '/dashboard',
  COMPANY_LEADER: '/cockpit/hub/operation-group?tab=overview',
  AUDITOR: '/cockpit/hub/operation-group?tab=overview',
  FINANCE_STAFF: '/finance/hub/payable-group?tab=workbench',
  MATERIAL_STAFF: '/material/hub/material-purchase-group?tab=inbound',
  PROJECT_MANAGER: '/project/hub/ledger-group?tab=list',
  COMMERCE_STAFF: '/contract/hub/contract-group?tab=list',
  COST_BUDGET_STAFF: '/budget/hub/cost-group?tab=list',
  STAFF: '/dashboard'
}

/** 落地页对应的最小 view 码；角色被收窄/自定义角色时用于过滤，避免登录即 403 */
const ROUTE_PERMISSION: Record<string, string> = {
  '/dashboard': 'dashboard:view',
  '/cockpit/hub/operation-group': 'dashboard:view',
  '/finance/hub/payable-group': 'finance:view',
  '/material/hub/material-purchase-group': 'material:view',
  '/project/hub/ledger-group': 'project:view',
  '/contract/hub/contract-group': 'contract:view',
  '/budget/hub/cost-group': 'budget:view'
}

function canEnter(home: string, permissions: readonly string[]): boolean {
  if (!home) return false
  if (permissions.includes('*:*:*')) return true
  const need = ROUTE_PERMISSION[home.split('?')[0]]
  return !need || permissions.includes(need)
}

/**
 * 解析登录落地页。
 *
 * @param roles 登录响应中的角色码（userInfo.roles）
 * @param permissions 登录响应中的权限码（userStore.permissions）
 */
export function resolveRoleHome(roles: string[] = [], permissions: string[] = []): string {
  const roleList = Array.isArray(roles) ? roles : []
  const permissionList = Array.isArray(permissions) ? permissions : []

  // 超管通配权限优先（避免被自定义角色抢先命中）
  if (permissionList.includes('*:*:*')) return ROLE_HOME.SUPER_ADMIN

  for (const role of ROLE_PRIORITY) {
    const home = ROLE_HOME[role]
    if (roleList.includes(role) && canEnter(home, permissionList)) return home
  }

  // 兜底：按权限码推断可用首屏（顺序即优先级）
  if (permissionList.includes('finance:view')) return '/finance/hub/payable-group?tab=workbench'
  if (permissionList.includes('project:view')) return '/project/hub/ledger-group?tab=list'
  if (permissionList.includes('material:view')) return '/material/hub/material-purchase-group?tab=inbound'
  if (permissionList.includes('contract:view')) return '/contract/hub/contract-group?tab=list'

  // 连首页都不可用时落到 403，避免登录后第一次跳转被守卫再次拦截
  return canEnter('/dashboard', permissionList) ? '/dashboard' : '/403'
}
