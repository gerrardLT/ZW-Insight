---
version: alpha
name: Industrial-Precision-Mix-design-analysis
description: "A construction-industry engineering-management design system mixed from three source languages: SpaceX (industrial D-DIN display type with uppercase wide tracking and austere black-and-white hero bands), Linear (surface ladder + hairline borders + data-dense product-UI discipline for the SaaS app shell), and NVIDIA (2px angular geometry, blueprint corner tick-marks, and hard editorial grids). The single chromatic accent is Construction Safety Orange (#ff6b00) — the hue of safety vests, tower cranes, and hazard signage — replacing the conventional SaaS blue entirely. Light mode anchors on concrete-white canvas (#f6f7f5) with a permanent graphite-dark control-room sidebar; dark mode uses a graphite ladder (#101214 → #23262c). Signature moments: blueprint corner-marked cards, hazard-stripe accent bars, monospaced numeric ledgers, and uppercase DIN eyebrows. No drop shadows, no gradients, no pill CTAs — elevation is carried by surface lift and hairlines."

colors:
  primary: "#ff6b00"
  primary-hover: "#ff8a2e"
  primary-active: "#e05f00"
  on-primary: "#14161a"
  primary-soft: "#fff0e3"
  primary-soft-dark: "#3a2413"
  ink: "#14161a"
  ink-muted: "#4a4f57"
  ink-subtle: "#7c828c"
  ink-tertiary: "#a3a8b0"
  canvas: "#f6f7f5"
  surface-1: "#ffffff"
  surface-2: "#eef0ed"
  surface-3: "#e4e7e3"
  hairline: "#d9dcd6"
  hairline-strong: "#b9bdb6"
  canvas-dark: "#101214"
  surface-dark-1: "#16181c"
  surface-dark-2: "#1c1f24"
  surface-dark-3: "#23262c"
  hairline-dark: "#2b2e34"
  hairline-dark-strong: "#3d4048"
  ink-dark: "#f2f3f1"
  ink-dark-muted: "#c6c9cc"
  ink-dark-subtle: "#8a8f98"
  semantic-success: "#1f9d55"
  semantic-success-soft: "#e0f3e8"
  semantic-warning: "#f7b500"
  semantic-warning-soft: "#fdf3d7"
  semantic-error: "#d92d20"
  semantic-error-soft: "#fbe5e2"
  semantic-info: "#2b6cb0"
  semantic-info-soft: "#e2ecf7"
  hazard-black: "#14161a"
  hazard-yellow: "#ffc400"

typography:
  display-hero:
    fontFamily: "D-DIN-Bold, 'Barlow Condensed', 'Arial Narrow', sans-serif"
    fontSize: 56px
    fontWeight: 700
    lineHeight: 1.0
    letterSpacing: 1.2px
  display-lg:
    fontFamily: "D-DIN-Bold, 'Barlow Condensed', 'Arial Narrow', sans-serif"
    fontSize: 40px
    fontWeight: 700
    lineHeight: 1.05
    letterSpacing: 0.8px
  display-md:
    fontFamily: "D-DIN-Bold, 'Barlow Condensed', 'Arial Narrow', sans-serif"
    fontSize: 28px
    fontWeight: 700
    lineHeight: 1.1
    letterSpacing: 0.4px
  headline:
    fontFamily: "Inter, 'PingFang SC', 'Microsoft YaHei', system-ui, sans-serif"
    fontSize: 20px
    fontWeight: 600
    lineHeight: 1.3
    letterSpacing: -0.2px
  card-title:
    fontFamily: "Inter, 'PingFang SC', 'Microsoft YaHei', system-ui, sans-serif"
    fontSize: 16px
    fontWeight: 600
    lineHeight: 1.35
    letterSpacing: -0.1px
  body-lg:
    fontFamily: "Inter, 'PingFang SC', 'Microsoft YaHei', system-ui, sans-serif"
    fontSize: 16px
    fontWeight: 400
    lineHeight: 1.6
    letterSpacing: 0
  body:
    fontFamily: "Inter, 'PingFang SC', 'Microsoft YaHei', system-ui, sans-serif"
    fontSize: 14px
    fontWeight: 400
    lineHeight: 1.5
    letterSpacing: 0
  body-sm:
    fontFamily: "Inter, 'PingFang SC', 'Microsoft YaHei', system-ui, sans-serif"
    fontSize: 13px
    fontWeight: 400
    lineHeight: 1.5
    letterSpacing: 0
  caption:
    fontFamily: "Inter, 'PingFang SC', 'Microsoft YaHei', system-ui, sans-serif"
    fontSize: 12px
    fontWeight: 400
    lineHeight: 1.4
    letterSpacing: 0
  eyebrow:
    fontFamily: "D-DIN, 'Barlow Condensed', 'Arial Narrow', sans-serif"
    fontSize: 12px
    fontWeight: 700
    lineHeight: 1.3
    letterSpacing: 1.0px
  button:
    fontFamily: "Inter, 'PingFang SC', 'Microsoft YaHei', system-ui, sans-serif"
    fontSize: 14px
    fontWeight: 600
    lineHeight: 1.2
    letterSpacing: 0
  button-cap:
    fontFamily: "D-DIN, 'Barlow Condensed', 'Arial Narrow', sans-serif"
    fontSize: 13px
    fontWeight: 700
    lineHeight: 1.0
    letterSpacing: 1.0px
  mono:
    fontFamily: "'JetBrains Mono', 'IBM Plex Mono', ui-monospace, monospace"
    fontSize: 13px
    fontWeight: 500
    lineHeight: 1.5
    letterSpacing: 0
  mono-num:
    fontFamily: "'JetBrains Mono', 'IBM Plex Mono', ui-monospace, monospace"
    fontSize: 28px
    fontWeight: 700
    lineHeight: 1.2
    letterSpacing: -0.5px

rounded:
  xs: 2px
  sm: 4px
  md: 6px
  lg: 8px
  xl: 12px
  pill: 9999px
  full: 9999px

spacing:
  xxs: 4px
  xs: 8px
  sm: 12px
  md: 16px
  lg: 24px
  xl: 32px
  xxl: 48px
  section: 96px

motion:
  duration-instant: 75ms
  duration-fast: 150ms
  duration-base: 200ms
  duration-slow: 250ms
  duration-drawer: 300ms
  duration-count: 800ms
  duration-shimmer: 1400ms
  ease-out: "cubic-bezier(0.16, 1, 0.3, 1)"
  ease-out-back: "cubic-bezier(0.34, 1.56, 0.64, 1)"
  ease-in-out: "cubic-bezier(0.4, 0, 0.2, 1)"
  ease-in: "cubic-bezier(0.6, 0, 0.8, 0.6)"
  ease-linear: "linear"

iconography:
  library-primary: "Tabler Icons (MIT)"
  library-secondary: "Lucide (ISC)"
  grid: 24px
  stroke-width: 2px
  sizes: [12px, 16px, 20px, 24px]
  color: "inherit text hierarchy"

z-index:
  base: 0
  top-bar: 100
  sidebar: 200
  dropdown: 1000
  sticky: 1100
  drawer: 1200
  modal: 1300
  toast: 1400
  tooltip: 1500

overlay-shadow: "0 4px 16px rgba(20, 22, 26, 0.12)"
overlay-shadow-dark: "0 4px 16px rgba(0, 0, 0, 0.5)"

