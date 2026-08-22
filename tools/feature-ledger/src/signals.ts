/**
 * 八维能力信号规则表（声明式配置，唯一可调参数）
 *
 * 维护约定：
 *   - 每条规则 = 一个维度的一组正则 + 作用范围（vue 页面源码 / java 后端源码）
 *   - exemption 为豁免正则：命中行同时匹配 exemption 时不计（消化「导出模板」类误报）
 *   - 规则修正后请将 SIGNAL_VERSION +1（触发账本全量复核提示）
 *   - 每维证据上限 EVIDENCE_CAP 条，防 JSON 膨胀
 */
import type { Dimension } from './types.js';

/** 信号规则版本（修正规则后 +1） */
export const SIGNAL_VERSION = 3;

/** 每维证据保留上限 */
export const EVIDENCE_CAP = 5;

// ---- 评分器引用的关键规则名（改名请同步 scoring.ts，常量化防悄悄失效） ----
/** 状态渲染映射（L2 判据） */
export const RULE_STATE_RENDER = '状态渲染映射';
/** 状态门禁按钮（L2 判据） */
export const RULE_STATE_GATE = '状态门禁(按钮)';
/** 流转端点 submit/approve/withdraw/reject 等（L3 判据） */
export const RULE_FLOW_ENDPOINT = '流转端点(submit/approve/withdraw/reject)';
/** 统计/趋势/排行端点（L4 判据） */
export const RULE_STAT_ENDPOINT = '统计/趋势/排行端点';
/** 图表组件（L4 判据） */
export const RULE_CHART = '图表组件';

/** 作用范围 */
export type SignalScope = 'vue' | 'java';

/** 单条信号规则 */
export interface SignalRule {
  dimension: Dimension;
  scope: SignalScope;
  /** 信号名称（证据展示用） */
  name: string;
  /** 命中正则（作用于逐行） */
  pattern: RegExp;
  /** 豁免正则（同行命中则不计入） */
  exemption?: RegExp;
}

/**
 * 八维信号规则全集。
 * 设计依据：2026-08-22 全仓 grep 实证（批量仅 2 处 / 导入仅 4 处 / 导出约 6 页 /
 * @OperLog 20 处 / v-permission 0 处 / @Scheduled 12 处）。
 */
export const SIGNAL_RULES: SignalRule[] = [
  // ---- 效率：批量 / 导入 / 导出 ----
  {
    dimension: 'efficiency', scope: 'vue', name: '批量操作(多选)',
    pattern: /type="selection"|@selection-change/,
  },
  {
    dimension: 'efficiency', scope: 'vue', name: 'Excel导入',
    pattern: /el-upload|批量导入|导入Excel|导入 Excel/,
    // 豁免：模板管理页的「导入模板」选项是模板类型枚举，非导入功能（实证误报）
    exemption: /value="IMPORT"|label="导入模板"/,
  },
  {
    dimension: 'efficiency', scope: 'vue', name: '导出',
    pattern: /handleExport|导出\s*Excel|导出Excel/,
    // 豁免：模板管理页的「导出模板」选项是模板类型枚举，非导出功能
    exemption: /value="EXPORT"|label="导出模板"/,
  },
  {
    dimension: 'efficiency', scope: 'vue', name: '移动端列表加载(loadMore)',
    pattern: /loadMore|onReachBottom/,
  },

  // ---- 查询：组合筛选 ----
  {
    dimension: 'query', scope: 'vue', name: '筛选控件(下拉)',
    pattern: /<el-select/,
  },
  {
    dimension: 'query', scope: 'vue', name: '筛选控件(日期)',
    pattern: /<el-date-picker/,
  },
  {
    dimension: 'query', scope: 'vue', name: '查询区输入框',
    pattern: /queryParams\.\w+/,
  },
  {
    dimension: 'query', scope: 'java', name: '可选查询参数',
    pattern: /@RequestParam\([^)]*required\s*=\s*false/,
  },

  // ---- 状态：状态机 ----
  {
    dimension: 'state', scope: 'vue', name: RULE_STATE_RENDER,
    pattern: /getStatusLabel|statusMap|getStatusType/,
  },
  {
    dimension: 'state', scope: 'vue', name: RULE_STATE_GATE,
    pattern: /v-if="[^"]*row\.status\s*===?/,
  },
  {
    dimension: 'state', scope: 'java', name: RULE_FLOW_ENDPOINT,
    // 兼容两种写法：@PostMapping("/{id}/submit") 与 @RequestMapping(value = "/{id}/submit", method = ...)
    pattern: /@(?:Post|Put|Get|Request)Mapping\((?:value = )?"[^"]*\/(submit|approve|withdraw|reject|close|publish|award|confirm)[^"]*"/,
  },

  // ---- 追溯：审计 ----
  {
    dimension: 'audit', scope: 'java', name: '操作日志注解',
    pattern: /@OperLog\(/,
  },
  {
    dimension: 'audit', scope: 'vue', name: '变更记录展示',
    pattern: /变更记录|操作记录|审批记录/,
  },

  // ---- 通知：消息触达 ----
  {
    dimension: 'notify', scope: 'java', name: '消息服务调用',
    pattern: /MessageService|messageService\./,
  },
  {
    dimension: 'notify', scope: 'java', name: '提醒任务',
    pattern: /ReminderTask|Reminder\b/,
  },
  {
    dimension: 'notify', scope: 'vue', name: '站内信/通知展示',
    pattern: /未读|通知列表|消息中心/,
  },

  // ---- 权限：按钮级 ----
  {
    dimension: 'permission', scope: 'java', name: '方法级权限注解',
    pattern: /@(RequiresPermissions|RequiresPermission)\(\s*\{|\@(RequiresPermissions|RequiresPermission)\(\s*"[\w:]+:[\w:]+:[\w]+/,
  },
  {
    dimension: 'permission', scope: 'vue', name: '按钮级权限指令',
    pattern: /v-permission|v-hasPerm|hasPermission\(/,
  },

  // ---- 异常：错误恢复 ----
  {
    dimension: 'error', scope: 'vue', name: '错误提示分支',
    pattern: /ElMessage\.error|ElMessageBox\.confirm/,
  },
  {
    dimension: 'error', scope: 'vue', name: '移动端反馈提示(uni)',
    pattern: /uni\.showToast|uni\.showModal/,
  },
  {
    dimension: 'error', scope: 'java', name: '驳回/回滚端点',
    pattern: /@(Post|Put)Mapping\([^)]*\/(reject|rollback|cancel|invalid)/,
  },

  // ---- 价值：聚合分析 ----
  {
    dimension: 'value', scope: 'java', name: RULE_STAT_ENDPOINT,
    // 实证词汇（2026-08-22 全后端扫描）：statistics/overview/trend/ranking/summary/dashboard/stats/compare/-stats/-analysis
    pattern: /@(Get|Post)Mapping\([^)]*(\/statistics|\/overview|\/trend|\/ranking|\/summary|\/dashboard|\/stats|\/compare|-stats|\/analysis)/,
  },
  {
    dimension: 'value', scope: 'vue', name: RULE_CHART,
    pattern: /<el-chart|echarts|v-chart/,
  },
];

/** 移动端只评三维子集（单表单页无组合筛选/状态机等概念，其余维度不硬塞） */
export const MOBILE_DIMENSIONS: readonly Dimension[] = ['efficiency', 'error', 'notify'];

/** 判定维度对平台是否适用 */
export function dimensionAppliesTo(dimension: Dimension, platform: 'pc' | 'mobile'): boolean {
  if (platform === 'pc') return true;
  return MOBILE_DIMENSIONS.includes(dimension);
}
