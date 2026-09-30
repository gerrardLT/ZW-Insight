import { describe, it, expect } from 'vitest'
import { approvalBlock, batchBlock, money } from './approval'
const detail = { taskId: 't1', status: 'pending', businessType: 'PAYMENT_APPLY', businessId: '12', processInstanceId: 'p1' }
const payment = { id: '12', status: 'SUBMITTED', workflowInstanceId: 'p1', paymentAmount: 123, projectId: '21' }
describe('approval source gates', () => {
  it('blocks missing detail and failed payment source', () => {
    expect(approvalBlock(null, 't1')).toBeTruthy()
    expect(approvalBlock(detail, 't1')).toBeTruthy()
  })
  it('requires authoritative status and matching identities', () => {
    expect(approvalBlock(detail, 't1', payment)).toBe('')
    for (const patch of [{ status: undefined }, { status: 'done' }, { taskId: 'other' }, { businessId: null }]) expect(approvalBlock({ ...detail, ...patch }, 't1', payment)).toBeTruthy()
    for (const patch of [{ id: '13' }, { status: 'APPROVED' }, { workflowInstanceId: 'p2' }]) expect(approvalBlock(detail, 't1', { ...payment, ...patch })).toBeTruthy()
  })
  it('blocks invalid source amount and project identity', () => {
    for (const paymentAmount of [null, undefined, '', ' ', 'bad', NaN, Infinity, 0, -1, true, '0x10']) expect(approvalBlock(detail, 't1', { ...payment, paymentAmount })).toBeTruthy()
    for (const projectId of [null, undefined, '', 'bad', 0]) expect(approvalBlock(detail, 't1', { ...payment, projectId })).toBeTruthy()
    expect(approvalBlock(detail, 't1', { ...payment, paymentAmount: '123.45' })).toBe('')
  })
  it('blocks payment batches and mixed business types', () => {
    expect(batchBlock([detail])).toBeTruthy()
    expect(batchBlock([{ businessType: 'A' }, { businessType: 'B' }])).toBeTruthy()
    expect(batchBlock([{ businessType: 'A' }, { businessType: 'A' }])).toBe('')
  })
  it('never invents missing amounts', () => {
    for (const value of [null, undefined, '', 'bad', Infinity]) expect(money(value)).toBe('未提供')
    expect(money(0)).toContain('0.00')
  })
})
