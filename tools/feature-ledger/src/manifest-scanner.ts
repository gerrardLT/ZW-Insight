/**
 * 页面清单扫描器
 *
 * PC：正则 + 括号配对解析 zw-insight-web/src/router/index.ts 的 constantRoutes
 *     （嵌套 children 递归拼接父子 path，父级 DefaultLayout 不计页面）
 * Mobile：JSON.parse 读 zw-insight-app/src/pages.json
 *
 * 基准自校验：PC 期望 104 页 / Mobile 期望 28 页（对齐 tests/frontend-test-case-matrix.md
 * 封板基准），偏离仅警告并在报告中显式列出，不静默丢弃。
 */
import * as fs from 'node:fs';
import * as path from 'node:path';
import type { FeatureGroup, PageManifestItem, Platform } from './types.js';

/** 仓库内相对路径常量 */
export const PC_ROUTER = 'zw-insight-web/src/router/index.ts';
export const MOBILE_PAGES = 'zw-insight-app/src/pages.json';
export const PC_VIEWS_ROOT = 'zw-insight-web/src/views';
export const MOBILE_PAGES_ROOT = 'zw-insight-app/src';

/** 基准页面数（frontend-test-case-matrix.md 封板口径，偏离仅警告） */
export const PC_BASELINE_COUNT = 104;
export const MOBILE_BASELINE_COUNT = 28;

/** 模块 → 功能分组映射（对齐 test-case-matrix A/B/C/D 分组惯例） */
const GROUP_BY_MODULE: Record<string, FeatureGroup> = {
  // A 核心主链
  project: 'A', contract: 'A', budget: 'A', tender: 'A',
  // B 支出域
  material: 'B', machine: 'B', labor: 'B', subcontract: 'B', purchase: 'B',
  // C 财务现场域
  finance: 'C', site: 'C', hr: 'C',
  // D 平台支撑域（其余全部）
};

/** 移动端 pages 首段 → 后端/视图模块名映射 */
const MOBILE_MODULE_MAP: Record<string, string> = {
  approval: 'workflow',
  'message-center': 'message',
  home: 'nav', workbench: 'nav', mine: 'nav',
  login: 'login',
};

/** 无业务语义、不计入账本的页面路径尾段（按需维护） */
const IGNORED_PAGE_PATHS = new Set<string>([]);

/** 路由对象解析结果 */
interface RouteNode {
  path: string;
  component?: string; // @/views/... 相对段
  title?: string;
  children?: RouteNode[];
}

/**
 * 跳过单/双引号字符串字面量的括号配对扫描：
 * 从 from 开始找与 openChar 配对的闭合字符位置，返回内容区间（不含两端定界符）。
 */
function findMatchingBracket(text: string, from: number, openChar: '{' | '['): number {
  const closeChar = openChar === '{' ? '}' : ']';
  let depth = 0;
  let inStr: string | null = null;
  for (let i = from; i < text.length; i++) {
    const ch = text[i];
    if (inStr) {
      if (ch === '\\') { i++; continue; }
      if (ch === inStr) inStr = null;
      continue;
    }
    if (ch === "'" || ch === '"') { inStr = ch; continue; }
    if (ch === openChar) depth++;
    else if (ch === closeChar) {
      depth--;
      if (depth === 0) return i;
    }
  }
  return -1;
}

/** 提取字符串中所有顶层大括号对象块（跳过字符串字面量内的括号） */
function splitObjects(text: string): string[] {
  const blocks: string[] = [];
  let i = 0;
  while (i < text.length) {
    const open = text.indexOf('{', i);
    if (open < 0) break;
    const close = findMatchingBracket(text, open, '{');
    if (close < 0) break;
    blocks.push(text.slice(open, close + 1));
    i = close + 1;
  }
  return blocks;
}

/** 从路由对象块提取属性 */
function parseRouteBlock(block: string): RouteNode {
  const node: RouteNode = { path: '' };
  let m = block.match(/path:\s*'([^']+)'/);
  if (m) node.path = m[1];
  m = block.match(/component:\s*\(\)\s*=>\s*import\('@\/views\/([^']+)'\)/);
  if (m) node.component = m[1];
  m = block.match(/title:\s*'([^']+)'/);
  if (m) node.title = m[1];

  const childrenIdx = block.search(/children:\s*\[/);
  if (childrenIdx >= 0) {
    const open = block.indexOf('[', childrenIdx);
    const close = findMatchingBracket(block, open, '[');
    if (close > open) {
      node.children = splitObjects(block.slice(open + 1, close))
        .map(parseRouteBlock)
        .filter((c) => c.path !== '');
    }
  }
  return node;
}

/** 拼接父子 path（'/system' + 'org' → '/system/org'；'/' + 'dashboard' → '/dashboard'） */
function joinPath(parent: string, child: string): string {
  if (parent === '/' || parent === '') return child.startsWith('/') ? child : `/${child}`;
  return `${parent.replace(/\/$/, '')}/${child.replace(/^\//, '')}`;
}

