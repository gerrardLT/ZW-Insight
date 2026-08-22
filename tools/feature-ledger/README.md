# 功能深度账本（feature-ledger）

对 ZW-Insight 全部功能页面（PC + 移动端）做 **L0-L4 成熟度评分 + 八维缺口标注**，产出可排期的 ROI 差距清单。回答一个问题：**哪些功能最基础、补齐什么的投入产出比最高。**

> 与 `tools/consistency-audit` 的分工：一致性审计评「前后端接口是否对齐」，本账本评「产品功能能力成熟度」。两者正交、互不替代。与 `tests/frontend-test-case-matrix.md` 的分工：测试矩阵评「测试覆盖」，本账本评「功能深度」。

## 快速开始

```bash
cd tools/feature-ledger
npm install
npm run dev -- scan     # 扫描 → 生成/合并 data/ledger-data.json
npm run dev -- report   # 从账本 JSON 生成 audit-reports/feature-ledger/feature-ledger-report.md
npm test                # 运行规则/评分/合并的单元测试
```

## 子命令与选项

| 命令 | 说明 |
|---|---|
| `scan` | 扫描页面清单 + 八维能力信号，按 featureId 合并进账本（manual 人工字段不受影响） |
| `report` | 只读账本 JSON，生成聚合 markdown 报告 |

通用选项：

- `--root <path>`：仓库根目录（默认自动推断为工具所在仓库根）
- `scan --data <file>`：账本数据文件（默认 `tools/feature-ledger/data/ledger-data.json`）
- `scan --module <names>`：仅扫描指定模块（逗号分隔，用于增量复核，如 `--module finance,material`）
- `report --output <dir>`：报告输出目录（默认 `audit-reports/feature-ledger`）

## 数据架构：auto / manual 分离（核心）

`data/ledger-data.json` 是唯一数据源。每个条目分两层字段：

- **auto 层（脚本每次 scan 覆写）**：`signals` / `levelAuto` / `confidence` / `lineCount` / `scoreReasons`
- **manual 层（人工填写，重跑永不覆盖）**：`levelFinal` / `gapNotes` / `benchmarkNote` / `roi`

重跑 `scan` 只刷新 auto 层；人工判断沉淀在 manual 层。页面被删除时标 `removed:true` 而非物理删除，以保留历史判断。

## manual 字段填写规范

| 字段 | 类型 | 说明 |
|---|---|---|
| `levelFinal` | `0-4` | 复核后的最终成熟度等级（报告以此为准，缺省用 levelAuto） |
| `gapNotes` | `{ 维度: 说明 }` | 各维度缺口的人工说明，维度 key 用英文（efficiency/query/state/audit/notify/permission/error/value） |
| `benchmarkNote` | `string` | 领域对标判断（参照行业产品功能矩阵，如合同/财务/预算高频域） |
| `roi` | `{ impact, effort, priority? }` | impact/effort 均为 1-5；priority 可缺省（P0/P1/P2），报告按优先级渲染差距清单 |

## 成熟度量表

| 等级 | 语义 | 自动判据 |
|---|---|---|
| L0 | 缺失/占位 | 页面文件缺失或 <40 行 |
| L1 | 单路径 CRUD | 默认（生成器 CRUD 骨架） |
| L2 | 规则完整 | 状态渲染/状态门禁（PC）；表单校验/错误分支（mobile） |
| L3 | 协同流转 | 存在 submit/approve/withdraw/reject 等流转端点 |
| L4 | 数据智能 | 存在统计/趋势/排行端点或图表 |

## 信号规则调整方法

所有信号规则集中在 `src/signals.ts` 的 `SIGNAL_RULES` 数组（声明式，唯一可调参数）。每条规则含 `dimension` / `scope`（vue/java）/ `name` / `pattern`（命中正则）/ `exemption`（豁免正则，消化「导出模板」类误报）。

**修改规则后务必将 `SIGNAL_VERSION` +1**：scan 会检测到版本变化并提示「规则已修正，建议全量复核」。

## 置信度与复核

`confidence` 分两档：

- `high`：信号充分，levelAuto 可直接采信
- `needs-review`：信号稀疏/冲突/后端为模块级回退匹配等，需人工复核

人工复核约定：只复核 `needs-review` 子集 + 抽查 10% 高置信条目验证规则准确性，填 `levelFinal` 等 manual 字段。scan 输出会列出全部 needs-review 条目及原因。

## 目录结构

```
tools/feature-ledger/
├── src/
│   ├── cli.ts                    # CLI 入口（scan / report）
│   ├── types.ts                  # 账本 schema（LedgerEntry auto/manual）
│   ├── manifest-scanner.ts       # 页面清单（router 嵌套解析 + pages.json + 基准自校验）
│   ├── signals.ts                # 八维信号规则表（声明式）
│   ├── capability-scanner.ts     # 能力扫描（vue + Controller，单文件单次 IO）
│   ├── scoring.ts                # L0-L4 确定性评分
│   ├── merge.ts                  # auto/manual 分离合并
│   └── reporters/
│       └── markdown-reporter.ts  # markdown 报告（首屏即答案）
├── tests/                        # vitest 单元测试
└── data/
    └── ledger-data.json          # 账本数据（入库，含人工 manual 字段）
```

## 基准自校验

PC 期望 104 页、移动端期望 28 页（对齐 `tests/frontend-test-case-matrix.md` 封板基准）。偏离仅警告并在报告附录列出，不阻塞扫描。
