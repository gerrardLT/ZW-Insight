import { describe, expect, it } from 'vitest';
import { scoreEntry, type ScoringInput } from '../src/scoring.js';

function makeInput(overrides: Partial<ScoringInput> = {}): ScoringInput {
  return {
    platform: 'pc',
    pageExists: true,
    lineCount: 200,
    signals: {},
    ruleHits: {},
    controllerFallback: false,
    hasBackend: true,
    ...overrides,
  };
}

describe('scoreEntry 评分规则', () => {
  it('页面缺失 → L0 + needs-review', () => {
    const r = scoreEntry(makeInput({ pageExists: false }));
    expect(r.levelAuto).toBe(0);
    expect(r.confidence).toBe('needs-review');
  });

  it('行数低于占位阈值 → L0', () => {
    const r = scoreEntry(makeInput({ lineCount: 30 }));
    expect(r.levelAuto).toBe(0);
    expect(r.confidence).toBe('needs-review');
  });

  it('仅基础信号 → L1', () => {
    const r = scoreEntry(makeInput({ signals: { query: { count: 3, evidence: [] } } }));
    expect(r.levelAuto).toBe(1);
  });

  it('状态渲染/门禁信号 → L2（PC，高置信）', () => {
    const r = scoreEntry(
      makeInput({
        signals: { query: { count: 3, evidence: [] }, state: { count: 2, evidence: [] } },
        ruleHits: { '状态渲染映射': 2 },
      }),
    );
    expect(r.levelAuto).toBe(2);
    expect(r.confidence).toBe('high');
  });

  it('流转端点 → L3', () => {
    const r = scoreEntry(
      makeInput({
        signals: { query: { count: 3, evidence: [] }, state: { count: 2, evidence: [] } },
        ruleHits: { '流转端点(submit/approve/withdraw/reject)': 1 },
      }),
    );
    expect(r.levelAuto).toBe(3);
  });

  it('统计/图表信号 → L4（优先级高于 L3）', () => {
    const r = scoreEntry(
      makeInput({
        signals: { query: { count: 3, evidence: [] }, value: { count: 1, evidence: [] } },
        ruleHits: { '流转端点(submit/approve/withdraw/reject)': 1 },
      }),
    );
    expect(r.levelAuto).toBe(4);
  });

  it('controllerFallback → needs-review', () => {
    const r = scoreEntry(makeInput({ controllerFallback: true }));
    expect(r.confidence).toBe('needs-review');
  });

  it('信号全 0 → needs-review', () => {
    const r = scoreEntry(makeInput());
    expect(r.confidence).toBe('needs-review');
  });

  it('PC L3 但无 query 信号 → needs-review（信号冲突）', () => {
    const r = scoreEntry(
      makeInput({
        signals: { state: { count: 1, evidence: [] } },
        ruleHits: { '流转端点(submit/approve/withdraw/reject)': 1 },
      }),
    );
    expect(r.levelAuto).toBe(3);
    expect(r.confidence).toBe('needs-review');
  });

  it('mobile：error 信号 → L2', () => {
    const r = scoreEntry(
      makeInput({ platform: 'mobile', lineCount: 120, signals: { error: { count: 2, evidence: [] } } }),
    );
    expect(r.levelAuto).toBe(2);
  });

  it('稀疏行数 → needs-review', () => {
    const r = scoreEntry(makeInput({ lineCount: 50, signals: { query: { count: 1, evidence: [] } } }));
    expect(r.confidence).toBe('needs-review');
  });
});