components:
  button-primary:
    backgroundColor: "{colors.primary}"
    textColor: "{colors.on-primary}"
    typography: "{typography.button}"
    rounded: "{rounded.xs}"
    padding: 8px 16px
  button-primary-hover:
    backgroundColor: "{colors.primary-hover}"
    textColor: "{colors.on-primary}"
    typography: "{typography.button}"
    rounded: "{rounded.xs}"
  button-secondary:
    backgroundColor: "{colors.surface-1}"
    textColor: "{colors.ink}"
    typography: "{typography.button}"
    rounded: "{rounded.xs}"
    padding: 8px 16px
  button-secondary-dark:
    backgroundColor: "{colors.surface-dark-2}"
    textColor: "{colors.ink-dark}"
    typography: "{typography.button}"
    rounded: "{rounded.xs}"
    padding: 8px 16px
  button-danger:
    backgroundColor: "{colors.semantic-error}"
    textColor: "#ffffff"
    typography: "{typography.button}"
    rounded: "{rounded.xs}"
    padding: 8px 16px
  button-ghost-on-dark:
    backgroundColor: "{colors.canvas-dark}"
    textColor: "{colors.ink-dark}"
    typography: "{typography.button-cap}"
    rounded: "{rounded.xs}"
    padding: 10px 20px
  status-badge:
    backgroundColor: "{colors.surface-2}"
    textColor: "{colors.ink-muted}"
    typography: "{typography.caption}"
    rounded: "{rounded.pill}"
    padding: 2px 10px
  status-badge-overdue:
    backgroundColor: "{colors.semantic-error-soft}"
    textColor: "{colors.semantic-error}"
    typography: "{typography.caption}"
    rounded: "{rounded.pill}"
    padding: 2px 10px
  data-table:
    backgroundColor: "{colors.surface-1}"
    textColor: "{colors.ink}"
    typography: "{typography.body}"
    rounded: "{rounded.sm}"
    padding: 0px
  data-table-header:
    backgroundColor: "{colors.surface-2}"
    textColor: "{colors.ink-muted}"
    typography: "{typography.eyebrow}"
    rounded: "{rounded.xs}"
    padding: 10px 12px
  form-input:
    backgroundColor: "{colors.surface-1}"
    textColor: "{colors.ink}"
    typography: "{typography.body}"
    rounded: "{rounded.xs}"
    padding: 8px 12px
  form-input-focused:
    backgroundColor: "{colors.surface-1}"
    textColor: "{colors.ink}"
    typography: "{typography.body}"
    rounded: "{rounded.xs}"
    padding: 8px 12px
  sidebar-nav:
    backgroundColor: "{colors.canvas-dark}"
    textColor: "{colors.ink-dark-muted}"
    typography: "{typography.body}"
    rounded: "{rounded.xs}"
    padding: 12px 8px
  sidebar-nav-active:
    backgroundColor: "{colors.surface-dark-2}"
    textColor: "{colors.primary}"
    typography: "{typography.button}"
    rounded: "{rounded.xs}"
    padding: 12px 8px
  stat-card:
    backgroundColor: "{colors.surface-1}"
    textColor: "{colors.ink}"
    typography: "{typography.mono-num}"
    rounded: "{rounded.sm}"
    padding: 20px 24px
  card-corner-marked:
    backgroundColor: "{colors.surface-1}"
    textColor: "{colors.ink}"
    typography: "{typography.body}"
    rounded: "{rounded.sm}"
    padding: 24px
  page-card:
    backgroundColor: "{colors.surface-1}"
    textColor: "{colors.ink}"
    typography: "{typography.body}"
    rounded: "{rounded.sm}"
    padding: 20px 24px
  hero-band:
    backgroundColor: "{colors.canvas-dark}"
    textColor: "{colors.ink-dark}"
    typography: "{typography.display-hero}"
    rounded: "{rounded.xs}"
    padding: 96px 48px
  hazard-divider:
    backgroundColor: "{colors.hazard-black}"
    textColor: "{colors.hazard-yellow}"
    typography: "{typography.caption}"
    rounded: "{rounded.xs}"
    padding: 0px
  top-bar:
    backgroundColor: "{colors.surface-1}"
    textColor: "{colors.ink}"
    typography: "{typography.body-sm}"
    rounded: "{rounded.xs}"
    height: 56px
---

## Overview

本 Mix 风格专为 **ZW-Insight 中维智营**（面向建筑/工程企业的工程项目管理 SaaS）设计，由 `design-md` 库中三套源风格混合而成：

- **SpaceX** 提供**工业工程气质**：D-DIN 字体族（源自德国工程制图标准 DIN 1451，是工程行业的原生字体语言）、大写宽字距标题、克制的黑白基调。
- **Linear** 提供**数据密集型 SaaS 的产品纪律**：surface 阶梯分层、1px hairline 边框、无阴影纵深、产品 UI 本身作为主角。
- **NVIDIA** 提供**工程制图的形态语言**：2px 直角几何、卡片角标、硬朗的结构化网格。

唯一的彩色强调是 **Construction Safety Orange** `{colors.primary}`（#ff6b00）——安全背心、塔吊吊钩与工地警示牌的颜色。它**整体替代了传统 SaaS 蓝**，是本系统最核心的差异化决策。

产品默认运行在**亮色模式**：混凝土白画布 `{colors.canvas}`（#f6f7f5）+ **常驻石墨黑控制室侧边栏** `{colors.canvas-dark}`——如同重型机械的操作台座舱，亮色工作区包裹深色控制台。暗色模式提供完整的石墨阶梯（#101214 → #23262c），供监控大屏与夜间值班场景使用。

**Key Characteristics:**
- 唯一品牌彩色为安全橙 `{colors.primary}`，仅限：主按钮、焦点环、激活导航、关键链接。绝不大面积铺色。
- 双画布系统：亮色混凝土白 `{colors.canvas}` 为日常工作画布；石墨黑 `{colors.canvas-dark}` 为侧边栏、Hero、大屏画布。
- **蓝图角标卡片**（`{card-corner-marked}`）：1px L 形角标取自工程图纸的图框角标，是品牌签名组件。
- **警示条纹**（`{hazard-divider}`）：45° 黑黄斜纹，极克制使用——仅出现在逾期告警、危险操作确认、Hero 顶部 4px 横条。
- 数字一律进入等宽账本：金额、编号、统计数字使用 `{typography.mono}` / `{typography.mono-num}` + `font-variant-numeric: tabular-nums`。
- Display 层全部大写 + 正字距（SpaceX 签名）；UI 正文层使用 Inter 系（Linear 签名）；两套声音分工明确。
- 无投影、无渐变、无 pill 形主按钮——纵深完全由 surface 阶梯与 hairline 承载。

## Colors

> 来源：spacex（黑白画布纪律）、linear.app（surface 阶梯与 hairline）、nvidia（角标与直角形态）；安全橙与语义色为建筑工程域定制扩展。

### Brand & Accent
- **Safety Orange**（`{colors.primary}` — #ff6b00）：品牌唯一彩色。用于主按钮、焦点环、激活导航项、关键链接、图表主线。
- **Primary Hover / Active**（#ff8a2e / #e05f00）：悬停提亮、按下加深。
- **On Primary**（`{colors.on-primary}` — #14161a）：橙色按钮上使用**深色文字**——沿用警示牌「橙底黑字」的行业惯例（而非白字），这是本系统反传统 SaaS 的细节之一。
- **Primary Soft**（#fff0e3 / dark #3a2413）：品牌色的极浅底，仅用于选中行高亮、品牌 badge 底色。

### Surface（Light）
- **Canvas**（`{colors.canvas}` — #f6f7f5）：混凝土白，微暖的中性画布——比纯冷白多一分"水泥"质感。
- **Surface 1**（#ffffff）：卡片、表格、输入框的承托面。
- **Surface 2**（#eef0ed）：表头、禁用底、徽章默认底。
- **Surface 3**（#e4e7e3）：嵌套加深面（下拉面板、工具栏）。
- **Hairline / Hairline Strong**（#d9dcd6 / #b9bdb6）：1px 边框两级——常规分隔与强调边框（输入框聚焦前、表格容器）。

### Surface（Dark）
- **Canvas Dark**（`{colors.canvas-dark}` — #101214）：石墨黑，侧边栏与 Hero 的常驻底色（不用纯黑 #000000）。
- **Surface Dark 1–3**（#16181c / #1c1f24 / #23262c）：三级阶梯，承载暗色模式卡片层级。
- **Hairline Dark**（#2b2e34 / #3d4048）：暗面边框两级。

### Text
- **亮色**：`{colors.ink}`（#14161a）→ `{colors.ink-muted}`（#4a4f57）→ `{colors.ink-subtle}`（#7c828c）→ `{colors.ink-tertiary}`（#a3a8b0），四级。
- **暗色**：`{colors.ink-dark}`（#f2f3f1）→ `{colors.ink-dark-muted}`（#c6c9cc）→ `{colors.ink-dark-subtle}`（#8a8f98），三级。

### Semantic
工程管理是强状态驱动的领域（审批、逾期、预警），语义色必须完整：
- **Success**（#1f9d55 / soft #e0f3e8）：已通过、已完成、收款到账。
- **Warning**（#f7b500 / soft #fdf3d7）：待处理、临期提醒。与品牌橙**色相分离**（黄橙分明），不与品牌色混用。
- **Error**（#d92d20 / soft #fbe5e2）：驳回、逾期、超预算——逾期提醒是本系统核心业务，Error 红 + 警示条纹组合是逾期告警的专属语言。
- **Info**（#2b6cb0 / soft #e2ecf7）：中性流程状态（流转中、已归档）。

### Hazard
- **Hazard Black / Yellow**（#14161a / #ffc400）：仅用于 `{hazard-divider}` 警示条纹的 45° 斜纹对，**不是**通用配色。

## Typography

### Font Family

- **Display 层**：**D-DIN**（Bold）——SpaceX 的签名，其血统即德国 DIN 1451 工程制图字体，与建筑工程行业天然同源。免费替代：**Barlow Condensed**（Google Fonts，开源）。回退链 `Arial Narrow → sans-serif` 优先保证宽度压缩感。仅用于 Hero、页面大标题、Eyebrow、大写按钮。
- **UI 层**：**Inter**——Linear 的推荐开源替代，从 20 页标题到 12 元信息一条声音贯穿；中文回退 `PingFang SC / Microsoft YaHei`。
- **数字层**：**JetBrains Mono** / IBM Plex Mono——项目编号、合同编号、金额、统计数字专属（Linear 的 mono 纪律），全局启用 `font-variant-numeric: tabular-nums`。

### Hierarchy

