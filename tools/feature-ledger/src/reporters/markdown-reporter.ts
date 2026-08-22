/**
 * Markdown 报告生成器：ledger-data.json → 首屏即答案的聚合报告
 *
 * 设计：全量明细（含逐维 path:line 证据）留在 JSON，报告只放聚合——
 * 分布总览 / Top20 / ROI 清单 / 分组明细，行数控制在 ~300 内
 * （对齐 tests/flow-matrix.json + coverage-matrix.md 的「JSON 存明细、MD 存聚合」先例）。
 */
import { MOBILE_DIMENSIONS } from '../signals.js';
import { DIMENSION_LABELS, GROUP_LABELS, LEVEL_LABELS } from '../types.js';
import type { Dimension, FeatureGroup, LedgerData, LedgerEntry, Level } from '../types.js';

/** 全部八维 */
const ALL_DIMENSIONS: Dimension[] = [
  'efficiency', 'query', 'state', 'audit', 'notify', 'permission', 'error', 'value',
];

/** 缺口紧凑展示单字（明细表/Top20 用） */
const DIMENSION_SHORT: Record<Dimension, string> = {
  efficiency: '效', query: '查', state: '状', audit: '追',
  notify: '通', permission: '权', error: '异', value: '值',
};

/** 有效等级（人工 levelFinal 优先，未复核时用 levelAuto 建议分） */
export function effectiveLevel(entry: LedgerEntry): Level {
  return entry.levelFinal ?? entry.levelAuto;
}

/** 条目适用维度（mobile 三维子集） */
function dimensionsOf(entry: LedgerEntry): Dimension[] {
  return entry.platform === 'pc' ? ALL_DIMENSIONS : [...MOBILE_DIMENSIONS];
}

/** 条目缺失维度（适用维度中信号计数为 0 的） */
export function missingDimensions(entry: LedgerEntry): Dimension[] {
  return dimensionsOf(entry).filter((d) => (entry.signals[d]?.count ?? 0) === 0);
}

/** 信号命中总数 */
function totalSignals(entry: LedgerEntry): number {
  let n = 0;
  for (const ev of Object.values(entry.signals)) n += ev?.count ?? 0;
  return n;
}

/** markdown 表格单元格转义 */
function cell(s: string): string {
  return s.replace(/\|/g, '\\|');
}

/** 等级单元格：仅 auto 显示 L1；final 覆盖时显示 L1→L2 */
function levelCell(e: LedgerEntry): string {
  if (e.levelFinal === undefined) return `L${e.levelAuto}`;
  if (e.levelFinal === e.levelAuto) return `L${e.levelFinal}`;
  return `L${e.levelAuto}→L${e.levelFinal}`;
}

/** 复核状态单元格：✓ 已复核 / ⚠ 待复核 / 空 */
function reviewCell(e: LedgerEntry): string {
  if (e.levelFinal !== undefined) return '✓';
  return e.confidence === 'needs-review' ? '⚠' : '';
}

/** 缺口单元格：缺失维度单字串（如「效查状」），全齐显示 — */
function gapCell(e: LedgerEntry): string {
  const missing = missingDimensions(e);
  return missing.length === 0 ? '—' : missing.map((d) => DIMENSION_SHORT[d]).join('');
}

/** 百分比 */
function pct(n: number, total: number): string {
  if (total === 0) return '—';
  return `${Math.round((n / total) * 100)}%`;
}

/** 人工判断标记（明细表用） */
function manualCell(e: LedgerEntry): string {
  return e.levelFinal !== undefined ||
    e.gapNotes !== undefined ||
    e.benchmarkNote !== undefined ||
    e.roi !== undefined
    ? '✓'
    : '';
}

