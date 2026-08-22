/**
 * 能力扫描器：对页面 vue 源码与后端 Java 源码跑八维信号规则
 *
 * 性能设计：每文件 readFileSync 一次后对同一字符串跑全部规则（单文件单次 IO），
 * 130+ 文件量级实测秒级完成，不做缓存。
 *
 * 后端信号归属：Controller 按类名与页面 slug 归一化匹配（如 finance/settlement ↔
 * SettlementController）；匹配不到时回退该模块全部 Controller 信号并集（标注 module-level）。
 */
import * as fs from 'node:fs';
import * as path from 'node:path';
import { EVIDENCE_CAP, SIGNAL_RULES } from './signals.js';
import type { Dimension, PageManifestItem, Platform, SignalEvidence } from './types.js';

/** 后端服务器模块根 */
const SERVER_ROOT = 'zw-insight-server';

/** 前端模块名 → 后端模块目录名映射（默认 zw-{name}，特例在此登记） */
const BACKEND_MODULE_ALIAS: Record<string, string> = {
  platform: 'system', // 平台管理（租户/存储）API 实际在 zw-system
};

/** 无后端对应的前端域（纯前端页面/导航壳） */
const NO_BACKEND_MODULES = new Set(['login', 'user', 'error', 'nav']);

/** 归一化 slug：去连字符/下划线后小写（invoice-apply → invoiceapply） */
function norm(s: string): string {
  return s.replace(/[-_]/g, '').toLowerCase();
}

/** 对单文件内容跑指定范围的全部规则，返回维度 → 证据 */
export function runSignalRules(
  content: string,
  fileRel: string,
  scope: 'vue' | 'java',
): Map<Dimension, SignalEvidence> {
  const result = new Map<Dimension, SignalEvidence>();
  const lines = content.split('\n');

  for (const rule of SIGNAL_RULES) {
    if (rule.scope !== scope) continue;
    let evidence: SignalEvidence | undefined = result.get(rule.dimension);
    for (let i = 0; i < lines.length; i++) {
      const line = lines[i];
      if (!rule.pattern.test(line)) continue;
      if (rule.exemption && rule.exemption.test(line)) continue;

      if (!evidence) {
        evidence = { count: 0, evidence: [] };
        result.set(rule.dimension, evidence);
      }
      evidence.count++;
      if (evidence.evidence.length < EVIDENCE_CAP) {
        evidence.evidence.push(`${fileRel}:${i + 1} [${rule.name}]`);
      }
    }
  }
  return result;
}

/** 合并两份信号（后者补充计数与证据） */
export function mergeSignals(
  target: Map<Dimension, SignalEvidence>,
  extra: Map<Dimension, SignalEvidence>,
): Map<Dimension, SignalEvidence> {
  for (const [dim, ev] of extra) {
    const existing = target.get(dim);
    if (!existing) {
      target.set(dim, { count: ev.count, evidence: [...ev.evidence] });
    } else {
      existing.count += ev.count;
      for (const e of ev.evidence) {
        if (existing.evidence.length < EVIDENCE_CAP) existing.evidence.push(e);
      }
    }
  }
  return target;
}

/** 后端模块自动发现（zw-* 目录，消灭硬编码） */
export function discoverBackendModules(rootPath: string): string[] {
  const serverDir = path.join(rootPath, SERVER_ROOT);
  return fs
    .readdirSync(serverDir, { withFileTypes: true })
    .filter((e) => e.isDirectory() && e.name.startsWith('zw-'))
    .map((e) => e.name.slice('zw-'.length))
    .sort();
}

/** 前端模块名 → 后端模块名（无对应返回 null） */
export function backendModuleOf(frontendModule: string): string | null {
  if (NO_BACKEND_MODULES.has(frontendModule)) return null;
  return BACKEND_MODULE_ALIAS[frontendModule] ?? frontendModule;
}

/** 已扫描的 Controller 信号 */
export interface ControllerSignals {
  module: string;
  className: string; // 如 SettlementController
  fileRel: string;
  /** 类级 @RequestMapping 路径（如 /api/v1/budget/change），用于按页面实际调用端点精确匹配 */
  basePath: string;
  signals: Map<Dimension, SignalEvidence>;
  /** 各规则命中次数（评分器区分 L2 状态渲染 / L3 流转端点用） */
  ruleHits: RuleHits;
}

/** 规则命中详情：规则名 → 命中次数 */
export type RuleHits = Record<string, number>;