/** 递归展平路由树，产出页面清单条目（排除无 views component 的纯容器节点） */
function flattenRoutes(
  nodes: RouteNode[],
  parentPath: string,
  out: { pagePath: string; pageFile: string; title: string; module: string }[],
): void {
  for (const node of nodes) {
    const fullPath = joinPath(parentPath, node.path);
    if (node.component && !node.component.startsWith('../layouts')) {
      const module = fullPath.split('/')[1] || 'dashboard'; // 根路由子页归 dashboard
      out.push({
        pagePath: fullPath,
        pageFile: `${PC_VIEWS_ROOT}/${node.component}`,
        title: node.title ?? node.path,
        module,
      });
    }
    if (node.children?.length) {
      flattenRoutes(node.children, fullPath, out);
    }
  }
}

/** 扫描 PC 路由表 */
export function scanPcPages(rootPath: string): { pages: PageManifestItem[]; warnings: string[] } {
  const warnings: string[] = [];
  const routerFile = path.join(rootPath, PC_ROUTER);
  const source = fs.readFileSync(routerFile, 'utf-8');

  const constIdx = source.search(/const\s+constantRoutes\s*:\s*RouteRecordRaw\[\]\s*=\s*\[/);
  if (constIdx < 0) {
    throw new Error(`未在 ${PC_ROUTER} 中找到 constantRoutes 数组声明，前端路由结构可能已变更`);
  }
  // 定位赋值等号后的数组开括号（跳过类型注解 RouteRecordRaw[] 的括号，否则会误取 [] 区间）
  const eqIdx = source.indexOf('=', constIdx);
  const arrOpen = source.indexOf('[', eqIdx);
  const arrClose = findMatchingBracket(source, arrOpen, '[');
  const blocks = splitObjects(source.slice(arrOpen + 1, arrClose));
  const routes = blocks.map(parseRouteBlock).filter((n) => n.path !== '');

  const flat: { pagePath: string; pageFile: string; title: string; module: string }[] = [];
  flattenRoutes(routes, '', flat);

  const pages: PageManifestItem[] = flat
    .filter((p) => !IGNORED_PAGE_PATHS.has(p.pagePath))
    .map((p) => ({
      platform: 'pc' as Platform,
      pagePath: p.pagePath,
      pageFile: p.pageFile,
      title: p.title,
      module: p.module,
    }));

  // PC 基准自校验移至 CLI（按唯一组件去重后的功能页数对比，见 cli.ts；
  // router path 数会因 redirect 入口重复而偏多，不宜直接对比）
  return { pages, warnings };
}

/** 扫描移动端 pages.json */
export function scanMobilePages(rootPath: string): { pages: PageManifestItem[]; warnings: string[] } {
  const warnings: string[] = [];
  const raw = JSON.parse(fs.readFileSync(path.join(rootPath, MOBILE_PAGES), 'utf-8')) as {
    pages: { path: string; style?: { navigationBarTitleText?: string } }[];
  };

  const pages: PageManifestItem[] = raw.pages.map((p) => {
    const firstSeg = p.path.split('/')[1] ?? 'nav';
    const module = MOBILE_MODULE_MAP[firstSeg] ?? firstSeg;
    return {
      platform: 'mobile' as Platform,
      pagePath: p.path,
      pageFile: `${MOBILE_PAGES_ROOT}/${p.path}.vue`,
      title: p.style?.navigationBarTitleText ?? p.path,
      module,
    };
  });

  if (pages.length !== MOBILE_BASELINE_COUNT) {
    warnings.push(
      `移动端页面数 ${pages.length} 与基准 ${MOBILE_BASELINE_COUNT} 不符（pages.json 可能已增删，属正常演进）`,
    );
  }
  return { pages, warnings };
}

/** 模块 → 分组（未登记模块归 D 平台支撑域） */
export function groupOf(module: string): FeatureGroup {
  return GROUP_BY_MODULE[module] ?? 'D';
}

/**
 * 由清单条目生成 featureId：`${group}-${platform}-${componentSlug}`。
 * 以 vue 组件为功能单位：同一组件被多个路由 path 引用时天然去重
 * （如 /budget 与 /budget/list 均指向 budget/index.vue，属同一功能）。
 */
export function featureIdOf(item: PageManifestItem): string {
  return `${groupOf(item.module)}-${item.platform}-${componentSlugOf(item.pageFile)}`;
}

/** 从 pageFile 提取组件 slug（views/contract/form.vue → contract-form） */
export function componentSlugOf(pageFile: string): string {
  const m = pageFile.match(/(?:views|pages)\/(.+)\.vue$/);
  if (m) return m[1].split('/').filter(Boolean).join('-');
  return pageFile.replace(/\.vue$/, '').split('/').filter(Boolean).pop() ?? 'root';
}

/** 列出与 views 目录的清单差异（辅助人工核对，不阻塞扫描） */
export function diffWithViewsDir(rootPath: string, pcPages: PageManifestItem[]): string[] {
  const diffs: string[] = [];
  const viewsRoot = path.join(rootPath, PC_VIEWS_ROOT);
  const known = new Set(pcPages.map((p) => path.posix.normalize(p.pageFile)));

  const walk = (dir: string): void => {
    for (const ent of fs.readdirSync(dir, { withFileTypes: true })) {
      const full = path.join(dir, ent.name);
      if (ent.isDirectory()) walk(full);
      else if (ent.name.endsWith('.vue') && ent.name !== 'App.vue') {
        const rel = path.relative(rootPath, full).split(path.sep).join('/');
        if (!known.has(rel)) diffs.push(`views 有文件但无路由: ${rel}`);
      }
    }
  };
  if (fs.existsSync(viewsRoot)) walk(viewsRoot);
  return diffs;
}
