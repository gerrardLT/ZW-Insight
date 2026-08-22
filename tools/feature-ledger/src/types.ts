/**
 * 功能深度账本类型定义（单文件集中，对齐 consistency-audit/src/types.ts 风格）
 *
 * 核心设计：单一数据源。ledger-data.json 中每个条目分两层字段——
 *   auto 层（signals/levelAuto/confidence）：脚本每次 scan 重写
 *   manual 层（levelFinal/gapNotes/benchmarkNote/roi）：人工填写，重跑 merge 原样保留
 */

/** 八维缺口维度 */
export type Dimension =
  | 'efficiency'   // 效率：批量操作 / Excel 导入导出
  | 'query'        // 查询：组合筛选 / 视图保存
  | 'state'        // 状态：状态机完整性（门禁/流转分支）
  | 'audit'        // 追溯：操作审计日志 / 变更记录
  | 'notify'       // 通知：状态变化触达（消息/推送）
  | 'permission'   // 权限：按钮级（方法级注解 / v-permission）
  | 'error'        // 异常：错误提示分支 / 驳回回滚恢复路径
  | 'value';       // 价值：聚合分析（统计/趋势/排行端点）

/** 平台 */
export type Platform = 'pc' | 'mobile';

/** 功能分组（对齐 tests/frontend-test-case-matrix.md 的 A/B/C/D 分组惯例） */
export type FeatureGroup = 'A' | 'B' | 'C' | 'D';

/** 成熟度等级 */
export type Level = 0 | 1 | 2 | 3 | 4;

/** 单维度信号证据 */
export interface SignalEvidence {
  /** 命中次数 */
  count: number;
  /** 证据定位（相对仓库根的 path:line，最多保留若干条） */
  evidence: string[];
}

/** 账本条目 */
export interface LedgerEntry {
  /** 条目主键，如 "B3-material-inbound"（分组前缀 + 模块 + 页面名） */
  featureId: string;
  /** 模块名（前端视图域目录名，如 system / finance / material） */
  module: string;
  /** 功能分组 */
  group: FeatureGroup;
  platform: Platform;
  /** 路由 path（PC）或 pages.json path（mobile） */
  pagePath: string;
  /** view 文件相对仓库根路径 */
  pageFile: string;
  /** 页面标题（meta.title / navigationBarTitleText） */
  title: string;

  // ---- auto 层（脚本写）----
  /** 八维信号（移动端仅评 efficiency/error/notify 三维子集，其余维度缺省） */
  signals: Partial<Record<Dimension, SignalEvidence>>;
  /** 规则化建议分 */
  levelAuto: Level;
  /** 置信度：high=信号充分；needs-review=信号稀疏/冲突，需人工复核 */
  confidence: 'high' | 'needs-review';
  /** 页面源码行数（占位页识别辅助） */
  lineCount: number;
  /** 评分判定依据（每层结论 + 置信度触发原因，人工复核参考） */
  scoreReasons: string[];
  /** 规则命中次数（规则名 → 次数，复核 L2/L3 判据用；vue + Controller 合并后） */
  ruleHits?: Record<string, number>;

  // ---- manual 层（人工写，merge 保留）----
  /** 复核后的最终等级（未复核时缺省，报告以 levelAuto 兜底展示） */
  levelFinal?: Level;
  /** 各维度缺口的人工说明 */
  gapNotes?: Partial<Record<Dimension, string>>;
  /** 领域对标判断（行业产品功能矩阵参照） */
  benchmarkNote?: string;
  /** ROI 评估 */
  roi?: RoiAssessment;
  /** 页面已从路由移除（merge 标记，不物理删除以保留历史人工判断） */
  removed?: boolean;
}

/** ROI 评估 */
export interface RoiAssessment {
  /** 价值 1-5（频次 × 痛感 × 付费意愿） */
  impact: 1 | 2 | 3 | 4 | 5;
  /** 成本 1-5（1=极低，可复用既有基建） */
  effort: 1 | 2 | 3 | 4 | 5;
  /** 优先级（可缺省，由 impact/effort 推导） */
  priority?: 'P0' | 'P1' | 'P2';
}

/** 账本数据文件顶层结构 */
export interface LedgerData {
  /** 信号规则版本（规则修正后 +1，提示全量复核） */
  signalVersion: number;
  /** 最近一次 scan 时间 */
  generatedAt: string;
  /** 清单扫描警告（基准数偏离 / views 目录差异等，每次 scan 全量刷新） */
  warnings?: string[];
  entries: LedgerEntry[];
}

/** 页面清单条目（manifest-scanner 产出） */
export interface PageManifestItem {
  platform: Platform;
  pagePath: string;
  pageFile: string;
  title: string;
  module: string;
}

/** scan 命令运行结果摘要 */
export interface ScanSummary {
  pcPages: number;
  mobilePages: number;
  backendModules: number;
  controllersScanned: number;
  entries: number;
  needsReview: number;
  warnings: string[];
  durationMs: number;
}

/** 八维维度的中文标签 */
export const DIMENSION_LABELS: Record<Dimension, string> = {
  efficiency: '效率(批量/导入导出)',
  query: '查询(组合筛选)',
  state: '状态(状态机)',
  audit: '追溯(审计日志)',
  notify: '通知(消息触达)',
  permission: '权限(按钮级)',
  error: '异常(错误恢复)',
  value: '价值(聚合分析)',
};

/** 等级语义标签 */
export const LEVEL_LABELS: Record<Level, string> = {
  0: 'L0 缺失/占位',
  1: 'L1 单路径CRUD',
  2: 'L2 规则完整',
  3: 'L3 协同流转',
  4: 'L4 数据智能',
};

/** 分组语义标签 */
export const GROUP_LABELS: Record<FeatureGroup, string> = {
  A: 'A 核心主链',
  B: 'B 支出域',
  C: 'C 财务现场域',
  D: 'D 平台支撑域',
};
