#!/usr/bin/env node
/**
 * zw-ledger CLI：功能深度账本工具入口
 *
 * 子命令：
 *   scan    扫描页面清单 + 八维能力信号 → 生成/合并 data/ledger-data.json
 *   report  从账本 JSON 生成 audit-reports/feature-ledger/feature-ledger-report.md
 */
import { Command } from 'commander';
import * as fs from 'node:fs';
import * as path from 'node:path';
import { fileURLToPath } from 'node:url';

import {
  applicableDimensions,
  backendModuleOf,
  matchControllers,
  matchControllersByEndpoints,
  mergeRuleHits,
  mergeSignals,
  scanControllers,
  scanVuePage,
  type ControllerSignals,
  type RuleHits,
} from './capability-scanner.js';
import {
  diffWithViewsDir,
  featureIdOf,
  groupOf,
  PC_BASELINE_COUNT,
  scanMobilePages,
  scanPcPages,
} from './manifest-scanner.js';
import { mergeLedger } from './merge.js';
import { renderMarkdownReport } from './reporters/markdown-reporter.js';
import { scoreEntry } from './scoring.js';
import { SIGNAL_VERSION } from './signals.js';
import type {
  Dimension,
  LedgerData,
  LedgerEntry,
  PageManifestItem,
  SignalEvidence,
} from './types.js';

const pkg = JSON.parse(
  fs.readFileSync(new URL('../package.json', import.meta.url), 'utf-8'),
) as { version: string };

/** 仓库根目录（tools/feature-ledger/src → 上三级；tsc 构建后 dist 同构） */
const DEFAULT_ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..', '..');

const DATA_FILE_DEFAULT = 'tools/feature-ledger/data/ledger-data.json';
const REPORT_DIR_DEFAULT = 'audit-reports/feature-ledger';
const REPORT_FILE = 'feature-ledger-report.md';

interface CliOptions {
  root: string;
  data: string;
  module?: string;
  output?: string;
}

const program = new Command();
program
  .name('zw-ledger')
  .description('ZW-Insight 全模块功能深度账本：L0-L4 成熟度评分 + 八维缺口标注')
  .version(pkg.version);

program
  .command('scan')
  .description('扫描页面清单与八维能力信号，生成/合并账本数据（manual 人工字段不受影响）')
  .option('--root <path>', '仓库根目录', DEFAULT_ROOT)
  .option('--data <file>', '账本数据文件（相对 root）', DATA_FILE_DEFAULT)
  .option('--module <names>', '仅扫描指定模块（逗号分隔，用于增量复核）')
  .action((opts: CliOptions) => {
    try {
      runScan(opts);
    } catch (err) {
      console.error(`✗ 扫描失败：${err instanceof Error ? err.message : String(err)}`);
      process.exit(1);
    }
  });

program
  .command('report')
  .description('从账本 JSON 生成 markdown 报告（只读账本数据，不修改）')
  .option('--root <path>', '仓库根目录', DEFAULT_ROOT)
  .option('--data <file>', '账本数据文件（相对 root）', DATA_FILE_DEFAULT)
  .option('--output <dir>', '报告输出目录（相对 root）', REPORT_DIR_DEFAULT)
  .action((opts: CliOptions) => {
    try {
      runReport(opts);
    } catch (err) {
      console.error(`✗ 报告生成失败：${err instanceof Error ? err.message : String(err)}`);
      process.exit(1);
    }
  });

program.parse();

// ---------------------------------------------------------------------------
// scan
// ---------------------------------------------------------------------------

