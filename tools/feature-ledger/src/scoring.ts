/**
 * 规则化评分器：从八维信号推导 L0-L4 建议分
 *
 * 确定性规则（自上而下取最高命中层）：
 *   L0 = 页面文件缺失 或 行数低于占位阈值
 *   L4 = value 维度有信号（统计/趋势/排行端点、图表）
 *   L3 = 存在流转端点（submit/approve/withdraw/reject 等）
 *   L2 = PC：状态渲染/状态门禁信号；mobile：表单校验/错误分支
 *   L1 = 默认（本项目列表页均为代码生成器 CRUD 骨架，页面存在即 L1 起步）
 *
 * levelAuto 只是建议分，最终事实以人工 levelFinal 为准。
 */
import { RULE_FLOW_ENDPOINT, RULE_STATE_GATE, RULE_STATE_RENDER } from './signals.js';
import type { Dimension, Level, Platform, SignalEvidence } from './types.js';
import type { RuleHits } from './capability-scanner.js';

/** 低于此行数视为占位页（L0） */
const L0_LINE_THRESHOLD = 40;
/** 信号稀疏判定阈值（PC） */
const SPARSE_LINE_PC = 80;
/** 信号稀疏判定阈值（mobile，移动端页面天然较短） */
const SPARSE_LINE_MOBILE = 60;

/** 评分输入 */
export interface ScoringInput {
  platform: Platform;
  pageExists: boolean;
  lineCount: number;
  /** 维度适用性过滤后的信号（mobile 仅三维子集） */
  signals: Partial<Record<Dimension, SignalEvidence>>;
  /** 规则名 → 命中次数（vue + 匹配 Controller 合并后） */
  ruleHits: RuleHits;
  /** Controller 是否为模块级回退匹配（归属不确定） */
  controllerFallback: boolean;
  /** 是否有对应后端模块（login/error 等纯前端页为 false） */
  hasBackend: boolean;
}

/** 评分结果 */
export interface ScoringResult {
  levelAuto: Level;
  confidence: 'high' | 'needs-review';
  reasons: string[];
}

/** 维度命中计数 */
function countOf(
  signals: Partial<Record<Dimension, SignalEvidence>>,
  dim: Dimension,
): number {
  return signals[dim]?.count ?? 0;
}

/** 全维度信号总计数 */
function totalSignalCount(signals: Partial<Record<Dimension, SignalEvidence>>): number {
  let total = 0;
  for (const ev of Object.values(signals)) total += ev?.count ?? 0;
  return total;
}

/** 对单个条目评分 */
export function scoreEntry(input: ScoringInput): ScoringResult {
  const { platform, pageExists, lineCount, signals, ruleHits, controllerFallback, hasBackend } = input;
  const reasons: string[] = [];

  // ---- L0：占位/缺失 ----
  if (!pageExists) {
    return {
      levelAuto: 0,
      confidence: 'needs-review',
      reasons: ['页面文件缺失（路由已声明但 views 无对应文件）'],
    };
  }
  if (lineCount < L0_LINE_THRESHOLD) {
    return {
      levelAuto: 0,
      confidence: 'needs-review',
      reasons: [`页面仅 ${lineCount} 行（< ${L0_LINE_THRESHOLD}），疑似占位页`],
    };
  }

  // ---- L4 → L1 逐层判定（取最高命中） ----
  let levelAuto: Level;
  const flowHits = ruleHits[RULE_FLOW_ENDPOINT] ?? 0;
  const stateHits = (ruleHits[RULE_STATE_RENDER] ?? 0) + (ruleHits[RULE_STATE_GATE] ?? 0);

  if (countOf(signals, 'value') > 0) {
    levelAuto = 4;
    reasons.push('存在统计/趋势/排行端点或图表组件（L4 数据智能）');
  } else if (flowHits > 0) {
    levelAuto = 3;
    reasons.push(`存在流转端点 submit/approve 等 ×${flowHits}（L3 协同流转）`);
  } else if (platform === 'pc' ? stateHits > 0 : countOf(signals, 'error') > 0) {
    levelAuto = 2;
    reasons.push(
      platform === 'pc'
        ? `存在状态渲染/状态门禁信号 ×${stateHits}（L2 规则完整）`
        : '存在表单校验/错误分支（L2 规则完整）',
    );
  } else {
    levelAuto = 1;
    reasons.push('仅基础 CRUD 信号，无状态/流转/统计差异化能力（L1）');
  }

  // ---- 置信度 ----
  const reviewReasons: string[] = [];
  const total = totalSignalCount(signals);
  if (total === 0) {
    reviewReasons.push('八维信号计数为 0（规则可能不适用于该页面形态）');
  }
  if (controllerFallback) {
    reviewReasons.push('后端信号为模块级回退匹配（页面级 Controller 未命中，归属不确定）');
  }
  if (!hasBackend && platform === 'pc') {
    reviewReasons.push('无对应后端模块（纯前端页，评分仅凭页面源码）');
  }
  if (platform === 'pc' && levelAuto >= 3 && countOf(signals, 'query') === 0) {
    reviewReasons.push('L3+ 却无查询筛选信号（信号冲突，疑似误匹配）');
  }
  const sparseLine = platform === 'pc' ? SPARSE_LINE_PC : SPARSE_LINE_MOBILE;
  if (lineCount < sparseLine) {
    reviewReasons.push(`页面 ${lineCount} 行（< ${sparseLine}），信号稀疏`);
  }

  reasons.push(...reviewReasons);
  return { levelAuto, confidence: reviewReasons.length > 0 ? 'needs-review' : 'high', reasons };
}