| Token | Size | Weight | Line Height | Letter Spacing | Use |
|---|---|---|---|---|---|
| `{typography.display-hero}` | 56px | 700 | 1.0 | +1.2px | 登录页 / Hero 大标题（大写） |
| `{typography.display-lg}` | 40px | 700 | 1.05 | +0.8px | 数据大屏区块标题（大写） |
| `{typography.display-md}` | 28px | 700 | 1.1 | +0.4px | 模块首页标题（大写） |
| `{typography.headline}` | 20px | 600 | 1.3 | -0.2px | 页面标题（常规大小写） |
| `{typography.card-title}` | 16px | 600 | 1.35 | -0.1px | 卡片标题 |
| `{typography.body-lg}` | 16px | 400 | 1.6 | 0 | 引导文案、说明段 |
| `{typography.body}` | 14px | 400 | 1.5 | 0 | 默认正文、表格内容 |
| `{typography.body-sm}` | 13px | 400 | 1.5 | 0 | 顶栏、辅助说明 |
| `{typography.caption}` | 12px | 400 | 1.4 | 0 | 徽章、表尾、时间戳 |
| `{typography.eyebrow}` | 12px | 700 | 1.3 | +1.0px | 分类眉题（大写）、表头 |
| `{typography.button}` | 14px | 600 | 1.2 | 0 | 常规按钮 |
| `{typography.button-cap}` | 13px | 700 | 1.0 | +1.0px | 大写按钮（仅暗面 / Hero） |
| `{typography.mono}` | 13px | 500 | 1.5 | 0 | 编号、单元格数字 |
| `{typography.mono-num}` | 28px | 700 | 1.2 | -0.5px | KPI 统计大字 |

### Principles
- **Display 一律大写 + 正字距**（+0.4 ~ +1.2px）——SpaceX 签名；中文无大小写概念，中文标题回退到中文工程铭牌方案（见下）。
- **UI 层不混用 Display 字体**——正文、表格、表单全部走 Inter 系，两套声音严格分区。
- **数字一律等宽**——金额与编号用 mono + tabular-nums，对齐即工程精度。

### 中文 Display 方案（工程铭牌）

D-DIN 无中文字形，大写签名在中文场景必须有自己的替代方案，否则品牌最强特征会在最常用场景里失效。规则：
- **字形**：中文 Display 使用系统重黑（PingFang SC / Microsoft YaHei 的 Bold–Heavy 档），**不加载中文 webfont**（思源黑体 Heavy 单字重体积超 2MB，首屏不可接受）。
- **铭牌化处理**：中文标题采用重字重 + 宽字距（+0.02em ~ +0.05em）——工程铭牌与图纸图签的传统是字拉开而非压缩，与英文 DIN 的正字距逻辑一致。
- **中英双语铭牌（签名组合）**：中文标题（重黑宽字距）+ 下方一行英文/编号的 `{typography.eyebrow}` 大写 mono 副线（如 `PROJECT LEDGER`、`NO. BJ-2026-001`）——这是本系统中文场景下的品牌签名时刻，替代了纯英文大写 DIN 的位置。
- **行高**：中文 Display 行高不低于 1.2（中文方块字需要比拉丁字更多垂直呼吸）。

### Note on Font Substitutes
D-DIN 有免费分发版本；若不可用，**Barlow Condensed**（700）是最接近的开源替代。Inter 使用 400/600/700 三档。等宽首选 **JetBrains Mono**（开源）；数字场景禁用衬线与等线外的任何字体。拉丁/数字 webfont（Barlow Condensed + JetBrains Mono 子集）总体积可控在 200KB 内，可自托管；中文字重完全交给系统字体。

## Layout

### Spacing System
- **Base unit**: 4px。
- **Tokens**: `{spacing.xxs}` 4px · `{spacing.xs}` 8px · `{spacing.sm}` 12px · `{spacing.md}` 16px · `{spacing.lg}` 24px · `{spacing.xl}` 32px · `{spacing.xxl}` 48px · `{spacing.section}` 96px。
- 卡片内边距：常规 `{spacing.lg}` 24px；紧凑页内卡片 `{spacing.md}`+`{spacing.lg}`（20px 24px）；统计卡片 20px 24px。
- 按钮内边距：8px 16px；大写暗面按钮 10px 20px。

### Grid & Container
- 应用壳（App Shell）：石墨侧边栏固定 220px（折叠 64px）+ 顶栏 56px + 内容区；内容区最大宽度 1440px，页内留白 `{spacing.lg}`。
- 卡片网格：统计卡 4-up（≥1280px）→ 2-up（≥768px）→ 1-up；功能卡 3-up → 2-up → 1-up。
- Hero / 登录页：全幅石墨画布 + 1200px 居中阅读列（SpaceX 的全幅带 + 居中型列组合）。

### Whitespace Philosophy
亮色模式下留白是画布本身（混凝土白）；区块分隔靠 surface 阶梯抬升与 1px hairline，而非大段空白。暗色侧边栏内用 `{spacing.xs}` 紧凑节奏，与内容区的 `{spacing.lg}` 形成"控制台密 / 工作区疏"的节奏对比。

## Elevation & Depth

| Level | Treatment | Use |
|---|---|---|
| 0 | 无阴影、无边框 | 正文、Hero 文字 |
| 1 | surface-1 底 + 1px `{colors.hairline}` | 默认卡片、表格容器、输入框 |
| 2 | surface-2/3 底 或 1px `{colors.hairline-strong}` | 表头、悬停卡片、工具栏 |
| 3 | 暗面阶梯（dark surface 1→3） | 侧边栏内弹层、下拉菜单 |
| 4 | 2px `{colors.primary}` 焦点环（25% opacity 外扩） | 聚焦输入框、聚焦按钮 |

纵深由 surface 阶梯 + hairline 承载，**静态组件禁用 box-shadow 做层级**（Linear 纪律）。

### 浮层例外（唯一合法阴影）

悬浮于页面内容之上的临时层（dropdown / popover / tooltip / drawer / modal / toast）只靠 hairline 无法与下方内容分离，因此本系统定义**唯一一级浮层阴影**，仅限这些临时层使用：
- 亮色：`{overlay-shadow}`（`0 4px 16px rgba(20,22,26,0.12)`）
- 暗色：`{overlay-shadow-dark}`（`0 4px 16px rgba(0,0,0,0.5)`）
- 浮层永远叠加 1px hairline 边框 + 阴影，二者成对出现；遮罩统一 `rgba(20,22,26,0.45)`。
- 静态卡片、表格、按钮**永远禁止**使用此阴影——阴影是「悬浮」的专属语义，不是「重要」的语义。

### Decorative Depth
- **蓝图角标**：`{card-corner-marked}` 四角 1px L 形角标（`{colors.hairline-strong}`，激活时为 `{colors.primary}`），工程图纸图框的隐喻——本系统唯一允许的装饰性纵深。
- **警示条纹**：45° 黑黄斜纹（4px 高横条或 8px 竖条），仅逾期告警与危险确认场景。
- 无插画、无渐变网格、无光斑。

## Shapes

### Border Radius Scale

| Token | Value | Use |
|---|---|---|
| `{rounded.xs}` | 2px | 所有按钮、输入框、表格容器（NVIDIA 直角纪律） |
| `{rounded.sm}` | 4px | 卡片、统计卡 |
| `{rounded.md}` | 6px | 弹层、大面板 |
| `{rounded.lg}` | 8px | Hero 内嵌媒体框（罕见） |
| `{rounded.pill}` | 9999px | **仅状态徽章**——徽章是唯一允许胶囊形的元素 |
| `{rounded.full}` | 9999px | 头像 |

直角（2px）是形态主声部；胶囊形只属于徽章。主按钮**禁止** pill 圆角。

### Corner-Mark Geometry
`{card-corner-marked}` 的角标：每个角 12px 长的 L 形线，距卡片边缘 8px，线宽 1px。四角齐全表示"正式文档/重点数据"，仅左上角表示"草稿"——角标本身携带状态语义（工程图纸图签的隐喻）。

## Components

### Buttons

**`button-primary`** — 安全橙主按钮。
- 背景 `{colors.primary}`，文字 `{colors.on-primary}`（深字橙底），类型 `{typography.button}`，padding 8px 16px，圆角 `{rounded.xs}`。悬停 `{colors.primary-hover}`，按下 `{colors.primary-active}`。

**`button-secondary`** — 白底描边次按钮。
- 背景 `{colors.surface-1}`，文字 `{colors.ink}`，1px `{colors.hairline}` 边框；悬停边框升级为 `{colors.hairline-strong}`，背景升 `{colors.surface-2}`。

**`button-secondary-dark`** — 暗面次按钮（侧边栏 / 大屏）。
- 背景 `{colors.surface-dark-2}`，文字 `{colors.ink-dark}`，1px `{colors.hairline-dark}` 边框。

**`button-danger`** — 危险操作（终止流程、作废合同）。
- 背景 `{colors.semantic-error}`，白字；确认弹层标题条叠加 `{hazard-divider}` 4px 条纹。