function runScan(opts: CliOptions): void {
  const started = Date.now();
  const root = path.resolve(opts.root);
  console.log(`扫描仓库：${root}`);

  // 1) 页面清单（全量解析，基准校验基于全量口径）
  const pc = scanPcPages(root);
  const mobile = scanMobilePages(root);
  const warnings: string[] = [...pc.warnings, ...mobile.warnings];
  for (const d of diffWithViewsDir(root, pc.pages)) warnings.push(`清单差异: ${d}`);

  // PC 基准自校验：按唯一组件去重后的功能页数对比基准
  // （router path 数会因 redirect 入口重复而偏多，不宜直接对比）
  const pcUniqCount = new Set(pc.pages.map((p) => featureIdOf(p))).size;
  if (pcUniqCount !== PC_BASELINE_COUNT) {
    warnings.push(
      `PC 功能页数 ${pcUniqCount} 与基准 ${PC_BASELINE_COUNT} 不符（前端路由可能已增删，属正常演进，请复核清单差异）`,
    );
  }

  const moduleFilter = opts.module
    ? new Set(
        opts.module
          .split(',')
          .map((s) => s.trim())
          .filter(Boolean),
      )
    : null;
  if (moduleFilter) console.log(`模块过滤：${[...moduleFilter].join(', ')}`);

  let pages: PageManifestItem[] = [...pc.pages, ...mobile.pages];
  if (moduleFilter) pages = pages.filter((p) => moduleFilter.has(p.module));

  // 按 featureId 去重：同一 vue 组件被多个路由 path 引用（redirect 入口 + 实际页）时，
  // 保留路径最具体的代表（如 /budget/list 优于 /budget）
  const seen = new Map<string, PageManifestItem>();
  const depth = (p: PageManifestItem): number => p.pagePath.split('/').filter(Boolean).length;
  for (const p of pages) {
    const id = featureIdOf(p);
    const prev = seen.get(id);
    if (!prev || depth(p) > depth(prev)) seen.set(id, p);
  }
  pages = [...seen.values()];

  // 2) 后端 Controller 信号（全量扫描一次，内存匹配）
  const controllers = scanControllers(root);

  // 3) 逐页：vue 信号 + 匹配 Controller java 信号 → 评分
  const freshEntries: LedgerEntry[] = pages.map((page) => buildEntry(root, page, controllers));

  // 4) 与旧账本 merge（manual 字段保护）
  const dataFile = path.resolve(root, opts.data);
  const previous: LedgerData | null = fs.existsSync(dataFile)
    ? (JSON.parse(fs.readFileSync(dataFile, 'utf-8')) as LedgerData)
    : null;
  const mergeResult = mergeLedger(previous, freshEntries, SIGNAL_VERSION, moduleFilter);
  mergeResult.data.warnings = warnings; // 警告每次全量刷新

  // 5) 写账本
  fs.mkdirSync(path.dirname(dataFile), { recursive: true });
  fs.writeFileSync(dataFile, JSON.stringify(mergeResult.data, null, 2) + '\n', 'utf-8');

  // 6) 摘要
  const activeEntries = mergeResult.data.entries.filter((e) => !e.removed);
  const needsReview = activeEntries.filter((e) => e.confidence === 'needs-review');
  const backendModules = new Set(controllers.map((c) => c.module)).size;
  const duration = ((Date.now() - started) / 1000).toFixed(1);

  console.log('');
  console.log(`✓ 扫描完成（${duration}s）`);
  console.log(
    `  条目：${activeEntries.length}（PC ${activeEntries.filter((e) => e.platform === 'pc').length}` +
      ` / 移动端 ${activeEntries.filter((e) => e.platform === 'mobile').length}）` +
      `｜新增 ${mergeResult.added.length}｜下线 ${mergeResult.removed.length}｜保留人工判断 ${mergeResult.preserved} 条`,
  );
  console.log(`  后端：${backendModules} 模块 / ${controllers.length} Controller`);
  console.log(
    `  置信度：high ${activeEntries.length - needsReview.length}｜needs-review ${needsReview.length}`,
  );
  console.log(`  数据：${path.relative(root, dataFile)}`);
  if (mergeResult.versionChanged && previous) {
    console.log(`⚠ 信号规则版本 v${previous.signalVersion} → v${SIGNAL_VERSION}（规则已修正，建议全量复核）`);
  }
  if (needsReview.length > 0) {
    console.log('');
    console.log(`需人工复核（${needsReview.length} 条）：`);
    for (const e of needsReview) {
      console.log(`  - ${e.featureId}：${e.scoreReasons.join('；')}`);
    }
  }
  if (warnings.length > 0) {
    console.log('');
    console.log(`警告（${warnings.length} 条）：`);
    for (const w of warnings) console.log(`  - ${w}`);
  }
}

