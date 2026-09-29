import type { RouteRecordRaw } from 'vue-router'

export interface AuthorizedMenuDto {
  id: number | string
  parentId?: number | string | null
  menuName?: string
  menuType?: 'DIR' | 'MENU' | 'BUTTON' | string
  path?: string | null
  icon?: string | null
  sortOrder?: number | null
  status?: number | string | boolean | null
  hidden?: number | string | boolean | null
}

export interface NavItem {
  path: string
  title: string
  icon?: string
  domain: string
  section: string
  order: number
}

export interface NavDomain {
  key: string
  title: string
  order: number
  sections: Array<{ title: string; items: NavItem[] }>
}

export interface NavigationModel {
  items: NavItem[]
  domains: NavDomain[]
}

type DomainDefinition = Omit<NavDomain, 'sections'> & { roots: readonly string[] }

/** 全部可见业务路由收敛为八个稳定业务域；section 取静态路由分组标题。 */
export const NAV_DOMAINS: readonly DomainDefinition[] = [
  { key: 'overview', title: '经营总览', order: 10, roots: ['dashboard', 'project-dashboard', 'project-cost-control', 'cockpit'] },
  { key: 'business', title: '经营管理', order: 20, roots: ['project', 'tender', 'contract'] },
  { key: 'cost', title: '成本履约', order: 30, roots: ['budget', 'purchase', 'labor', 'material', 'machine', 'subcontract'] },
  { key: 'site', title: '现场生产', order: 40, roots: ['site'] },
  { key: 'finance', title: '财务资金', order: 50, roots: ['finance'] },
  { key: 'resources', title: '资源档案', order: 60, roots: ['basedata', 'archive'] },
  { key: 'collaboration', title: '协同办公', order: 70, roots: ['workflow', 'message', 'hr'] },
  { key: 'administration', title: '平台治理', order: 80, roots: ['system', 'platform'] }
]

const DOMAIN_BY_ROOT = new Map(NAV_DOMAINS.flatMap((domain) => domain.roots.map((root) => [root, domain] as const)))
const EXCLUDED_PATHS = new Set(['/system/monitor'])

export function joinNavPath(parent: string, child: string): string {
  const value = child.trim()
  if (value.startsWith('/')) return normalizePath(value)
  const base = parent === '/' ? '' : parent.replace(/\/$/, '')
  return normalizePath(`${base}/${value}`)
}

export function normalizePath(path: string): string {
  const normalized = `/${path.trim()}`.replace(/\/{2,}/g, '/')
  return normalized.length > 1 ? normalized.replace(/\/$/, '') : normalized
}

export function getDomainForPath(path: string): Omit<NavDomain, 'sections'> | undefined {
  const root = normalizePath(path).split('/')[1]
  const domain = DOMAIN_BY_ROOT.get(root)
  return domain && { key: domain.key, title: domain.title, order: domain.order }
}

/** 将扁平授权菜单解析为明确的 MENU 路径。DIR 只参与路径拼接，绝不授权其后代。 */
export function getAuthorizedLeafPaths(menus: readonly AuthorizedMenuDto[]): Set<string> {
  const byId = new Map(menus.map((menu) => [String(menu.id), menu]))
  const cache = new Map<string, string | undefined>()

  const resolvePath = (menu: AuthorizedMenuDto, visiting = new Set<string>()): string | undefined => {
    const id = String(menu.id)
    if (cache.has(id)) return cache.get(id)
    if (visiting.has(id) || !menu.path?.trim()) return undefined
    visiting.add(id)
    const rawPath = menu.path.trim()
    let path: string | undefined
    if (rawPath.startsWith('/')) path = normalizePath(rawPath)
    else if (menu.parentId != null && String(menu.parentId) !== '0') {
      const parent = byId.get(String(menu.parentId))
      const parentPath = parent && resolvePath(parent, visiting)
      if (parentPath) path = joinNavPath(parentPath, rawPath)
    }
    visiting.delete(id)
    cache.set(id, path)
    return path
  }

  const paths = new Set<string>()
  for (const menu of menus) {
    if (menu.menuType !== 'MENU' || isDisabled(menu.status) || isTruthyFlag(menu.hidden)) continue
    const path = resolvePath(menu)
    if (path) paths.add(path)
  }
  return paths
}

/** 静态路由为展示信息源，用户 MENU 为唯一授权源。 */
export function buildNavigation(routes: readonly RouteRecordRaw[], menus: readonly AuthorizedMenuDto[]): NavigationModel {
  const authorized = getAuthorizedLeafPaths(menus)
  const routeItems: NavItem[] = []
  let routeOrder = 0

  const visit = (route: RouteRecordRaw, parentPath: string, section: string, hiddenByParent: boolean): void => {
    const path = joinNavPath(parentPath, route.path)
    const hidden = hiddenByParent || Boolean(route.meta?.hidden)
    const title = typeof route.meta?.title === 'string' ? route.meta.title : ''
    const nextSection = title || section || '经营概览'
    const visibleChildren = (route.children ?? []).filter((child) => !child.meta?.hidden)

    if (!hidden && visibleChildren.length === 0 && title && authorized.has(path) && !EXCLUDED_PATHS.has(path)) {
      const domain = getDomainForPath(path)
      if (domain) routeItems.push({
        path,
        title,
        icon: typeof route.meta?.icon === 'string' ? route.meta.icon : undefined,
        domain: domain.key,
        section: section || '经营概览',
        order: routeOrder++
      })
    }
    for (const child of route.children ?? []) visit(child, path, nextSection, hidden)
  }

  for (const route of routes) visit(route, '', '', false)
  const items = routeItems.sort((a, b) =>
    (getDomainForPath(a.path)?.order ?? 999) - (getDomainForPath(b.path)?.order ?? 999) || a.order - b.order
  )
  const domains = NAV_DOMAINS.map(({ roots: _roots, ...domain }) => {
    const domainItems = items.filter((item) => item.domain === domain.key)
    const sectionNames = [...new Set(domainItems.map((item) => item.section))]
    return { ...domain, sections: sectionNames.map((title) => ({ title, items: domainItems.filter((item) => item.section === title) })) }
  }).filter((domain) => domain.sections.length > 0)
  return { items, domains }
}

function isDisabled(status: AuthorizedMenuDto['status']): boolean {
  return status === 0 || status === '0' || status === false
}

function isTruthyFlag(value: AuthorizedMenuDto['hidden']): boolean {
  return value === 1 || value === '1' || value === true
}
