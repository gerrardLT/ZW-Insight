import { describe, it, expect, vi, beforeEach } from 'vitest'

// ---- mock request (使用 vi.hoisted 确保提升后仍可访问) ----
const { mockRequest } = vi.hoisted(() => ({
  mockRequest: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
  },
}))

vi.mock('@/utils/request', () => ({ default: mockRequest }))

// ---- import SUT ----
import {
  getProjectPage,
  getProjectList,
  getProjectDetail,
  createProject,
  updateProject,
  deleteProject,
  submitProject,
  getOwnerList,
  getOwnerPage,
  getCompanyList,
  getProjectMembers,
  addProjectMember,
  removeProjectMember,
  updateMemberRoles,
} from '@/api/project'

describe('project API 模块', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  // ======================== 项目管理 ========================

  it('getProjectPage 调用 GET /v1/project/page', () => {
    const params = { pageNum: 1, pageSize: 10 }
    getProjectPage(params)
    expect(mockRequest.get).toHaveBeenCalledWith('/v1/project/page', { params })
  })

  it('getProjectList 调用 GET /v1/project/list', () => {
    const params = { keyword: 'test' }
    getProjectList(params)
    expect(mockRequest.get).toHaveBeenCalledWith('/v1/project/list', { params })
  })

  it('getProjectList 无参数', () => {
    getProjectList()
    expect(mockRequest.get).toHaveBeenCalledWith('/v1/project/list', { params: undefined })
  })

  it('getProjectDetail 调用 GET /v1/project/:id', () => {
    getProjectDetail(42)
    expect(mockRequest.get).toHaveBeenCalledWith('/v1/project/42')
  })

  it('createProject 调用 POST /v1/project', () => {
    const data = { name: '新项目', budget: 1000000 }
    createProject(data)
    expect(mockRequest.post).toHaveBeenCalledWith('/v1/project', data)
  })

  it('updateProject 调用 PUT /v1/project/:id', () => {
    const data = { id: 5, name: '更新项目' }
    updateProject(data)
    expect(mockRequest.put).toHaveBeenCalledWith('/v1/project/5', data)
  })

  it('deleteProject 调用 DELETE /v1/project/:id', () => {
    deleteProject(10)
    expect(mockRequest.delete).toHaveBeenCalledWith('/v1/project/10')
  })

  it('submitProject 调用 PUT /v1/project/:id/submit', () => {
    submitProject(7)
    expect(mockRequest.put).toHaveBeenCalledWith('/v1/project/7/submit')
  })

  // ======================== 建设单位（甲方） ========================

  it('getOwnerList 调用 GET /v1/basedata/owner/list', () => {
    const params = { keyword: '甲方A' }
    getOwnerList(params)
    expect(mockRequest.get).toHaveBeenCalledWith('/v1/basedata/owner/list', { params })
  })

  it('getOwnerPage 调用 GET /v1/basedata/owner/page', () => {
    getOwnerPage({ pageNum: 1 })
    expect(mockRequest.get).toHaveBeenCalledWith('/v1/basedata/owner/page', { params: { pageNum: 1 } })
  })

  // ======================== 签约公司 ========================

  it('getCompanyList 调用 GET /v1/basedata/company/list', () => {
    getCompanyList({ keyword: '公司' })
    expect(mockRequest.get).toHaveBeenCalledWith('/v1/basedata/company/list', { params: { keyword: '公司' } })
  })

  // ======================== 项目成员管理 ========================

  it('getProjectMembers 调用 GET /v1/project/:id/member', () => {
    getProjectMembers(1, { pageNum: 1 })
    expect(mockRequest.get).toHaveBeenCalledWith('/v1/project/1/member', { params: { pageNum: 1 } })
  })

  it('addProjectMember 调用 POST /v1/project/:id/member', () => {
    const data = { userId: 100, role: 'PM' }
    addProjectMember(1, data)
    expect(mockRequest.post).toHaveBeenCalledWith('/v1/project/1/member', data)
  })

  it('removeProjectMember 调用 DELETE /v1/project/:id/member/:userId', () => {
    removeProjectMember(1, 100)
    expect(mockRequest.delete).toHaveBeenCalledWith('/v1/project/1/member/100')
  })

  it('updateMemberRoles 调用 PUT /v1/project/:id/member/:userId/roles', () => {
    const data = { roles: ['PM', 'TECH'] }
    updateMemberRoles(1, 100, data)
    expect(mockRequest.put).toHaveBeenCalledWith('/v1/project/1/member/100/roles', data)
  })
})