**`button-ghost-on-dark`** — 暗面幽灵按钮（SpaceX 签名变体）。
- 透明底 + 1px `{colors.ink-dark}` 边框，文字 `{colors.ink-dark}`，类型 `{typography.button-cap}`（大写），padding 10px 20px，圆角 `{rounded.xs}`。仅用于 Hero 与登录页。

### Status & Badges

**`status-badge`** — 状态胶囊徽章（唯一胶囊元素）。
- 默认：背景 `{colors.surface-2}`，文字 `{colors.ink-muted}`，类型 `{typography.caption}`，padding 2px 10px。各状态替换为对应语义色对（如 已通过 = success-soft 底 + success 字）。

**`status-badge-overdue`** — 逾期专属徽章。
- `{colors.semantic-error-soft}` 底 + `{colors.semantic-error}` 字，可前置 8px `{hazard-divider}` 竖纹——逾期是本系统最重要的告警语义，值得专属组件。

### Data Table

**`data-table`** — 业务表格（本系统最高频组件）。
- 容器 `{colors.surface-1}` 底 + 1px `{colors.hairline}` 边框，圆角 `{rounded.sm}`；行高 48px（默认）/ 40px（紧凑）。

**`data-table-header`** — 表头。
- `{colors.surface-2}` 底，文字 `{colors.ink-muted}`，类型 `{typography.eyebrow}`（大写眉题风格，但中文场景用 12px/700 不加字距），列分隔用 1px `{colors.hairline}`。
- 金额列右对齐 + `{typography.mono}`；编号列 `{typography.mono}`；状态列渲染 `{status-badge}`。
- 悬停行底 `{colors.surface-2}`（50%）；选中行底 `{colors.primary-soft}` + 左侧 2px `{colors.primary}` 竖条。

### Forms

**`form-input` / `form-input-focused`** — 表单输入。
- 背景 `{colors.surface-1}`，文字 `{colors.ink}`，类型 `{typography.body}`，padding 8px 12px，圆角 `{rounded.xs}`，1px `{colors.hairline-strong}` 边框。
- 聚焦：边框换 `{colors.primary}` + 2px 外扩焦点环（25% opacity）；错误：边框 `{colors.semantic-error}` + 下方 12px 错误文案。
- 金额输入框内文字使用 `{typography.mono}` 右对齐。

### Navigation

**`sidebar-nav`** — 石墨控制室侧边栏（**亮色模式下也保持深色**）。
- 背景 `{colors.canvas-dark}`，文字 `{colors.ink-dark-muted}`，类型 `{typography.body}`；顶部品牌区为 logo + `{typography.eyebrow}` 大写产品名。
- 分组标题用 `{typography.eyebrow}` 大写眉题（`{colors.ink-dark-subtle}`）。

**`sidebar-nav-active`** — 激活项。
- `{colors.surface-dark-2}` 底 + `{colors.primary}` 文字 + **左侧 2px `{colors.primary}` 竖条**（角标语义的导航版）。

**`top-bar`** — 顶栏。
- `{colors.surface-1}` 底，高 56px，底部 1px `{colors.hairline}`；面包屑 `{typography.body-sm}`，右侧工具区含租户徽标与用户头像（`{rounded.full}` 32px）。

### Cards & Surfaces

**`page-card`** — 页内常规卡片（表单分区、详情区块）。
- `{colors.surface-1}` 底，1px `{colors.hairline}`，圆角 `{rounded.sm}`，padding 20px 24px；标题行 `{typography.card-title}` + 左侧 3px `{colors.primary}` 竖标（延续现有 `.section-title` 语义，但从渐变换为纯色直角）。

**`stat-card`** — KPI 统计卡。
- `{colors.surface-1}` 底 + 角标（`{card-corner-marked}` 变体），数字用 `{typography.mono-num}`，标签用 `{typography.eyebrow}` 大写；环比箭头用语义色。金额卡数字颜色默认 `{colors.ink}`，**仅"预警/逾期"统计**数字用 `{colors.semantic-error}`。

**`card-corner-marked`** — 签名卡片：四角 1px L 形角标（见 Shapes 章）。用于首页核心数据卡、详情首屏卡、空状态容器。

### Marketing / Entry Surfaces

**`hero-band`** — 登录页 / 门户 Hero（SpaceX 全幅带变体）。
- 全幅 `{colors.canvas-dark}`，标题 `{typography.display-hero}` 大写，副文案 `{typography.body-lg}`（`{colors.ink-dark-muted}`），单个 `{button-ghost-on-dark}` 或 `{button-primary}`。顶部压一条 4px `{hazard-divider}`——品牌识别线。

**`hazard-divider`** — 警示条纹。
- 45° 黑黄斜纹：`repeating-linear-gradient(45deg, #14161a 0 8px, #ffc400 8px 16px)`。仅三处合法：Hero 顶部 4px、逾期告警条、危险操作确认标题条。

## Do's and Don'ts

### Do
- 安全橙只出现在：主按钮、焦点环、激活导航、选中行竖条、图表主线、品牌徽标。
- 所有数字场景使用等宽字体 + `tabular-nums`（金额、编号、统计）。
- 卡片层级用 surface 阶梯 + 1px hairline；重点卡片加蓝图角标。
- 状态一律走 `{status-badge}` 胶囊 + 语义色对；逾期叠加警示条纹语言。
- 侧边栏在亮色模式下保持石墨黑——控制室意象是本壳层的灵魂。
- Display 层大写 + 正字距；中文标题用工程铭牌方案（重黑 + 宽字距 + 中英双语副线）。
- 动效默认 150ms · ease-out，只在用户触发的事件上做；浮层专用 `{overlay-shadow}`，静态组件永远无阴影。
- 加载态统一旋转方块；等待 > 1s 必骨架屏；空状态给方向不给道歉。
- 焦点环 :focus-visible 永不移除；状态色必配文字标签双通道（色盲安全）。

### Don't
- 不要把安全橙用作区块背景或卡片填充——它是稀缺品。
- 不要引入第二种品牌彩色（紫、绿、粉）做装饰；彩色只属于语义系统与图表。
- 不要用 box-shadow 做层级、不要用渐变、不要用 pill 形主按钮。
- 不要在正文/表格/表单里使用 D-DIN 系 Display 字体。
- 不要在非告警场景使用黑黄警示条纹——用滥即失效。
- 不要用纯黑 #000000 做画布（用 `{colors.canvas-dark}`）；不要用纯冷白 #ffffff 做页面画布（用 `{colors.canvas}`）。
- 不要给页面挂载入场动画、不要视差、不要卡片网格全量 stagger；产品内动效不超过 300ms（账本滚动除外）。
- 不要加载中文 webfont（思源黑体单字重 2MB+）；不要在橙底上放白字（对比度仅 2.9:1）。
- 不要用警示黄 `{colors.hazard-yellow}` 做前景文字；不要给业务工程照片加任何滤镜。
- 不要用彩色序列区分普通图表数据（灰阶 + 单一橙色高亮）；不要用圆形节点做审批步骤条。

## Responsive Behavior

### Breakpoints

| Name | Width | Key Changes |
|---|---|---|
| Wide | ≥ 1440px | 内容区 1440px 封顶，统计卡 4-up |
| Desktop | 1280–1439px | 默认桌面布局 |
| Laptop | 961–1279px | 统计卡 4-up → 2-up；侧边栏可折叠 |
| Tablet | 768–960px | 侧边栏抽屉化；表格横向滚动 |
| Mobile | < 768px | 顶栏汉堡化；卡片单列；表格转卡片列表 |

### Touch Targets
- 按钮与表单控件 ≥ 44px 触达高度（移动端表格行转卡片后行内操作同样遵守）。
- 侧边栏抽屉态手势边缘 ≥ 16px 热区。

### Collapsing Strategy
- 统计卡 4-up → 2-up → 1-up；功能卡 3-up → 2-up → 1-up。
- 表格 < 768px 转卡片列表：编号 + 状态徽章为卡片头（`{typography.mono}` + `{status-badge}`），金额用 `{typography.mono-num}` 20px 变体。
- Display 阶梯降级：56 → 40 → 28 → 20（进入 headline 档后不再大写强制）。

### Image Behavior
- Hero 与门户使用 `srcset` 分档裁切；工程实景图（工地、结构、图纸）作摄影语言，替代插画。

## Motion（动效系统）

> 双源交叉验证：Atlassian Motion 体系（语义 token、时长分区、频率表达规则）与 Cloudflare frontend-design-saas 规范（时长阶梯、easing 取值、产品 UI ≤300ms 红线）结论一致：快、ease-out 主导、只响应用户触发。

### 原则（承重墙）
- **动效的职责是确认动作发生了，不是娱乐**。不阻断流程、不制造等待。
- **只在用户触发的事件上做动效**：禁止页面挂载时的入场动画、禁止装饰性视差、禁止所有网格都 stagger。
- **出场快于入场**：退场时长 = 入场时长 × 70%，关闭的东西永远不阻断下一步操作。
- **高频低频分级**：每天触发几十次的交互（hover、按钮、列表行）≤ 150ms；每会话一次的时刻（首屏、里程碑、引导）才允许更多表达。
- **单一焦点**：同一时刻只允许一个主导动画，其余辅助；禁止多动画争夺注意力。
- **无障碍**：尊重 `prefers-reduced-motion`——降级为瞬时切换（保留 opacity 快切）；禁止闪烁、快速振荡、大面积扫动。
- **禁用 CSS `ease` / `ease-in` 默认曲线**——在数据密集界面上感觉迟钟。

