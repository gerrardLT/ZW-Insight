/**
 * ECharts 受限色板与图表主题（Industrial Precision）
 * 依据 docs/DESIGN-mix-industrial-precision.md「Data Visualization」章节：
 * - 多序列默认灰阶，单一橙色留给关键序列
 * - 同实体跨图同色（语义色与全局 token 对齐）
 * - 亮/暗两套，由页面按 appStore.isDark 选取
 */

export interface ChartTheme {
  /** 混合色板：橙(关键序列优先) + 灰阶 + 语义色，echarts 自动循环 */
  palette: string[]
  /** 多序列灰阶（收支/趋势等对比图） */
  seriesGray: string[]
  /** 关键序列高亮橙 */
  highlight: string
  semantic: {
    success: string
    warning: string
    danger: string
    info: string
  }
  axis: {
    line: string
    label: string
    splitLine: string
  }
  /** 画布内文字（标题/图例等，非坐标轴） */
  text: {
    primary: string
    secondary: string
  }
  /** 画布表面色（饼图分隔描边等需要与卡片底融合处） */
  surface: {
    card: string
  }
  tooltip: {
    background: string
    border: string
    text: string
  }
}

export const chartThemeLight: ChartTheme = {
  palette: ['#ff6b00', '#8a8f98', '#b9bdb6', '#d9dcd6', '#1f9d55', '#f7b500', '#d92d20', '#2b6cb0'],
  seriesGray: ['#8a8f98', '#b9bdb6', '#d9dcd6'],
  highlight: '#ff6b00',
  semantic: {
    success: '#1f9d55',
    warning: '#f7b500',
    danger: '#d92d20',
    info: '#2b6cb0'
  },
  axis: {
    line: '#d9dcd6',
    label: '#5c6168',
    splitLine: '#e8eae6'
  },
  text: {
    primary: '#14161a',
    secondary: '#5c6168'
  },
  surface: {
    card: '#ffffff'
  },
  tooltip: {
    background: '#14161a',
    border: '#14161a',
    text: '#f2f3f1'
  }
}

export const chartThemeDark: ChartTheme = {
  palette: ['#ff6b00', '#8a8f98', '#b9bdb6', '#4a4f57', '#34c476', '#ffc53d', '#f97066', '#5a94d1'],
  seriesGray: ['#8a8f98', '#b9bdb6', '#4a4f57'],
  highlight: '#ff6b00',
  semantic: {
    success: '#34c476',
    warning: '#ffc53d',
    danger: '#f97066',
    info: '#5a94d1'
  },
  axis: {
    line: '#2a2d33',
    label: '#9ba0a8',
    splitLine: '#1c1f24'
  },
  text: {
    primary: '#f2f3f1',
    secondary: '#9ba0a8'
  },
  surface: {
    card: '#16181c'
  },
  tooltip: {
    background: '#23262c',
    border: '#2a2d33',
    text: '#f2f3f1'
  }
}

/** 选取当前主题（图表为 canvas 绘制，不读 CSS 变量，需在创建时固化） */
export function pickChartTheme(isDark: boolean): ChartTheme {
  return isDark ? chartThemeDark : chartThemeLight
}

/** 通用坐标轴样式片段（展开进 option 即可） */
export function chartAxisStyle(theme: ChartTheme) {
  return {
    axisLine: { lineStyle: { color: theme.axis.line } },
    axisTick: { lineStyle: { color: theme.axis.line } },
    axisLabel: { color: theme.axis.label },
    splitLine: { lineStyle: { color: theme.axis.splitLine } }
  }
}

/** 通用 tooltip 样式片段 */
export function chartTooltipStyle(theme: ChartTheme) {
  return {
    backgroundColor: theme.tooltip.background,
    borderColor: theme.tooltip.border,
    textStyle: { color: theme.tooltip.text }
  }
}

/** 坐标轴单个实例的主题默认值填充（保留调用方已有的 formatter 等配置） */
function themeAxis(axis: any, theme: ChartTheme) {
  if (!axis || typeof axis !== 'object') return axis
  const styled = { ...axis }
  if (!styled.axisLine) styled.axisLine = { lineStyle: { color: theme.axis.line } }
  if (!styled.axisTick) styled.axisTick = { lineStyle: { color: theme.axis.line } }
  styled.axisLabel = { color: theme.axis.label, ...styled.axisLabel }
  if (!styled.splitLine) styled.splitLine = { lineStyle: { color: theme.axis.splitLine } }
  if (styled.name && !styled.nameTextStyle) styled.nameTextStyle = { color: theme.axis.label }
  return styled
}

/**
 * 将主题默认值应用到任意 option（不覆盖调用方显式指定的字段）：
 * 色板、tooltip、坐标轴、图例/标题文字色。供 StatChartPanel 等统一应用，
 * 使调用方的 buildOption 无需自行感知亮/暗主题。
 */
export function applyChartTheme(option: any, theme: ChartTheme): any {
  if (!option || typeof option !== 'object') return option
  const o = { ...option }
  if (!o.color) o.color = theme.palette
  if (o.tooltip) {
    o.tooltip = { ...chartTooltipStyle(theme), ...o.tooltip }
  }
  if (Array.isArray(o.xAxis)) o.xAxis = o.xAxis.map((a: any) => themeAxis(a, theme))
  else if (o.xAxis) o.xAxis = themeAxis(o.xAxis, theme)
  if (Array.isArray(o.yAxis)) o.yAxis = o.yAxis.map((a: any) => themeAxis(a, theme))
  else if (o.yAxis) o.yAxis = themeAxis(o.yAxis, theme)
  if (o.legend) {
    o.legend = { textStyle: { color: theme.text.secondary }, ...o.legend }
  }
  if (o.title) {
    o.title = { textStyle: { color: theme.text.primary }, ...o.title }
  }
  return o
}
