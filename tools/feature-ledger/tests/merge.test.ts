import { describe, expect, it } from 'vitest';
import { mergeLedger } from '../src/merge.js';
import type { LedgerEntry } from '../src/types.js';

function makeEntry(overrides: Partial<LedgerEntry> = {}): LedgerEntry {
  return {
    featureId: 'A-pc-project-list',
    module: 'project',
    group: 'A',
    platform: 'pc',
    pagePath: '/project',
    pageFile: 'zw-insight-web/src/views/project/index.vue',
    title: '项目管理',
    signals: {},
    levelAuto: 1,
    confidence: 'high',
    lineCount: 200,
    scoreReasons: [],
    ...overrides,
  };
}

describe('mergeLedger 合并（auto/manual 分离）', () => {
  it('首跑：全部条目为新增', () => {
    const fresh = [
      makeEntry(),
      makeEntry({ featureId: 'B-pc-material-list', module: 'material', group: 'B' }),
    ];
    const r = mergeLedger(null, fresh, 1);
    expect(r.added).toHaveLength(2);
    expect(r.removed).toHaveLength(0);
    expect(r.data.entries).toHaveLength(2);
  });

  it('重跑：manual 字段保留，auto 字段覆写', () => {
    const old = makeEntry({
      levelAuto: 1,
      levelFinal: 2,
      gapNotes: { efficiency: '缺批量操作' },
      roi: { impact: 4, effort: 2 },
    });
    const previous = { signalVersion: 1, generatedAt: 'x', entries: [old] };
    const fresh = [makeEntry({ levelAuto: 3, lineCount: 300 })];
    const r = mergeLedger(previous, fresh, 1);
    const merged = r.data.entries[0];
    expect(merged.levelAuto).toBe(3); // auto 覆写
    expect(merged.lineCount).toBe(300);
    expect(merged.levelFinal).toBe(2); // manual 保留
    expect(merged.gapNotes?.efficiency).toBe('缺批量操作');
    expect(merged.roi?.impact).toBe(4);
    expect(r.preserved).toBe(1);
  });

  it('页面消失 → 标 removed（保留历史判断）', () => {
    const previous = { signalVersion: 1, generatedAt: 'x', entries: [makeEntry()] };
    const r = mergeLedger(previous, [], 1);
    expect(r.removed).toEqual(['A-pc-project-list']);
    expect(r.data.entries[0].removed).toBe(true);
  });

  it('removed 条目重新出现 → 复活且保留 manual', () => {
    const previous = {
      signalVersion: 1,
      generatedAt: 'x',
      entries: [makeEntry({ removed: true, levelFinal: 2 })],
    };
    const r = mergeLedger(previous, [makeEntry()], 1);
    expect(r.data.entries[0].removed).toBeUndefined();
    expect(r.data.entries[0].levelFinal).toBe(2);
  });

  it('scopeModules 增量：scope 外条目原样保留、不标 removed', () => {
    const previous = {
      signalVersion: 1,
      generatedAt: 'x',
      entries: [
        makeEntry({ module: 'project' }),
        makeEntry({ featureId: 'B-pc-material-list', module: 'material', group: 'B' }),
      ],
    };
    const fresh = [makeEntry({ module: 'project', levelAuto: 3 })]; // 只扫 project
    const r = mergeLedger(previous, fresh, 1, new Set(['project']));
    expect(r.removed).toHaveLength(0);
    const material = r.data.entries.find((e) => e.module === 'material');
    expect(material?.removed).toBeUndefined();
  });

  it('signalVersion 变更 → versionChanged 为 true', () => {
    const previous = { signalVersion: 1, generatedAt: 'x', entries: [makeEntry()] };
    const r = mergeLedger(previous, [makeEntry()], 2);
    expect(r.versionChanged).toBe(true);
  });
});