### Duration Tokens

| Token | 值 | 用途 |
|---|---|---|
| `{motion.duration-instant}` | 75ms | 颜色变化、opacity 翻转（按钮按下色变） |
| `{motion.duration-fast}` | 150ms | **默认**——大多数过渡（hover 底、行高亮、badge 切换） |
| `{motion.duration-base}` | 200ms | dropdown / popover / tooltip 入场 |
| `{motion.duration-slow}` | 250ms | modal 入场（配 scale 0.98→1） |
| `{motion.duration-drawer}` | 300ms | 抽屉/侧边栏滑入（产品 UI 上限，更长即错） |
| `{motion.duration-count}` | 800ms | KPI 数字滚动（低频品牌时刻，见下） |
| `{motion.duration-shimmer}` | 1400ms | 骨架屏呼吸周期 |

### Easing Tokens

| Token | 曲线 | 用途 |
|---|---|---|
| `{motion.ease-out}` | `cubic-bezier(0.16, 1, 0.3, 1)` | **默认**（expo-out，干脆）——绝大多数状态过渡与入场 |
| `{motion.ease-out-back}` | `cubic-bezier(0.34, 1.56, 0.64, 1)` | 稀缺——仅 toast 入场，微小回弹 |
| `{motion.ease-in-out}` | `cubic-bezier(0.4, 0, 0.2, 1)` | 按下/激活、位置重排 |
| `{motion.ease-in}` | `cubic-bezier(0.6, 0, 0.8, 0.6)` | 仅退场（元素加速离开） |
| `{motion.ease-linear}` | `linear` | 仅 spinner 与不确定进度 |

### 语义组合（开箱即用）

| 场景 | 组合 |
|---|---|
| hover / 行高亮 | 150ms · ease-out · background-color |
| dropdown / popover 入场 | 200ms · ease-out · opacity + translateY(-4px→0) |
| dropdown / popover 退场 | 140ms · ease-in · opacity |
| modal 入场 | 250ms · ease-out · opacity + scale(0.98→1)；遮罩 150ms 淡入 |
| modal 退场 | 175ms · ease-in · opacity + scale(1→0.98) |
| drawer 入/退 | 300ms / 210ms · ease-out / ease-in · translateX |
| toast 入场 | 200ms · ease-out-back · translateY(-8px→0) + opacity |
| 页面路由切换 | 150ms · ease-out · opacity + translateY(8px→0)（延续现有 `.fade-slide`，但统一为本 token） |
| 表格行增删 | 150ms · ease-out · 行高折叠 + opacity |

### 签名动效（品牌时刻，低频使用）
- **账本滚动（Ledger Count-Up）**：KPI 与金额统计数字用 `{typography.mono-num}` 从 0 滚动到目标值，800ms · ease-out，`tabular-nums` 保证数字滚动时不抖动。仅限工作台首屏与大屏加载，不用于表格内数字。
- **压合（Press-Fit）**：按钮按下时 `translateY(1px)` + 边框加深，75ms——机械压入感，不用 scale 弹跳。
- **警示脉冲（Hazard Pulse）**：逾期徽章的 8px 警示竖纹做两段式 opacity 呼吸（100%↔40%，1400ms），仅首页逾期汇总区使用；`prefers-reduced-motion` 下静止。
- **角标描画（Corner-Mark Draw）**：签名卡片悬停时 1px L 角标以 `stroke-dashoffset` 描入，200ms · ease-out——仅限 `{card-corner-marked}` 悬停，不做入场自动播放。
- **进度条行走**：确定进度条用 `{colors.primary}` 填充 + 右侧 2px `{colors.hazard-yellow}` 立边（工地浇筑线隐喻）；不确定进度用 `ease-linear` 往复扫描。
- **警示条纹**：静态斜纹为主；仅危险确认弹层标题条允许 2s 周期的缓慢斜向位移（`background-position` 动画），克制使用。
- **数字翻牌（大屏）**：大屏数字变化时用纵向滚动替换（旧数字上滑出、新数字下滑入），200ms · ease-out。

### 动效禁用清单（Don't）
- 不要给页面挂载加入场动画；不要给每个卡片网格加 stagger。
- 不要超过 300ms 的产品内动效（品牌时刻除外：账本滚动 800ms 是唯一例外）。
- 不要用动效替代加载反馈——超过 300ms 的等待必须有骨架屏或 spinner。
- 不要循环动画常驻（警示脉冲与大屏刷新除外）。

## Iconography（图标系统）

> 三角交叉验证：Reddit r/UXDesign 从业者口碑（Lucide “视觉上极其一致”）、GitHub 事实数据（Lucide 24.2k⭐ / Tabler 21.5k⭐，均为 2026-08 活跃维护）、规格对比（同为 24px 网格 + 2px 线宽）。

### 选型结论：Tabler 为主，Lucide 为辅
- **主库：Tabler Icons**（MIT，5000+ 图标）——几何感更强、转角更硬，与 2px 直角纪律的工业气质最契合；Vue 生态有官方支持且可 tree-shaking。
- **辅库：Lucide**（ISC，1500+）——仅当 Tabler 缺失某个语义时才借用；**同一页面内禁止两库混用同类图标**，避免笔性差异被察觉。
- Element Plus 自带图标仅作过渡期兼容，新页面一律用 Tabler。

### 规格
| 项 | 规则 |
|---|---|
| 网格 | 24×24 viewBox，内容区 20×20，四周 2px 安全边距 |
| 线宽 | 默认 2px；12px 小尺寸降为 1.5px 防糊 |
| 尺寸阶梯 | 12 / 16 / 20 / 24 四档，禁用中间值；与文字搭配时：行内 16、导航 20、页面空状态 48（24 网格 2× 渲染） |
| 颜色 | 图标色 = 所在文本层级色（随 `ink` 阶梯），激活态可用 `{colors.primary}`；图标不单独引入文本之外的颜色 |
| 像素对齐 | 自定义图标必须对齐像素网格，坐标取整，避免模糊边缘 |
| 端帽 | 默认沿用库原笔性（圆帽）；自定义工程专属图标用方帽 + miter 转角，呼应直角纪律 |

### 工程域自定义图标（需自绘，库中无对应语义）
塔吊、蓝图角标、安全帽、警示条纹块、图纸卷、经纬仪、结算账本——以 24px 网格 + 2px 线宽自绘，方帽笔性，作为本系统的图标签名资产；命名 `zw-{name}`。

### 图标使用规则（Don't）
- 不要用图标颜色传递状态——状态一律走 `{status-badge}`；图标前缀色点属于徽章而非图标。
- 不要给图标加描边、投影、背景圆角块（导航图标例外：无底）。
- 不要随意缩放非阶梯尺寸；不要旋转库图标角度（除了 loading 旋转）。
- 表格操作列图标必须配 `aria-label` 或 tooltip，纯图标按钮最小触达 32×32（内嵌图标 16）。

## State Matrix（状态矩阵）

所有交互组件的状态必须完整定义，不允许实现时临时发明。背景/边框/文字全部引用 color token：

| 组件 | default | hover | active/pressed | focus | disabled | loading | selected |
|---|---|---|---|---|---|---|---|
| button-primary | primary 底 + on-primary 字 | primary-hover 底 | primary-active 底 + translateY(1px) | 2px primary 焦点环 | surface-2 底 + ink-tertiary 字，去边框 | 原底 + 白色旋转方块，文字保留 | — |
| button-secondary | surface-1 底 + hairline 边 | surface-2 底 + hairline-strong 边 | surface-3 底 | 2px primary 焦点环 | 同上 | 同上 | — |
| button-danger | error 底 + 白字 | error 提亮 8% | error 加深 8% | 2px error 焦点环 | 同上 | 同上 | — |
| form-input | surface-1 + hairline-strong 边 | hairline-strong 边 + surface-2 底 | — | primary 边 + 25% 外扩环 | surface-2 底 + ink-tertiary 字 | 右侧小方块旋转 | — |
| form-input-error | — | — | — | error 边 + error 环 | — | — | — |
| table-row | surface-1 | surface-2（50%） | surface-2 | — | 文字 ink-tertiary，操作列禁用 | 行内操作变方块旋转 | primary-soft 底 + 左 2px primary 竖条 |
| nav-item | ink-dark-muted 字 | surface-dark-1 底 + ink-dark 字 | 同 hover | 2px primary 内描边 | ink-dark-subtle 40% | 右侧小方块旋转 | surface-dark-2 底 + primary 字 + 左 2px primary 竖条 |
| status-badge | 对应语义色对 | 不变（徽章不响应悬停） | — | — | 降为 surface-2 + ink-subtle | — | — |
| link | primary 字 + 无下划线 | 下划线出现 | primary-active | 2px 焦点环 | ink-tertiary | — | — |
| checkbox / switch | hairline-strong 边 | primary 边 | — | 焦点环 | surface-2 底 | — | primary 填充 + 白勾（暗面）/ primary 底 + on-primary 勾（亮面） |
| stat-card | surface-1 + 角标 | 角标转 primary（200ms 描画） | — | — | 全部降为灰阶 | 数字位换骨架块 | — |
| chart | 正常渲染 | 系列提亮，其余降到 30% | — | — | 整图降为灰阶 + 蒙层提示 | 骨架块 | 图例选中项高亮 |

