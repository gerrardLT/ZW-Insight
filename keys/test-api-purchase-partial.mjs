// Real API probe. Only isolated tenant 9999; supply an approved inbound fixture.
import assert from 'node:assert/strict'
const { ZWI_API, ZWI_TOKEN, ZWI_TENANT_ID, ZWI_PARTIAL_INBOUND_ID } = process.env
assert.equal(ZWI_TENANT_ID, '9999', 'Only tenant 9999 is permitted')
assert.ok(ZWI_API && ZWI_TOKEN && ZWI_PARTIAL_INBOUND_ID, 'Supply real API/token/approved inbound fixture')
async function call(path, method = 'GET', body) {
  const response = await fetch(`${ZWI_API}/api/v1${path}`, {
    method, headers: { Authorization: `Bearer ${ZWI_TOKEN}`, 'tenant-id': '9999', 'Content-Type': 'application/json' },
    ...(body ? { body: JSON.stringify(body) } : {})
  })
  const data = await response.json()
  return { response, data }
}
const source = await call(`/purchase/settlement/inbound-lines?inboundId=${ZWI_PARTIAL_INBOUND_ID}`)
assert.equal(source.data.code, 200)
assert.ok(Array.isArray(source.data.data) && source.data.data.length > 0)
const line = source.data.data[0]
assert.ok(line.inboundDetailId)
assert.ok(Number.isFinite(Number(line.availableQuantity)))
// Invalid requests must fail without persisting a draft or consuming source quantity.
for (const details of [[], [{ inboundDetailId: line.inboundDetailId, quantity: -1 }],
  [{ inboundDetailId: line.inboundDetailId, quantity: Number(line.availableQuantity) + 1 }],
  [{ inboundDetailId: line.inboundDetailId, quantity: 1 }, { inboundDetailId: line.inboundDetailId, quantity: 1 }]]) {
  const result = await call('/purchase/settlement', 'POST', { inboundId: ZWI_PARTIAL_INBOUND_ID, details })
  assert.notEqual(result.data.code, 200, 'Invalid batch must not create settlement')
}
const after = await call(`/purchase/settlement/inbound-lines?inboundId=${ZWI_PARTIAL_INBOUND_ID}`)
assert.deepEqual(after.data.data, source.data.data)
console.log('PASS: source contract, invalid quantity/duplicate/overflow, unchanged availability')