/** 对单文件内容统计各规则命中次数（与 runSignalRules 同口径，含豁免） */
export function runRuleHits(content: string, scope: 'vue' | 'java'): RuleHits {
  const hits: RuleHits = {};
  const lines = content.split('\n');
  for (const rule of SIGNAL_RULES) {
    if (rule.scope !== scope) continue;
    for (const line of lines) {
      if (!rule.pattern.test(line)) continue;
      if (rule.exemption && rule.exemption.test(line)) continue;
      hits[rule.name] = (hits[rule.name] ?? 0) + 1;
    }
  }
  return hits;
}

/** 合并规则命中（后者累加进前者） */
export function mergeRuleHits(target: RuleHits, extra: RuleHits): RuleHits {
  for (const [name, count] of Object.entries(extra)) {
    target[name] = (target[name] ?? 0) + count;
  }
  return target;
}

/** 扫描全部模块的 Controller（每文件单次 IO） */
export function scanControllers(rootPath: string): ControllerSignals[] {
  const controllers: ControllerSignals[] = [];

  for (const module of discoverBackendModules(rootPath)) {
    const moduleDir = path.join(rootPath, SERVER_ROOT, `zw-${module}`);
    const javaFiles = collectJavaFiles(moduleDir);
    for (const abs of javaFiles) {
      const base = path.basename(abs, '.java');
      if (!base.endsWith('Controller')) continue;
      const fileRel = path.relative(rootPath, abs).split(path.sep).join('/');
      const content = fs.readFileSync(abs, 'utf-8');
      const basePath = content.match(/@RequestMapping\("([^"]+)"\)/)?.[1] ?? '';
      controllers.push({
        module,
        className: base,
        fileRel,
        basePath,
        signals: runSignalRules(content, fileRel, 'java'),
        ruleHits: runRuleHits(content, 'java'),
      });
    }
  }
  return controllers;
}

/** 递归收集 .java 文件 */
function collectJavaFiles(dir: string): string[] {
  const out: string[] = [];
  for (const ent of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, ent.name);
    if (ent.isDirectory()) {
      if (ent.name === 'target') continue; // 跳过构建产物
      out.push(...collectJavaFiles(full));
    } else if (ent.name.endsWith('.java')) {
      out.push(full);
    }
  }
  return out;
}

/** 为页面挑选最匹配的 Controller 集合：slug 归一化前缀匹配优先，回退模块全量 */
export function matchControllers(
  page: PageManifestItem,
  controllers: ControllerSignals[],
): { matched: ControllerSignals[]; fallback: boolean } {
  const backend = backendModuleOf(page.module);
  if (!backend) return { matched: [], fallback: false };

  const moduleControllers = controllers.filter((c) => c.module === backend);
  if (moduleControllers.length === 0) return { matched: [], fallback: false };

  const slug = norm(page.pagePath.split('/').filter(Boolean).pop() ?? '');
  if (slug && slug !== 'index') {
    const exact = moduleControllers.filter((c) =>
      norm(c.className.replace(/Controller$/, '')).startsWith(slug),
    );
    if (exact.length > 0) return { matched: exact, fallback: false };
    // 反向包含（slug 比类名长，如 invoiceapply ↔ invoiceapply 的详情页）
    const contains = moduleControllers.filter((c) => slug.includes(norm(c.className.replace(/Controller$/, ''))));
    if (contains.length > 0) return { matched: contains, fallback: false };
  }
  return { matched: moduleControllers, fallback: true };
}

/**
 * 从页面源码解析其实际调用的后端端点，匹配 Controller（最可靠的一级匹配）。
 *
 * 链路：vue import { fn } from '@/api/x' → api/x.ts 中该函数体内的 request 调用
 * → 端点按段与 Controller basePath 最长前缀匹配（{xxx} 段视为通配）。
 * 只取页面实际 import 的函数（函数级粒度），避免同 api 文件其他功能的端点串扰。
 * 返回 null 表示页面未导入 api 或 api 文件不可解析（调用方回退 slug 匹配）。
 */
