/**
 * 角色落地页解析单元测试（V2026_124）。
 *
 * 目标：登录后按角色/权限落到该角色真实可用的首屏，避免统一进 /dashboard
 * 后出现大量 403 按钮；未知角色/空权限必须安全回落到 /dashboard。
 */
import { describe, it, expect } from 'vitest'
import { resolveRoleHome } from '@/utils/role-home'

describe('utils/role-home.resolveRoleHome', () => {
  it('超管通配权限 → 首页', () => {
    expect(resolveRoleHome(['SUPER_ADMIN'], ['*:*:*'])).toBe('/dashboard')
  })

  it('经营决策/审计 → 经营驾驶舱', () => {
    expect(resolveRoleHome(['COMPANY_LEADER'], ['dashboard:view'])).toBe('/cockpit/hub/operation-group?tab=overview')
    expect(resolveRoleHome(['AUDITOR'], ['dashboard:view'])).toBe('/cockpit/hub/operation-group?tab=overview')
  })

  it('财务 → 工程应付与支出（付款申请 Tab）', () => {
    expect(resolveRoleHome(['FINANCE_STAFF'], ['finance:view']))
      .toBe('/finance/hub/payable-group?tab=workbench')
  })

  it('材料 → 材料与采购（入库 Tab）', () => {
    expect(resolveRoleHome(['MATERIAL_STAFF'], ['material:view']))
      .toBe('/material/hub/material-purchase-group?tab=inbound')
  })

  it('项目经理 → 项目台账', () => {
    expect(resolveRoleHome(['PROJECT_MANAGER'], ['project:view']))
      .toBe('/project/hub/ledger-group?tab=list')
  })

  it('商务 → 总包合同与产值', () => {
    expect(resolveRoleHome(['COMMERCE_STAFF'], ['contract:view']))
      .toBe('/contract/hub/contract-group?tab=list')
  })

  it('成本预算 → 目标成本管理', () => {
    expect(resolveRoleHome(['COST_BUDGET_STAFF'], ['budget:view']))
      .toBe('/budget/hub/cost-group?tab=list')
  })

  it('普通员工 → 首页', () => {
    expect(resolveRoleHome(['STAFF'], ['dashboard:view', 'message:view'])).toBe('/dashboard')
  })

  it('未知角色按权限兜底：finance > project > material > contract', () => {
    expect(resolveRoleHome([], ['finance:view', 'project:view']))
      .toBe('/finance/hub/payable-group?tab=workbench')
    expect(resolveRoleHome([], ['project:view', 'material:view']))
      .toBe('/project/hub/ledger-group?tab=list')
    expect(resolveRoleHome([], ['material:view']))
      .toBe('/material/hub/material-purchase-group?tab=inbound')
    expect(resolveRoleHome([], ['contract:view']))
      .toBe('/contract/hub/contract-group?tab=list')
  })

  it('一人多角色按固定优先级取第一个（FINANCE 先于 STAFF）', () => {
    expect(resolveRoleHome(['STAFF', 'FINANCE_STAFF'], ['finance:view']))
      .toBe('/finance/hub/payable-group?tab=workbench')
  })

  it('角色命中但缺对应 view 码 → 回落到仍可进入的首页', () => {
    // 项目经理角色被收窄：没有 project:view，只剩 dashboard:view
    expect(resolveRoleHome(['PROJECT_MANAGER'], ['dashboard:view'])).toBe('/dashboard')
  })

  it('角色命中但无任何可用 view 码 → /403（避免登录后再次被守卫拦截）', () => {
    expect(resolveRoleHome(['PROJECT_MANAGER'], [])).toBe('/403')
    expect(resolveRoleHome(['STAFF'], ['message:view'])).toBe('/403')
  })

  it('空角色空权限 → /403（安全兜底）', () => {
    expect(resolveRoleHome([], [])).toBe('/403')
    expect(resolveRoleHome()).toBe('/403')
  })
})
