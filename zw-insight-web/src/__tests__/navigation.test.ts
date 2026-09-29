import type { RouteRecordRaw } from 'vue-router'
import { describe, expect, it } from 'vitest'
import {
  NAV_DOMAINS,
  buildNavigation,
  getAuthorizedLeafPaths,
  getDomainForPath,
  joinNavPath,
  type AuthorizedMenuDto
} from '@/utils/navigation'

const routes: RouteRecordRaw[] = [
  {
    path: '/project',
    meta: { title: '项目管理' },
    children: [
      { path: 'list', name: 'ProjectList', component: {}, meta: { title: '项目报备', icon: 'Document' } },
      { path: 'secret', name: 'Secret', component: {}, meta: { title: '隐藏页', hidden: true } }
    ]
  },
  {
    path: '/finance',
    meta: { title: '财务管理' },
    children: [{ path: 'fund', meta: { title: '资金中心' }, children: [
      { path: 'plan', name: 'FundPlan', component: {}, meta: { title: '资金计划' } }
    ] }]
  },
  {
    path: '/system',
    meta: { title: '系统管理' },
    children: [
      { path: 'org', name: 'Org', component: {}, meta: { title: '机构管理' } },
      { path: 'monitor', name: 'Monitor', component: {}, meta: { title: '系统监控' } }
    ]
  }
]

const menu = (value: Partial<AuthorizedMenuDto> & Pick<AuthorizedMenuDto, 'id'>): AuthorizedMenuDto => ({
  menuType: 'MENU', status: 1, hidden: 0, ...value
})

describe('统一导航纯函数', () => {
  it('直接拼接 parent 路径，并递归处理扁平多层菜单', () => {
    const menus = [
      menu({ id: 1, menuType: 'DIR', path: '/finance' }),
      menu({ id: 2, menuType: 'DIR', parentId: 1, path: 'fund' }),
      menu({ id: 3, parentId: 2, path: 'plan' })
    ]
    expect(joinNavPath('/finance', 'fund')).toBe('/finance/fund')
    expect([...getAuthorizedLeafPaths(menus)]).toEqual(['/finance/fund/plan'])
  })

  it('DIR 不扩权，并过滤 BUTTON、hidden、禁用与无父路径菜单', () => {
    const paths = getAuthorizedLeafPaths([
      menu({ id: 1, menuType: 'DIR', path: '/project' }),
      menu({ id: 2, menuType: 'BUTTON', parentId: 1, path: 'list' }),
      menu({ id: 3, parentId: 1, path: 'hidden', hidden: 1 }),
      menu({ id: 4, parentId: 1, path: 'disabled', status: 0 }),
      menu({ id: 5, parentId: 999, path: 'orphan' })
    ])
    expect([...paths]).toEqual([])
    expect(buildNavigation(routes, [menu({ id: 1, menuType: 'DIR', path: '/project' })]).items).toEqual([])
  })

  it('仅输出静态路由中存在、可见且明确授权的叶子', () => {
    const result = buildNavigation(routes, [
      menu({ id: 1, menuType: 'DIR', path: '/project' }),
      menu({ id: 2, parentId: 1, path: 'list' }),
      menu({ id: 3, parentId: 1, path: 'secret' }),
      menu({ id: 4, path: '/project/unknown' })
    ])
    expect(result.items.map((item) => item.path)).toEqual(['/project/list'])
    expect(result.items[0]).toMatchObject({ title: '项目报备', domain: 'business', section: '项目管理' })
    expect(result.domains).toHaveLength(1)
    expect(result.domains[0].sections[0].items).toEqual(result.items)
  })

  it('支持多层静态路由，并在 domains 中填充合理 section', () => {
    const result = buildNavigation(routes, [
      menu({ id: 1, menuType: 'DIR', path: '/finance' }),
      menu({ id: 2, menuType: 'DIR', parentId: 1, path: 'fund' }),
      menu({ id: 3, parentId: 2, path: 'plan' })
    ])
    expect(result.items[0]).toMatchObject({ path: '/finance/fund/plan', domain: 'finance', section: '资金中心' })
    expect(result.domains[0].sections[0].title).toBe('资金中心')
  })

  it('系统监控即使授权亦隐藏；所有现有模块根映射到不超过九个业务域', () => {
    const result = buildNavigation(routes, [
      menu({ id: 1, path: '/system/org' }),
      menu({ id: 2, path: '/system/monitor' })
    ])
    expect(result.items.map((item) => item.path)).toEqual(['/system/org'])
    expect(NAV_DOMAINS.length).toBeLessThanOrEqual(9)
    const roots = ['dashboard', 'project-dashboard', 'project-cost-control', 'cockpit', 'system', 'project', 'contract', 'finance', 'budget', 'purchase', 'labor', 'material', 'machine', 'subcontract', 'site', 'tender', 'hr', 'archive', 'platform', 'workflow', 'message', 'basedata']
    expect(roots.filter((root) => !getDomainForPath(`/${root}/x`))).toEqual([])
    expect(getDomainForPath('/unknown/x')).toBeUndefined()
  })
})
