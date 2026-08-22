/**
 * 账本合并器：重跑 scan 时按 featureId 主键合并，保护人工判断
 *
 * 核心语义：
 *   - fresh 条目覆写 auto 层（signals/levelAuto/confidence/lineCount/scoreReasons 及基本字段）
 *   - 旧条目的 manual 层（levelFinal/gapNotes/benchmarkNote/roi）原样搬到合并结果
 *   - 旧有 fresh 无 → 标 removed:true（保留全部字段与历史判断，不物理删除）
 *   - removed 条目重新出现 → 天然复活（fresh 无 removed 字段）
 *   - scopeModules 模式（增量复核）：仅指定模块参与增删判定，其余旧条目原样保留
 */
import type { LedgerData, LedgerEntry } from './types.js';

/** 合并结果 */
export interface MergeResult {
  data: LedgerData;
  /** 本次新增的 featureId */
  added: string[];
  /** 本次标记下线的 featureId */
  removed: string[];
  /** 保留了 manual 层字段（levelFinal/gapNotes/benchmarkNote/roi 任一）的条目数 */
  preserved: number;
  /** 旧账本 signalVersion 与当前不一致（提示全量复核） */
  versionChanged: boolean;
}

/** 从旧条目搬运 manual 层字段到合并条目 */
function carryManualFields(fresh: LedgerEntry, old: LedgerEntry): LedgerEntry {
  const merged: LedgerEntry = { ...fresh };
  if (old.levelFinal !== undefined) merged.levelFinal = old.levelFinal;
  if (old.gapNotes !== undefined) merged.gapNotes = old.gapNotes;
  if (old.benchmarkNote !== undefined) merged.benchmarkNote = old.benchmarkNote;
  if (old.roi !== undefined) merged.roi = old.roi;
  return merged;
}

/** 判断条目是否含任何人工判断 */
function hasManualFields(entry: LedgerEntry): boolean {
  return (
    entry.levelFinal !== undefined ||
    entry.gapNotes !== undefined ||
    entry.benchmarkNote !== undefined ||
    entry.roi !== undefined
  );
}

/**
 * 合并旧账本与新扫描条目。
 *
 * @param previous 旧账本数据（null 表示首跑）
 * @param freshEntries 新扫描产出的条目（调用方保证已按需过滤）
 * @param signalVersion 当前信号规则版本
 * @param scopeModules 增量模式：仅这些模块参与增删判定，其余旧条目原样保留（null = 全量）
 */
export function mergeLedger(
  previous: LedgerData | null,
  freshEntries: LedgerEntry[],
  signalVersion: number,
  scopeModules: Set<string> | null = null,
): MergeResult {
  const prevEntries = previous?.entries ?? [];
  const prevMap = new Map(prevEntries.map((e) => [e.featureId, e]));
  const freshIds = new Set(freshEntries.map((e) => e.featureId));

  const added: string[] = [];
  const removed: string[] = [];
  let preserved = 0;

  // 1) fresh 条目：覆写 auto 层 + 搬运 manual 层
  const entries: LedgerEntry[] = freshEntries.map((fresh) => {
    const old = prevMap.get(fresh.featureId);
    if (!old) {
      added.push(fresh.featureId);
      return fresh;
    }
    const merged = carryManualFields(fresh, old);
    if (hasManualFields(merged)) preserved++;
    return merged;
  });

  // 2) 旧条目中未出现在 fresh 的：scope 内标 removed，scope 外原样保留
  for (const old of prevEntries) {
    if (freshIds.has(old.featureId)) continue;
    if (scopeModules !== null && !scopeModules.has(old.module)) {
      entries.push(old); // 增量模式：本次未扫描的模块不动
      continue;
    }
    if (!old.removed) removed.push(old.featureId);
    entries.push({ ...old, removed: true });
  }

  // 3) 稳定排序：分组 → 模块 → 路由路径（removed 条目仍按原归属排列）
  entries.sort(
    (a, b) =>
      a.group.localeCompare(b.group) ||
      a.module.localeCompare(b.module) ||
      a.pagePath.localeCompare(b.pagePath),
  );

  return {
    data: {
      signalVersion,
      generatedAt: new Date().toISOString(),
      warnings: previous?.warnings, // CLI 层每次 scan 会全量刷新覆盖
      entries,
    },
    added,
    removed,
    preserved,
    versionChanged: previous !== null && previous.signalVersion !== signalVersion,
  };
}
