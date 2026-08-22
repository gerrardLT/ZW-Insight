import { describe, expect, it } from 'vitest';
import { runRuleHits, runSignalRules } from '../src/capability-scanner.js';
import { MOBILE_DIMENSIONS, SIGNAL_RULES, SIGNAL_VERSION } from '../src/signals.js';

describe('信号规则表快照', () => {
  it('规则数量与八维覆盖稳定（改规则请同步更新此快照）', () => {
    expect(SIGNAL_RULES.length).toBe(23);
    const dims = new Set(SIGNAL_RULES.map((r) => r.dimension));
    expect(dims.size).toBe(8);
  });

  it('SIGNAL_VERSION 为正整数', () => {
    expect(SIGNAL_VERSION).toBeGreaterThanOrEqual(1);
  });

  it('移动端三维子集（效率/异常/通知）', () => {
    expect([...MOBILE_DIMENSIONS].sort()).toEqual(['efficiency', 'error', 'notify'].sort());
  });
});

describe('规则匹配与豁免', () => {
  it('批量选择 + 导出命中 efficiency', () => {
    const content = '<el-table-column type="selection" />\n<button @click="handleExport">导出</button>';
    const signals = runSignalRules(content, 'a.vue', 'vue');
    expect(signals.get('efficiency')?.count).toBe(2);
  });

  it('导入 exemption 生效：value="IMPORT" 的 el-upload 不计', () => {
    const hit = runSignalRules('<el-upload />', 'a.vue', 'vue');
    expect(hit.get('efficiency')?.count).toBe(1);
    const exempt = runSignalRules('<el-upload value="IMPORT" />', 'a.vue', 'vue');
    expect(exempt.get('efficiency')).toBeUndefined();
  });

  it('流转端点命中 java state', () => {
    const content = '@PostMapping("/contract/submit")\npublic Result submit() { return null; }';
    const signals = runSignalRules(content, 'A.java', 'java');
    expect(signals.get('state')?.count).toBe(1);
  });

  it('@OperLog 命中 audit', () => {
    const content = '@OperLog(title = "合同", businessType = BusinessType.INSERT)';
    const signals = runSignalRules(content, 'A.java', 'java');
    expect(signals.get('audit')?.count).toBe(1);
  });

  it('runSignalRules 与 runRuleHits 计数口径一致', () => {
    const content = '<el-select v-model="q.status" />\n<el-date-picker v-model="q.date" />';
    const signals = runSignalRules(content, 'a.vue', 'vue');
    const hits = runRuleHits(content, 'vue');
    const signalTotal = [...signals.values()].reduce((s, e) => s + e.count, 0);
    const hitTotal = Object.values(hits).reduce((s, n) => s + n, 0);
    expect(signalTotal).toBe(hitTotal);
    expect(hitTotal).toBeGreaterThan(0);
  });
});