/** 渲染完整报告 */
export function renderMarkdownReport(data: LedgerData): string {
  const active = data.entries.filter((e) => !e.removed);
  const removedEntries = data.entries.filter((e) => e.removed);
  const pcCount = active.filter((e) => e.platform === 'pc').length;
  const mobileCount = active.length - pcCount;
  const reviewed = active.filter((e) => e.levelFinal !== undefined).length;
  const lines: string[] = [];

  // ---- 头部 ----
  lines.push('# 功能深度账本报告', '');
  lines.push(
    '> 本文件由 `tools/feature-ledger` 脚本生成，**请勿手改**。',
    '> 人工判断请编辑 `tools/feature-ledger/data/ledger-data.json` 的 manual 字段',
    '> （levelFinal / gapNotes / benchmarkNote / roi），再重跑 `npm run dev -- report` 刷新本报告。',
    '',
  );
  lines.push(`- 生成时间：${new Date(data.generatedAt).toLocaleString('zh-CN')}`);
  lines.push(`- 信号规则版本：v${data.signalVersion}`);
  lines.push(`- 条目：${active.length}（PC ${pcCount} / 移动端 ${mobileCount}）`);
  lines.push(`- 人工复核进度：${reviewed} / ${active.length}（${pct(reviewed, active.length)}）`);
  lines.push('');

  // ---- 一、成熟度分布总览 ----
  lines.push('## 一、成熟度分布总览', '');
  const dist = new Map<Level, number>();
  for (const e of active) {
    const lv = effectiveLevel(e);
    dist.set(lv, (dist.get(lv) ?? 0) + 1);
  }
  lines.push('| 等级 | 条目数 | 占比 |', '|---|---:|---:|');
  for (const lv of [0, 1, 2, 3, 4] as Level[]) {
    lines.push(`| ${LEVEL_LABELS[lv]} | ${dist.get(lv) ?? 0} | ${pct(dist.get(lv) ?? 0, active.length)} |`);
  }
  lines.push('');

  lines.push('### 八维缺口计数（适用维度中信号为 0 的条目数）', '');
  lines.push('| 维度 | PC 缺失 | 移动端缺失* |', '|---|---:|---:|');
  for (const d of ALL_DIMENSIONS) {
    const pcMiss = active.filter((e) => e.platform === 'pc' && (e.signals[d]?.count ?? 0) === 0).length;
    const applicable = (MOBILE_DIMENSIONS as readonly Dimension[]).includes(d);
    const mobMiss = applicable
      ? active.filter((e) => e.platform === 'mobile' && (e.signals[d]?.count ?? 0) === 0).length
      : -1;
    lines.push(
      `| ${DIMENSION_LABELS[d]} | ${pcMiss}/${pcCount} | ${mobMiss < 0 ? '—' : `${mobMiss}/${mobileCount}`} |`,
    );
  }
  lines.push('', '\\* 移动端仅评效率/异常/通知三维子集，其余维度不适用。', '');

  // ---- 二、最基础功能 Top 20 ----
  lines.push('## 二、最基础功能 Top 20', '');
  lines.push('按有效等级（levelFinal ?? levelAuto）升序，同分按信号总数升序（越少越基础）。', '');
  const top20 = [...active]
    .sort(
      (a, b) =>
        effectiveLevel(a) - effectiveLevel(b) ||
        totalSignals(a) - totalSignals(b) ||
        a.group.localeCompare(b.group) ||
        a.pagePath.localeCompare(b.pagePath),
    )
    .slice(0, 20);
  lines.push('| # | 页面 | 分组/模块 | 等级 | 缺口 | 复核 |', '|---:|---|---|---|---|:---:|');
  top20.forEach((e, i) => {
    lines.push(
      `| ${i + 1} | ${cell(e.title)} | ${e.group}-${e.module} | ${levelCell(e)} | ${gapCell(e)} | ${reviewCell(e)} |`,
    );
  });
  lines.push('');

  // ---- 三、ROI 差距清单 ----
  lines.push('## 三、ROI 差距清单', '');
  const roiEntries = active.filter((e) => e.roi !== undefined);
  if (roiEntries.length === 0) {
    lines.push('暂无 ROI 评估条目——人工复核填写 `roi` 字段（impact × effort）后重跑 report 生成。', '');
  } else {
    const priorityOrder: Record<'P0' | 'P1' | 'P2', number> = { P0: 0, P1: 1, P2: 2 };
    roiEntries.sort((a, b) => {
      const pa = priorityOrder[a.roi?.priority ?? 'P2'];
      const pb = priorityOrder[b.roi?.priority ?? 'P2'];
      return (
        pa - pb ||
        (b.roi?.impact ?? 3) - (a.roi?.impact ?? 3) ||
        (a.roi?.effort ?? 3) - (b.roi?.effort ?? 3)
      );
    });
    lines.push('按优先级 P0 → P1 → P2 → 未定，同级 Impact 降序、Effort 升序。', '');
    lines.push('| 优先级 | 页面 | 等级 | Impact | Effort | 建议补齐 |', '|---|---|---|:---:|:---:|---|');
    for (const e of roiEntries) {
      const suggest = e.gapNotes
        ? Object.keys(e.gapNotes)
            .map((d) => DIMENSION_SHORT[d as Dimension] ?? d)
            .join('')
        : gapCell(e);
      lines.push(
        `| ${e.roi?.priority ?? '未定'} | ${cell(e.title)} | ${levelCell(e)} | ${e.roi?.impact}/5 | ${e.roi?.effort}/5 | ${suggest || '—'} |`,
      );
    }
    lines.push('');
  }

  // ---- 四、分组明细 ----
  lines.push('## 四、分组明细', '');
  lines.push(
    '缺口列单字：效=批量/导入导出，查=组合筛选，状=状态机，追=审计日志，通=消息触达，',
    '权=按钮级权限，异=错误恢复，值=聚合分析。「人工」列=该条目含任意人工判断字段，内容见 JSON。',
    '',
  );
  for (const group of ['A', 'B', 'C', 'D'] as FeatureGroup[]) {
    const groupEntries = active.filter((e) => e.group === group);
    if (groupEntries.length === 0) continue;
    lines.push(`### ${GROUP_LABELS[group]}（${groupEntries.length} 条）`, '');
    lines.push('| 页面 | 路由 | 模块 | 等级 | 置信度 | 缺口 | 人工 |', '|---|---|---|---|:---:|---|:---:|');
    for (const e of groupEntries) {
      lines.push(
        `| ${cell(e.title)} | ${e.pagePath} | ${e.module} | ${levelCell(e)} | ${e.confidence === 'needs-review' ? '⚠' : 'high'} | ${gapCell(e)} | ${manualCell(e)} |`,
      );
    }
    lines.push('');
  }

  // ---- 附录 ----
  lines.push('## 附录', '');
  lines.push('### 清单扫描警告', '');
  if (data.warnings && data.warnings.length > 0) {
    for (const w of data.warnings) lines.push(`- ${w}`);
  } else {
    lines.push('无');
  }
  lines.push('');
  lines.push('### 已下线页面（removed，保留历史判断）', '');
  if (removedEntries.length > 0) {
    for (const e of removedEntries) lines.push(`- ${e.featureId}（${e.title}，最后等级 ${levelCell(e)}）`);
  } else {
    lines.push('无');
  }
  lines.push('');

  return lines.join('\n');
}