/** 组装单个账本条目：vue 扫描 + Controller 匹配 + 维度过滤 + 评分 */
function buildEntry(
  root: string,
  page: PageManifestItem,
  controllers: ControllerSignals[],
): LedgerEntry {
  const vueScan = scanVuePage(root, page.pageFile);

  let signals = new Map<Dimension, SignalEvidence>(vueScan.signals);
  let ruleHits: RuleHits = { ...vueScan.ruleHits };
  let controllerFallback = false;
  let hasBackend = false;

  // mobile 不采用 java 信号：模块级回退噪声大，移动端只评页面自身三维
  if (page.platform === 'pc') {
    const backend = backendModuleOf(page.module);
    hasBackend = backend !== null;
    const moduleControllers = backend ? controllers.filter((c) => c.module === backend) : [];

    // 一级：解析页面实际调用的端点匹配 Controller（最可靠）；失败回退 slug/模块级
    const vueAbs = path.join(root, page.pageFile);
    const vueContent = fs.existsSync(vueAbs) ? fs.readFileSync(vueAbs, 'utf-8') : '';
    const byEndpoint = vueContent
      ? matchControllersByEndpoints(root, vueContent, moduleControllers)
      : null;
    const matched = byEndpoint
      ? { matched: byEndpoint, fallback: false }
      : matchControllers(page, controllers);
    controllerFallback = matched.fallback;
    for (const c of matched.matched) {
      signals = mergeSignals(signals, c.signals);
      ruleHits = mergeRuleHits(ruleHits, c.ruleHits);
    }
  }

  // 维度适用性过滤（mobile 仅效率/异常/通知三维子集）
  const dims = new Set(applicableDimensions(page.platform));
  const entrySignals: Partial<Record<Dimension, SignalEvidence>> = {};
  for (const [dim, ev] of signals) {
    if (dims.has(dim)) entrySignals[dim] = ev;
  }

  const score = scoreEntry({
    platform: page.platform,
    pageExists: vueScan.exists,
    lineCount: vueScan.lineCount,
    signals: entrySignals,
    ruleHits,
    controllerFallback,
    hasBackend,
  });

  return {
    featureId: featureIdOf(page),
    module: page.module,
    group: groupOf(page.module),
    platform: page.platform,
    pagePath: page.pagePath,
    pageFile: page.pageFile,
    title: page.title,
    signals: entrySignals,
    levelAuto: score.levelAuto,
    confidence: score.confidence,
    lineCount: vueScan.lineCount,
    scoreReasons: score.reasons,
    ruleHits,
  };
}

// ---------------------------------------------------------------------------
// report
// ---------------------------------------------------------------------------

function runReport(opts: CliOptions): void {
  const root = path.resolve(opts.root);
  const dataFile = path.resolve(root, opts.data);
  if (!fs.existsSync(dataFile)) {
    console.error(`账本数据不存在：${dataFile}，请先执行 scan 子命令`);
    process.exit(1);
  }
  const data = JSON.parse(fs.readFileSync(dataFile, 'utf-8')) as LedgerData;

  const md = renderMarkdownReport(data);
  const outputDir = path.resolve(root, opts.output ?? REPORT_DIR_DEFAULT);
  fs.mkdirSync(outputDir, { recursive: true });
  const outFile = path.join(outputDir, REPORT_FILE);
  fs.writeFileSync(outFile, md, 'utf-8');

  console.log(`✓ 报告已生成：${path.relative(root, outFile)}（${md.split('\n').length} 行）`);
}
