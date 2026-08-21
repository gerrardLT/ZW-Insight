import { describe, it, expect } from 'vitest'
import { extractRecords } from '@/utils/list-response'

describe('extractRecords 列表响应归一化', () => {
  it('分页对象形态：返回 records 数组', () => {
    const res = {
      code: 200,
      message: 'success',
      data: {
        records: [
          { id: 1, roleName: '管理员', status: 1 },
          { id: 2, roleName: '项目经理', status: 1 }
        ],
        total: 2
      }
    }
    expect(extractRecords(res)).toEqual([
      { id: 1, roleName: '管理员', status: 1 },
      { id: 2, roleName: '项目经理', status: 1 }
    ])
  })

  it('纯数组形态：原样返回数组', () => {
    const res = {
      code: 200,
      message: 'success',
      data: [{ id: 10, menuName: '系统管理' }]
    }
    expect(extractRecords(res)).toEqual([{ id: 10, menuName: '系统管理' }])
  })

  it('空分页对象：返回空数组而非把对象当数组', () => {
    const res = { code: 200, data: { records: [], total: 0 } }
    expect(extractRecords(res)).toEqual([])
  })

  it('data 为 null/undefined/非法对象：兜底空数组', () => {
    expect(extractRecords({ code: 200, data: null })).toEqual([])
    expect(extractRecords({ code: 200 })).toEqual([])
    expect(extractRecords({ code: 200, data: { foo: 'bar' } })).toEqual([])
  })
})