### 状态通用规则（Do/Don't）
- 状态变化统一走 `{motion.duration-fast}` 150ms 过渡；focus 焦点环例外为瞬时（无障碍要求）。
- **disabled 不是隐形**：禁用元素保留可见（灰阶化），禁止 `opacity: 0` 或直接移除；禁用原因优先用 tooltip 说明。
- loading 统一形态：**旋转方块**（2px 边框、三面 `{colors.hairline-strong}` + 一面 `{colors.primary}`，顺时针 `{motion.ease-linear}`，800ms/圈）——不用圆形 spinner，方形是本系统的签名加载符；超过 300ms 的请求必须有骨架屏。
- 同一页面内同类组件的状态表现必须一致；禁止页面各自发明 hover 效果。

## Iteration Guide

1. 一次只改一个组件，用 `components:` token 名引用（`{button-primary}`、`{stat-card}`）。
2. 新增区块先决定它落在哪一级 surface，再决定边框——层级优先于装饰。
3. 默认正文 `{typography.body}` 14px/400；数字场景切 `{typography.mono}`。
4. 编辑后运行 `npx @google/design.md lint DESIGN.md` 校验 frontmatter。
5. 新变体作为独立组件条目追加，不覆盖原条目。
6. 安全橙稀缺性原则是承重墙——每次新增橙色用法前先问：能不能用 hairline / 角标 / 语义色替代？
7. 与现有 `--zw-*` Design Token 体系的映射：`--zw-brand` → `{colors.primary}`，`--zw-bg-page` → `{colors.canvas}`，`--zw-brand-gradient` 系列**废弃**（本系统无渐变），新增 `--zw-hazard` 与角标专用变量。

## Data Visualization（图表受限色板）

> 交叉验证：Datawrapper 与 FusionCharts 的图表配色原则一致——多序列图表以中性灰阶为主序列，仅对需要回答业务问题的那条序列使用高亮色；同一实体跨图颜色必须一致。

### 色板策略（承重墙）
- **默认多序列 = 中性灰阶**：`#8a8f98 → #b9bdb6 → #d9dcd6`（亮色画布上均 ≥ 3:1 图形元素对比），深色画布镜像使用 `{colors.ink-dark-subtle}` → `{colors.hairline-dark-strong}`。
- **安全橙 `{colors.primary}` 保留给关键序列**：成本实际值、产值主线、预算执行率等“用户盯着看的那条线”。一屏内最多 1 条橙色序列。
- **语义色仅状态类图表**：审批状态分布、逾期趋势、超预算预警——这些图本身就是状态语义，直接用 `{colors.semantic-*}`。
- **同一实体跨图颜色一致**：一个项目在成本、产值、收款三张图里必须同色（灰阶档位固定给同一实体，不因图表切换而变）。
- 禁止第三品牌色做序列区分；序列不够用就拆图或用线型（实线/2px 虚线）区分。

### 图表组件规格（ECharts 主题建议值）
- 轴线 `{colors.hairline}` 1px；刻度标签 `{typography.caption}` + `{colors.ink-subtle}`；轴数值一律 `{typography.mono}` + `tabular-nums`。
- 网格线 `{colors.hairline}` 1px 虚线；零线升级 `{colors.hairline-strong}` 实线。
- 图例 `{typography.caption}` + `{colors.ink-muted}`，方形图例块（10×10，`{rounded.xs}`）——不用圆点，直角纪律贯穿到图例。
- Tooltip：`{colors.canvas-dark}` 底 + `{colors.ink-dark}` 字 + 1px `{colors.hairline-dark}` + `{overlay-shadow-dark}`，数值行 `{typography.mono}`。
- 柱状图柱顶直角（不圆角）；线图 2px，数据点方形 6×6；饼图扇区间隔 1px 画布色。
- 预算/目标参考线：2px `{colors.ink}` 虚线 + 右端 `{typography.eyebrow}` 大写标签。
- 数据标签默认关闭，靠 hover tooltip；必须常驻的标签用 `{typography.caption}` + `{colors.ink-muted}`。

### Don't
- 不要给图表加渐变填充、面积图不要半透明彩色填充（如需面积图，灰色 8% opacity）。
- 不要用红色序列表达普通数据——红属于语义系统（超支/逾期）。
- 不要 3D 图表、不要环形仪表盘装饰；数字自己会说话。
- 大屏图表遵循同一套规格，仅底色切换暗面镜像色板。

## Feedback & Overlays（反馈与浮层）

> 层级引用 frontmatter `z-index` tokens（dropdown 1000 → tooltip 1500）；所有临时浮层 = `{overlay-shadow}` + 1px hairline（见 Elevation 浮层例外），出入场动效引用 Motion 章语义组合表。

### Message 轻提示
- 顶部居中，{motion.duration-base} 200ms · ease-out 下滑入场；`{colors.surface-1}` 底 + 1px hairline + `{overlay-shadow}`；左侧 3px 语义色竖条区分 success/warning/error，文字 `{typography.body-sm}` + `{colors.ink}`。默认 3s 自动关闭。
- 与业务强相关的操作反馈优先用页面内结果态（行变色、状态徽章切换），Message 仅用于跨页面的轻确认。

### Toast 通知（右下角队列）
- `{colors.canvas-dark}` 底 + `{colors.ink-dark}` 字（控制室广播的隐喻），1px `{colors.hairline-dark}` + `{overlay-shadow-dark}`；`{motion.ease-out-back}` 入场（Motion 章唯一回弹许可）；最多同屏 3 条，新入挤出旧条；关闭按钮 32×32 触达。
- 逾期类 toast 顶部压 2px `{hazard-divider}` 细纹。

### MessageBox 确认弹层（危险操作专属形态）
- 常规确认：标题 `{typography.card-title}` + 正文 `{typography.body}` + 按钮组 `{button-secondary}` + `{button-primary}`。
- 危险确认（终止流程、作废合同、批量驳回）：标题条叠加 4px `{hazard-divider}`；主按钮换 `{button-danger}`；必须输入确认（合同编号或“确认终止”四字）而非一键点按——破坏性操作永远不给捷径。
- 遮罩 `rgba(20,22,26,0.45)`；modal 入场 250ms · scale 0.98→1（Motion 章）。

### Steps 审批步骤条（本系统高频业务组件）
- 节点：方形 24×24（`{rounded.xs}`）——**圆形节点禁用**；连接线 2px `{colors.hairline-strong}`，已通过段变 `{colors.ink}`。
- 待办节点：`{colors.primary}` 2px 描边 + 白底；当前节点：`{colors.primary}` 填充 + `{colors.on-primary}` 数字；已通过：`{colors.ink}` 填充 + 白勾；驳回：`{colors.semantic-error}` 填充 + 白叉 + 节点下方驳回意见（`{typography.caption}`）。
- 节点标签 `{typography.caption}` + `{colors.ink-muted}`，当前节点标签升 `{colors.ink}` 600。
- 驳回后的重新提交：原驳回节点保留（历史可追溯），新流程从下一节点续接——步骤条是审计记录，不是装饰。

### Timeline 时间轴（审批历史、项目动态）
- 左侧 1px `{colors.hairline-strong}` 竖线 + 方形节点 8×8；时间戳 `{typography.mono}` + `{colors.ink-subtle}`，事件标题 `{typography.body-sm}` 600，操作人/意见 `{typography.caption}` + `{colors.ink-muted}`。
- 最新事件置顶；驳回事件节点换 `{colors.semantic-error}` + 8px `{hazard-divider}` 竖纹。
- 时间轴节点禁止入场动画（高频信息流，遵守 Motion 高频纪律）。

### Progress 进度条（浇筑线签名）
- 确定进度：轨道 `{colors.surface-3}` 高 6px，填充 `{colors.primary}` + 右端 2px `{colors.hazard-yellow}` 立边（浇筑线隐喻，Motion 章签名动效）；百分比标签 `{typography.mono}`。
- 预算执行率超 100%：填充切换 `{colors.semantic-error}`，立边保留——进度条自带预警语义。
- 不确定进度：`{motion.ease-linear}` 2px 橙色扫描块往复，轨道同确定进度。
- 步骤类进度不用进度条，用 Steps。

