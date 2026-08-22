/**
 * capability-scanner 测试：端点精确匹配（matchControllersByEndpoints）
 *
 * 覆盖：
 *  - 正常路径：api 文件端点 → Controller basePath 最长前缀匹配（含 {id} 通配段）
 *  - 异常路径：页面未导入 api / api 文件不存在 → 返回 null（调用方回退 slug 匹配）
 */
import { mkdtempSync, mkdirSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { afterAll, beforeAll, describe, expect, it } from 'vitest';

import { matchControllersByEndpoints, type ControllerSignals } from '../src/capability-scanner.js';

function ctrl(module: string, className: string, basePath: string): ControllerSignals {
  return { module, className, fileRel: `${className}.java`, basePath, signals: new Map(), ruleHits: {} };
}

describe('matchControllersByEndpoints', () => {
  let root: string;

  beforeAll(() => {
    root = mkdtempSync(join(tmpdir(), 'fl-endpoint-'));
    mkdirSync(join(root, 'zw-insight-web/src/api'), { recursive: true });
    writeFileSync(
      join(root, 'zw-insight-web/src/api/budget.ts'),
      [
        "export function getBudgetPage(p: any) { return request.get<R<PageResult<Budget>>>('/v1/budget/page', { params: p }) }",
        'export function submitBudgetChange(id: number) { return request.post<R<void>>(`/v1/budget/change/${id}/submit`) }',
        'export function listContracts() { return request.get(`/v1/contracts/${contractId}/boq`) }',
      ].join('\n'),
      'utf-8',
    );
  });

  afterAll(() => rmSync(root, { recursive: true, force: true }));

  it('正常路径：按端点最长前缀匹配，{id} 段视为通配', () => {
    const controllers = [
      ctrl('budget', 'BudgetController', '/api/v1/budget'),
      ctrl('budget', 'BudgetChangeController', '/api/v1/budget/change'),
    ];
    const vue = "import { getBudgetPage, submitBudgetChange } from '@/api/budget'";
    const matched = matchControllersByEndpoints(root, vue, controllers);
    expect(matched).not.toBeNull();
    const names = matched!.map((c) => c.className).sort();
    // /v1/budget/page → BudgetController；/v1/budget/change/{id}/submit → BudgetChangeController（最长前缀优先）
    expect(names).toEqual(['BudgetChangeController', 'BudgetController']);
  });

  it('正常路径：路径变量段（{contractId}）在 basePath 中也能匹配', () => {
    const controllers = [ctrl('contract', 'BoqController', '/api/v1/contracts/{contractId}/boq')];
    const vue = "import { listContracts } from '@/api/budget'";
    const matched = matchControllersByEndpoints(root, vue, controllers);
    expect(matched!.map((c) => c.className)).toEqual(['BoqController']);
  });

  it('正常路径：多行 import（函数名跨行）也能解析', () => {
    const controllers = [ctrl('budget', 'BudgetController', '/api/v1/budget')];
    const vue = "import {\n  getBudgetPage,\n  createBudget\n} from '@/api/budget'";
    const matched = matchControllersByEndpoints(root, vue, controllers);
    expect(matched!.map((c) => c.className)).toEqual(['BudgetController']);
  });

  it('异常路径：页面未导入任何 api 模块 → 返回 null', () => {
    const vue = '<template><div>static</div></template>';
    expect(matchControllersByEndpoints(root, vue, [])).toBeNull();
  });

  it('异常路径：导入的 api 文件不存在 → 返回 null', () => {
    const vue = "import { foo } from '@/api/not-exist'";
    expect(matchControllersByEndpoints(root, vue, [])).toBeNull();
  });
});