export function matchControllersByEndpoints(
  rootPath: string,
  pageVueContent: string,
  moduleControllers: ControllerSignals[],
): ControllerSignals[] | null {
  const endpoints = new Set<string>();
  // 支持多行 import：import { a,\n b } from '@/api/x'；[^{}]* 防止跨 import 语句贪婪匹配（逗号合法）
  for (const im of pageVueContent.matchAll(/import\s*\{([^{}]*)\}\s*from '@\/api\/([\w-]+)'/g)) {
    const fnNames = im[1]
      .split(',')
      .map((s) => s.trim().split(/\s+as\s+/)[0]) // 去 `a as b` 别名
      .filter((s) => /^[\w$]+$/.test(s));
    if (fnNames.length === 0) continue;

    const apiFile = path.join(rootPath, 'zw-insight-web/src/api', `${im[2]}.ts`);
    if (!fs.existsSync(apiFile)) return null; // api 文件不可解析 → 放弃精确匹配
    const content = fs.readFileSync(apiFile, 'utf-8');
    for (const block of extractFunctionBlocks(content, fnNames)) {
      // [^(]* 跳过任意嵌套泛型（如 <R<PageResult<Budget>>>），取第一个参数（'...' 或 `...`）
      for (const m of block.matchAll(
        /request\.(?:get|post|put|delete|patch)[^(]*\(\s*(?:'([^']+)'|`([^`]+)`)/g,
      )) {
        endpoints.add(m[1] ?? m[2]);
      }
    }
  }
  if (endpoints.size === 0) return null;

  const matched = new Set<ControllerSignals>();
  for (const ep of endpoints) {
    const segs = normalizeEndpoint(ep).split('/').filter(Boolean);
    let best: ControllerSignals | null = null;
    let bestLen = -1;
    for (const c of moduleControllers) {
      // 与端点同口径：去 /api/v1 前缀，仅比较资源段
      const baseSegs = c.basePath.replace(/^\/(api\/)?v1\//, '').split('/').filter(Boolean);
      if (baseSegs.length === 0 || baseSegs.some((s) => !s.startsWith('{') && !s)) continue;
      if (baseSegs.length > segs.length) continue;
      const ok = baseSegs.every((b, i) => b.startsWith('{') || b === segs[i]);
      if (ok && baseSegs.length > bestLen) {
        best = c;
        bestLen = baseSegs.length;
      }
    }
    if (best) matched.add(best);
  }
  return matched.size > 0 ? [...matched] : null;
}

/** 从 api 文件源码中提取指定导出函数的函数体文本（到下一个顶层 export 为止） */
function extractFunctionBlocks(content: string, fnNames: string[]): string[] {
  const exportIdx: number[] = [...content.matchAll(/^export /gm)].map((m) => m.index!);
  const blocks: string[] = [];
  for (const name of fnNames) {
    const re = new RegExp(`^export (?:async )?function ${name}\\b`, 'm');
    const m = content.match(re);
    if (!m || m.index === undefined) continue;
    const start = m.index;
    const next = exportIdx.find((i) => i > start);
    // 去注释：防止注释里的 submit/PUT 等词被规则误计（如「PUT 405 实证」）
    blocks.push(
      content
        .slice(start, next ?? content.length)
        .replace(/\/\/.*$/gm, '')
        .replace(/\/\*[\s\S]*?\*\//g, ''),
    );
  }
  return blocks;
}

/** 端点归一化：`/v1/budget/${id}/submit` 模板串 → budget/{id}/submit；去查询串与 /api 前缀 */
function normalizeEndpoint(ep: string): string {
  return ep
    .replace(/\$\{[^}]*\}/g, '{id}')
    .split('?')[0]
    .replace(/^\/(api\/)?v1\//, '');
}

/** 扫描单页面文件（vue 规则 + 行数） */
export function scanVuePage(
  rootPath: string,
  pageFile: string,
): { lineCount: number; signals: Map<Dimension, SignalEvidence>; ruleHits: RuleHits; exists: boolean } {
  const abs = path.join(rootPath, pageFile);
  if (!fs.existsSync(abs)) {
    return { lineCount: 0, signals: new Map(), ruleHits: {}, exists: false };
  }
  const content = fs.readFileSync(abs, 'utf-8');
  return {
    lineCount: content.split('\n').length,
    signals: runSignalRules(content, pageFile, 'vue'),
    ruleHits: runRuleHits(content, 'vue'),
    exists: true,
  };
}

/** 计算平台适用维度（mobile 仅三维子集） */
export function applicableDimensions(platform: Platform): Dimension[] {
  const all: Dimension[] = [
    'efficiency', 'query', 'state', 'audit', 'notify', 'permission', 'error', 'value',
  ];
  if (platform === 'pc') return all;
  return ['efficiency', 'error', 'notify'];
}