### Tabs 页签（详情抽屉高频）
- 激活页签：2px `{colors.primary}` 下划线 + `{colors.ink}` 600；未激活 `{colors.ink-muted}`，悬停 `{colors.ink}`。下划线切换 150ms · ease-out 位移，不淡入淡出。
- 页签数 > 6 收进更多下拉；禁止双行页签。
- 页签切换不触发整块骨架屏闪烁，仅切换区内容 150ms opacity 过渡。

### Pagination 分页
- 页码按钮 32×32 方形（`{rounded.xs}`），1px `{colors.hairline}`；当前页 `{colors.canvas-dark}` 底 + `{colors.ink-dark}` 字（深块即当前，不用橙色）；悬停 `{colors.surface-2}` 底。
- 总数与页大小文案 `{typography.caption}` + `{colors.ink-subtle}`；数字 `{typography.mono}`。
- 大数据量表格（>10 万行）默认虚拟滚动，分页作为兜底。

## Loading, Skeleton & Empty States（加载与空状态）

> 时长纪律（与 Motion 章一致）：< 300ms 无反馈；300ms–1s 旋转方块；> 1s 骨架屏；列表首屏必骨架。

### Skeleton 骨架屏
- **用 opacity 脉冲，不用渐变扫光**：骨架块底色 `{colors.surface-2}`，opacity 100%↔50% 呼吸，周期 `{motion.duration-shimmer}` 1400ms · ease-in-out——渐变扫光违反本系统无渐变纪律，脉冲是它的合法替代。
- 形状镜像真实内容：文本行高 14px（`{rounded.xs}`），数字位按 `{typography.mono}` 等宽块占位（保持对齐），图片位用角标容器。
- `prefers-reduced-motion` 下改为静态灰块。
- 骨架屏与真实内容切换 150ms opacity，禁止布局跳变（骨架高度必须等于真实高度）。

### Empty State 空状态（工程线框语言）
- 容器：`{card-corner-marked}` 虚线角标变体（仅左上 + 右下两角，隐喻“图纸未完成”）。
- 中心符号：48px 工程线框图标（`zw-*` 自绘系列或 Tabler 对应项）+ 标题 `{typography.card-title}` + 说明 `{typography.body-sm}` + `{colors.ink-subtle}` + 主操作按钮。
- 空态文案给方向不给道歉：“暂无合同——从立项开始创建”，不用“抱歉没有数据”。
- 筛选后为空 ≠ 业务为空：前者文案为“无匹配结果”+ 清除筛选操作，后者才给创建入口。

### Error State 错误态（网络/权限失败）
- 5xx/网络：`{zw-alert-triangle}` 线框符号 + `{colors.semantic-error}` 文案 + “重试”按钮（真实重试真实接口，不做假成功）。
- 403 无权限：说明缺失的角色/权限点 + 联系管理员路径；禁止空白静默。
- 错误态保持页面骨架结构，禁止整页白屏。
- 连续失败 3 次升级为全局 Message + 顶栏告警条（`{hazard-divider}` 细纹）。

## Images & Media（图片与媒体）

### 摄影语言替代插画（承重墙）
- 本系统零插画；门户与 Hero 用工程实景摄影（工地、钢结构、图纸细节、混凝土肌理）。
- 装饰用途可加 `filter: grayscale(100%) contrast(1.05)` 黑白处理（工程档案气质）；**业务图片（验收照片、现场照片）禁止任何滤镜**——工程影像是证据，必须原色。
- 图片容器 1px `{colors.hairline}` 边框 + `{rounded.sm}`；不加阴影。
- 加载失败占位：`{colors.surface-2}` 底 + 16px `zw-blueprint` 图标 + `{typography.caption}` 说明，禁止碎图标裸露。
- 缩略图统一比例：项目封面 16:9，现场照片方图 1:1；`srcset` 分档见 Responsive 章。

### 图纸缩略图（业务专属）
- 白底 + 1px `{colors.hairline-strong}` + 右下角 12px 图签角标（图号 `{typography.mono}` 9px）——CAD 图纸白底黑线是本系统独有的图片类型，保留图纸原貌，不做灰度处理。
- 作废/过期图纸：整体 `saturate(0)` + 左上角 `REV` 作废章式红色边框标记。
- 图纸预览支持滚轮缩放，缩放比例用 `{typography.mono}` 右上角标注（如 `1:100`）。

### Avatar 头像
- 方形头像（`{rounded.xs}`）为默认——`{rounded.full}` 圆形仅限系统级品牌位；与直角纪律保持一致。
- 无照片时：`{colors.surface-dark-3}` 底 + 姓名首字母大写 `{typography.eyebrow}` 白字；禁止随机彩色底（稀缺色纪律）。
- 尺寸阶梯：24（表格内）/ 32（顶栏）/ 40（评论区）三档。
- 审批场景头像旁叠加角色徽章（`{status-badge}` 微型变体）。

## Accessibility & Contrast（无障碍与对比度）

> WCAG 2.1 AA 门槛：正文 4.5:1；大字（≥18.66px bold 或 24px）与非文本图形 3:1。以下结论基于标准相对亮度公式手算验证（2026-08）。

### 关键对比度结论表（手算验证）
| 组合 | 对比度 | 判定 |
|---|---|---|
| `{colors.primary}` 底 + `{colors.on-primary}` 黑字 | ≈ 6.5:1 | ✓ 按钮文字，正文达标 |
| `{colors.primary}` 底 + 白字 | ≈ 2.9:1 | ✗ 禁止——橙底永远配深色字 |
| `{colors.ink-subtle}` 于 `{colors.surface-1}` | ≈ 3.9:1 | ⚠ 仅限 12–13px 辅助文本与装饰标签，不承载关键信息 |
| `{colors.ink-muted}` 于 `{colors.surface-1}` | ≈ 8.2:1 | ✓ 正文最小色阶——正文层级下限是 ink-muted 而非 ink-subtle |
| `{colors.semantic-warning}` 做前景文字 | ≈ 1.8:1 | ✗ 警示黄禁做前景色；仅做底色（配 `{colors.ink}` 黑字 ≈ 10:1） |
| `{colors.ink-dark}` 于 `{colors.canvas-dark}` | ≈ 16:1 | ✓ 暗面正文 |
| `{colors.ink-dark-subtle}` 于 `{colors.canvas-dark}` | ≈ 6:1 | ✓ 暗面辅助文本 |
| `{colors.primary}` 于白底（链接/图形） | ≈ 3.1:1 | ✓ 大文本与图形元素；小字链接需加粗或下划线辅助 |
| `{colors.semantic-error}` 于 `{colors.semantic-error-soft}` | ≈ 5:1 | ✓ 逾期徽章 |
| `{colors.ink-tertiary}` | ≈ 2.5:1 | ✗ 仅限 placeholder 与禁用字 |
| `{colors.hairline}` 边框于画布 | ≈ 1.2:1 | 边框不参与对比判定，控件识别靠形态与聚焦 |
- 关键结论：**正文最小色阶是 `{colors.ink-muted}`**；`ink-subtle` 降级为辅助/装饰；警示黄禁做前景色。
- 焦点可见性：2px 焦点环（primary / error 随组件）+ 2px gap，任意底色上均可识别；`:focus-visible` 永不被 `outline: none` 移除。
- 键盘导航：全部交互元素可 Tab 到达；modal 焦点圈禁 + Esc 关闭 + 焦点归还；危险确认弹层焦点默认落在取消按钮。
- 语义化：标题层级不跳级（页面 h1 唯一）；表格必须 caption/`aria-label`；纯图标按钮必须 `aria-label`；状态徽章的色彩语义同时有文字标签兜底（不单独依赖颜色）。
- 色盲安全：状态体系全部「色 + 文字标签」双通道；图表序列除颜色外可用线型区分。
- `prefers-reduced-motion`：全部动画降级为瞬切或 ≤100ms opacity；警示脉冲与账本滚动静止（Motion 章）。
- 触达目标：桌面 ≥ 32×32，移动端 ≥ 44×44（见 Responsive 章）。
- 表单校验错误必须同时：边框红 + 文字说明 + `aria-invalid`，不只靠颜色。

## Font Loading（字体加载策略）
- **加载清单（自托管，零 CDN 依赖）**：Barlow Condensed（D-DIN 开源替代）woff2 700 拉丁子集 ≈ 30KB；JetBrains Mono woff2 400/500/700 数字+拉丁子集 ≈ 120KB；合计 ≤ 200KB。
- **中文零 webfont**（承重墙）：思源黑体 Heavy 单字重 2MB+ 不可接受；中文全部走系统字体栈 `PingFang SC / Microsoft YaHei`。
- `font-display: swap` 全量；`unicode-range` 拆分拉丁/数字子集按需加载。
- `<link rel="preload" as="font">` 预加载首屏最重的 JetBrains Mono（账本数字是首屏主体）。
- 防布局漂移：`size-adjust` + 回退字体度量对齐（Barlow Condensed 配 `'Arial Narrow'`，JetBrains Mono 配 `ui-monospace`）。
- `font-feature-settings: "tnum"` 全局（配合 `font-variant-numeric: tabular-nums`）。
- 首屏文字必须在 1.5s 内可见（系统字体先行，webfont 到位后无感替换）。
- 图标库（Tabler）走 SVG 组件内联，不走 icon font（无 FOUT、可 tree-shaking）。

## Print Styles（打印与导出）

> 业务场景：审批单、结算报告、月报导出打印是工程管理刚需。
- `@media print`：隐藏侧边栏、顶栏、操作列、分页器与筛选区；内容区全宽。
- 字体：打印环境不保证 webfont，一律回退系统栈（Display 用 `Arial Narrow` 系，中文保持黑体）；正文 12px，行高 1.5。
- 色彩转灰阶纪律：画布转纯白，`{colors.canvas-dark}` 深块转实色黑；`{hazard-divider}` 斜纹转 4px 黑色实条（斜纹打印后成噪点）；状态徽章保留浅底深字（打印友好）。
- `@page { margin: 14mm; }`；`break-inside: avoid` 应用于表格行与卡片；跨页表格重复表头（`thead { display: table-header-group; }`）。
- 蓝图角标保留（打印时角标转实色黑）；水印区预留（合同类导出叠加「内部资料」斜向灰字）。
- 二维码/编号区（单据抬头右上角 `{typography.mono}` 编号）必须出现在首页首屏打印区。
- 数字列打印保持 `tabular-nums` 右对齐；金额保留千分位与两位小数。

## Large Display Mode（监控大屏模式）
- 画布锁定 `{colors.canvas-dark}`；`{colors.canvas}` 亮面在大屏禁用（值班室暗光环境）。
- 布局：1920×1080 基准，12 列网格；内容铺满无滚动（大屏零滚动原则），超出信息量分页轮播。
- 字体阶梯：KPI 数字 `{typography.mono-num}` 放大至 48–64px；区块标题 `{typography.display-lg}` 大写；眉题 `{typography.eyebrow}`。
- 数据刷新：`{motion.duration-fast}` 150ms 数字翻牌（Motion 章）；顶部常驻「最后刷新」时间戳 `{typography.mono}` + 刷新状态点（绿色呼吸 2s 周期）。
- 断线降级：数据源失联时顶部压 `{hazard-divider}` 告警条 + 数字冻结并标注冻结时刻——大屏禁止静默展示过期数据。
- 大屏只读：无表单交互；图表 hover tooltip 保留但触达放大。
- 大屏图表色板用暗面镜像序列（Data Visualization 章）。
- 大屏禁用账本滚动入场（常驻页面反复触发会疲劳），仅首载一次。
- 夜间模式可选自动降亮（整体 `filter: brightness(0.9)`）。

## Mobile Shell（移动端壳层）
- 壳层：顶栏 48px（白底 + 1px `{colors.hairline}`）+ 底部 TabBar 56px；侧边栏抽屉化（300ms · ease-out，Motion 章）。
- 主色使用不变（稀缺纪律同桌面）；侧边栏抽屉保持 `{colors.canvas-dark}` 控制室语义。
- 卡片间距降档：页边距 16，卡间 12；`{spacing.section}` 96px 在移动端永不使用。
- 表格转卡片列表规则见 Responsive 章；卡片头部 = `{typography.mono}` 编号 + `{status-badge}`。
- 表单：输入控件高 44px；金额输入 `{typography.mono}` 右对齐不变；日期选择用原生控件（工程现场弱网环境，轻量优先）。
- 底部操作栏固定主按钮（`{button-primary}` 全宽 44px 高）；危险操作在移动端同样走输入确认（MessageBox 章），不因移动端简化。
- 手势：列表下拉刷新用旋转方块符号；左滑行出操作仅限单一高频操作（如审批通过/驳回），且必须露出文字标签。
- 移动端动效预算更紧：禁用账本滚动与角标描画（性能与耗电），仅保留 150ms 状态过渡。
- 离线提示：弱网/断网顶栏告警条（`{colors.semantic-warning-soft}` 底 + `{colors.ink}` 字），不做静默重试欺骗。
- 移动端不渲染 `{hero-band}`（大屏营销面），登录页简化为表单 + 品牌铭牌。
- 移动端字号下限 13px，caption 12px 仅限时间戳。
- 安全区适配：底部 TabBar 适配 `env(safe-area-inset-bottom)`。

## Detail Finishing（细节收尾）
- **滚动条**：6px 方形，滑块 `{colors.hairline-strong}`，悬停 `{colors.ink-subtle}`，无圆角（`::-webkit-scrollbar`）；暗面滑块 `{colors.hairline-dark-strong}`。
- **选中态**：`::selection` 底 `{colors.primary}` + `{colors.on-primary}` 深字。
- **焦点环**：全局 `:focus-visible` 统一 2px `{colors.primary}` outline + 2px 偏移；暗面同色（橙在石墨黑上对比 ≈ 6.5:1 达标）。
- **数字**：全局 `tabular-nums`；表格/表单禁用浏览器自动拼写红线（`spellcheck="false"`）。
- **滚动行为**：禁用 `scroll-behavior: smooth` 全局（数据密集界面跳转要瞬时定位）；锚点定位例外可用。
- **拖拽**：列宽/看板拖拽用 2px `{colors.primary}` 插入指示线，不用半透明幽灵块。
- **快捷键提示**：`⌘/Ctrl + K` 等提示用 `{typography.mono}` 11px + 1px `{colors.hairline-strong}` 键帽框（直角）。
- **Toast/Message 文案**：动词开头、说明结果、可带操作（如「已提交审批 · 查看」），不用「成功！」式空话。
- **页面标题**：`<title>` 格式 `模块名 · 中维智营`，便于多页签区分。
- **右键菜单**：不做自定义右键菜单（工程用户依赖浏览器原生行为）。
- **浏览器兼容**：Chrome/Edge 最近两个大版本为基准；不支持 IE（D-DIN 替代链在 IE 下优雅降级到 Arial Narrow 即可）。

## Research Log（调研与交叉验证记录）
> 依项目规则：改造优化需完整记录调研详情。以下为本版本（动效/图标/状态矩阵/缺口补全）的调研来源与交叉验证结论，2026-08。
- **动效系统**：Atlassian Motion 官方规范（语义 token、交互 50–150ms / 过渡 150–400ms 时长分区、频率决定表达）×2 与 Cloudflare `frontend-design-saas`（`cubic-bezier(0.16,1,0.3,1)`、75–500ms 阶梯、「产品 UI 超 300ms 即错」）双源结论一致 → Motion 章全部取值。
- **图标选型**：GitHub 事实核查（Tabler 21.5k⭐ MIT / Lucide 24.2k⭐ ISC，均活跃维护）+ Reddit r/UXDesign 从业者口碑（Lucide 一致性最佳）+ 规格对比（同为 24px 网格 2px 线宽）→ Tabler 主、Lucide 辅。
- **工业风趋势**：Industrial Brutalist UI（2026）双原型（Swiss Industrial Print / Tactical Telemetry）——可见分隔线、ASCII 框注、禁渐变阴影，与 SpaceX/Linear/NVIDIA 源风格互证 → 角标描画、直角节点、无渐变纪律获得当代趋势背书。
- **图表色板**：Datawrapper 与 FusionCharts 配色指南一致结论（中性灰主序列 + 单一高亮 + 跨图实体同色）→ Data Visualization 章。
- **中文字体**：思源黑体免费商用但 Heavy 字重单文件 2MB+，首屏不可接受 → 系统重黑 + 铭牌字距方案（Typography 章）。
- **对比度**：WCAG 2.1 相对亮度公式手算 11 组关键组合 → Accessibility 章判定表；衍生出「橙底黑字」「警示黄禁做前景」「正文最小色阶 ink-muted」三条承重规则。
- **调研工具链备注**：Exa MCP 本次不可用，实际经 WebSearch + WebFetch + gh search + rdt 四通道完成交叉验证，结论未受单源影响。
- **待验证事项**：D-DIN 免费版分发许可需在实施前二次确认（法务），不可用则整体切 Barlow Condensed。

## Mix Sources

| 源风格 | 取自 | 用于本系统的 |
|---|---|---|
| `design-md/spacex` | D-DIN 大写正字距、全幅暗色 Hero、幽灵按钮、工程克制感 | 字体气质、Hero/登录、品牌基调 |
| `design-md/linear.app` | surface 阶梯、hairline 纵深、无阴影纪律、mono 数字、产品 UI 为主角 | 应用壳层、卡片/表格/表单纪律 |
| `design-md/nvidia` | 2px 直角、角标方块、硬朗编辑网格、深黑+单色强调的双画布结构 | 形态语言、角标组件、双画布结构 |
| 建筑工程域定制 | 安全橙 #ff6b00、黑黄警示条纹、等宽数字账本、逾期专属告警语言 | 行业身份与业务语义（本 Mix 原创） |
| 外部调研补充（2026-08） | Atlassian + Cloudflare 动效规范、Tabler/Lucide 图标、Industrial Brutalist UI 趋势、Datawrapper 图表原则、WCAG 对比度手算 | Motion、Iconography、State Matrix、Data Visualization、Accessibility 等章节（详见 Research Log） |
