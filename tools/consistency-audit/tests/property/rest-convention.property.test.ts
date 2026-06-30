/**
 * 判定准则正确性属性测试（Property 3：REST 约定收敛）
 *
 * 对统一 REST 约定判定函数 classifyRestOperation 做随机 (method, path) 验证：
 *  1. 断言「列表/详情/新增/更新/删除/批量删除/动作」的方法-路径映射与
 *     rest-convention.md 判定表逐字符一致；
 *  2. 断言路径规范化（去尾部斜杠）与方法规范化（大小写归一）后的相等判定正确；
 *  3. 断言违反约定的方法-路径组合收敛为「不符合约定」。
 *
 * 框架：vitest + fast-check
 *
 * Validates: Requirements 1.2, 1.3, 1.4, 1.5, 1.6, 1.7, 2.2, 3.2
 */

import { describe, it, expect } from 'vitest';
import fc from 'fast-check';
import {
  classifyRestOperation,
  normalizeMethod,
  normalizePath,
  BATCH_SEGMENT,
  SLUG_REGEX,
} from '../../src/auditors/rest-convention.js';

// ===================== 生成器（智能约束到输入空间） =====================

/** slug 生成器：严格满足 `^[a-z0-9-]{1,64}$`（module / entity / action 占位符约束） */
const slugChar = fc.constantFrom(...'abcdefghijklmnopqrstuvwxyz0123456789-'.split(''));
const slugArb = fc
  .array(slugChar, { minLength: 1, maxLength: 64 })
  .map((chars) => chars.join(''));

/** {id} 生成器：非空、不含 `/`、且不等于保留字面量 batch */
const idArb = fc
  .oneof(
    fc.integer({ min: 1, max: 1_000_000 }).map((n) => String(n)),
    fc
      .array(
        fc.constantFrom(...'abcdefghijklmnopqrstuvwxyzABCDEFGHIJ0123456789-_'.split('')),
        { minLength: 1, maxLength: 24 }
      )
      .map((chars) => chars.join('')),
    fc.constant('{id}')
  )
  .filter((s) => s.length > 0 && s !== BATCH_SEGMENT && !s.includes('/'));

/** action 生成器：slug 且不等于 batch（避免与批量删除字面量冲突） */
const actionArb = slugArb.filter((s) => s !== BATCH_SEGMENT);

/** 大小写混合的方法生成器，归一后应等于给定规范方法 */
function methodCaseArb(canonical: string) {
  return fc.mixedCase(fc.constant(canonical.toLowerCase()));
}

/** 路径构造：根据约定段拼接，可选 /api 前缀与尾部斜杠数量 */
function buildPath(
  parts: string[],
  opts: { api?: boolean; trailingSlashes?: number } = {}
): string {
  const base = `${opts.api ? '/api' : ''}/v1/${parts.join('/')}`;
  const slashes = '/'.repeat(opts.trailingSlashes ?? 0);
  return base + slashes;
}

const apiPrefixArb = fc.boolean();
const trailingArb = fc.nat({ max: 3 });

// ===================== Property 3：方法-路径映射逐字符一致 =====================

describe('Property 3: REST 约定收敛 — 方法-路径映射判定与 rest-convention.md 逐字一致', () => {
  it('列表/分页：根 GET `/v1/<module>/<entity>` → list（需求 1.2）', () => {
    fc.assert(
      fc.property(slugArb, slugArb, apiPrefixArb, (mod, ent, api) => {
        const path = buildPath([mod, ent], { api });
        expect(classifyRestOperation('GET', path)).toBe('list');
      })
    );
  });

  it('详情：GET `/v1/<module>/<entity>/{id}` → detail（需求 1.3）', () => {
    fc.assert(
      fc.property(slugArb, slugArb, idArb, apiPrefixArb, (mod, ent, id, api) => {
        const path = buildPath([mod, ent, id], { api });
        expect(classifyRestOperation('GET', path)).toBe('detail');
      })
    );
  });

  it('新增：POST 根 `/v1/<module>/<entity>` → create（需求 1.4）', () => {
    fc.assert(
      fc.property(slugArb, slugArb, apiPrefixArb, (mod, ent, api) => {
        const path = buildPath([mod, ent], { api });
        expect(classifyRestOperation('POST', path)).toBe('create');
      })
    );
  });

  it('更新：PUT `/v1/<module>/<entity>/{id}` → update（需求 1.5）', () => {
    fc.assert(
      fc.property(slugArb, slugArb, idArb, apiPrefixArb, (mod, ent, id, api) => {
        const path = buildPath([mod, ent, id], { api });
        expect(classifyRestOperation('PUT', path)).toBe('update');
      })
    );
  });

  it('单条删除：DELETE `/v1/<module>/<entity>/{id}` → delete（需求 1.6）', () => {
    fc.assert(
      fc.property(slugArb, slugArb, idArb, apiPrefixArb, (mod, ent, id, api) => {
        const path = buildPath([mod, ent, id], { api });
        expect(classifyRestOperation('DELETE', path)).toBe('delete');
      })
    );
  });

  it('批量删除：DELETE `/v1/<module>/<entity>/batch` → batchDelete（需求 1.7）', () => {
    fc.assert(
      fc.property(slugArb, slugArb, apiPrefixArb, (mod, ent, api) => {
        const path = buildPath([mod, ent, BATCH_SEGMENT], { api });
        expect(classifyRestOperation('DELETE', path)).toBe('batchDelete');
      })
    );
  });

  it('动作：POST `/v1/<module>/<entity>/{id}/<action>` → action（需求 1.9）', () => {
    fc.assert(
      fc.property(slugArb, slugArb, idArb, actionArb, apiPrefixArb, (mod, ent, id, action, api) => {
        const path = buildPath([mod, ent, id, action], { api });
        expect(classifyRestOperation('POST', path)).toBe('action');
      })
    );
  });
});

// ===================== 规范化相等判定 =====================

describe('规范化：去尾部斜杠 + 方法大小写归一后相等判定正确（需求 3.2）', () => {
  it('追加任意数量尾部斜杠不改变判定结果', () => {
    fc.assert(
      fc.property(slugArb, slugArb, trailingArb, (mod, ent, n) => {
        const canonical = buildPath([mod, ent]);
        const withSlashes = buildPath([mod, ent], { trailingSlashes: n });
        for (const method of ['GET', 'POST', 'PUT', 'DELETE']) {
          expect(classifyRestOperation(method, withSlashes)).toBe(
            classifyRestOperation(method, canonical)
          );
        }
      })
    );
  });

  it('带尾部斜杠的 {id} 路径与不带斜杠判定一致', () => {
    fc.assert(
      fc.property(slugArb, slugArb, idArb, trailingArb, (mod, ent, id, n) => {
        const canonical = buildPath([mod, ent, id]);
        const withSlashes = buildPath([mod, ent, id], { trailingSlashes: n });
        for (const method of ['GET', 'PUT', 'DELETE']) {
          expect(classifyRestOperation(method, withSlashes)).toBe(
            classifyRestOperation(method, canonical)
          );
        }
      })
    );
  });

  it('方法大小写混合归一后判定与规范大写一致', () => {
    const methodPairArb = fc
      .constantFrom('GET', 'POST', 'PUT', 'DELETE')
      .chain((canonical) =>
        methodCaseArb(canonical).map((mixed) => ({ canonical, mixed }))
      );
    fc.assert(
      fc.property(slugArb, slugArb, idArb, methodPairArb, (mod, ent, id, { canonical, mixed }) => {
        const path = buildPath([mod, ent, id]);
        expect(classifyRestOperation(mixed, path)).toBe(
          classifyRestOperation(canonical, path)
        );
      })
    );
  });

  it('normalizeMethod 幂等且与直接大写一致', () => {
    fc.assert(
      fc.property(fc.string(), (s) => {
        const once = normalizeMethod(s);
        expect(normalizeMethod(once)).toBe(once);
        expect(once).toBe(s.trim().toUpperCase());
      })
    );
  });

  it('normalizePath 去尾部斜杠后幂等', () => {
    fc.assert(
      fc.property(slugArb, slugArb, trailingArb, (mod, ent, n) => {
        const p = buildPath([mod, ent], { trailingSlashes: n });
        const once = normalizePath(p);
        expect(normalizePath(once)).toBe(once);
        expect(once.endsWith('/')).toBe(false);
      })
    );
  });
});

// ===================== 收敛性：违反约定 → nonCompliant =====================

describe('收敛性：违反统一约定的方法-路径组合判定为 nonCompliant', () => {
  it('根路径上的 PUT / DELETE 不构成任何 CRUD（→ nonCompliant）', () => {
    fc.assert(
      fc.property(slugArb, slugArb, fc.constantFrom('PUT', 'DELETE'), (mod, ent, method) => {
        const path = buildPath([mod, ent]);
        expect(classifyRestOperation(method, path)).toBe('nonCompliant');
      })
    );
  });

  it('`/{id}` 单尾段上的 POST 不构成动作（→ nonCompliant）', () => {
    fc.assert(
      fc.property(slugArb, slugArb, idArb, (mod, ent, id) => {
        const path = buildPath([mod, ent, id]);
        expect(classifyRestOperation('POST', path)).toBe('nonCompliant');
      })
    );
  });

  it('`/batch` 仅 DELETE 合法，其余方法 → nonCompliant', () => {
    fc.assert(
      fc.property(slugArb, slugArb, fc.constantFrom('GET', 'POST', 'PUT'), (mod, ent, method) => {
        const path = buildPath([mod, ent, BATCH_SEGMENT]);
        expect(classifyRestOperation(method, path)).toBe('nonCompliant');
      })
    );
  });

  it('`/{id}/<action>` 上非 POST 方法 → nonCompliant', () => {
    fc.assert(
      fc.property(
        slugArb,
        slugArb,
        idArb,
        actionArb,
        fc.constantFrom('GET', 'PUT', 'DELETE'),
        (mod, ent, id, action, method) => {
          const path = buildPath([mod, ent, id, action]);
          expect(classifyRestOperation(method, path)).toBe('nonCompliant');
        }
      )
    );
  });

  it('module/entity 含非法字符（大写）→ nonCompliant', () => {
    fc.assert(
      fc.property(
        slugArb,
        slugArb,
        fc.constantFrom('GET', 'POST', 'PUT', 'DELETE'),
        (mod, ent, method) => {
          const badModule = mod + 'A'; // 引入大写，违反 ^[a-z0-9-]{1,64}$
          fc.pre(!SLUG_REGEX.test(badModule));
          const path = buildPath([badModule, ent]);
          expect(classifyRestOperation(method, path)).toBe('nonCompliant');
        }
      )
    );
  });

  it('缺少版本段或层级过深 → nonCompliant', () => {
    fc.assert(
      fc.property(slugArb, slugArb, idArb, actionArb, (mod, ent, id, action) => {
        // 无 /v1 前缀
        expect(classifyRestOperation('GET', `/${mod}/${ent}`)).toBe('nonCompliant');
        // 层级过深：/{id}/<action>/<extra>
        const tooDeep = buildPath([mod, ent, id, action, 'extra']);
        expect(classifyRestOperation('POST', tooDeep)).toBe('nonCompliant');
      })
    );
  });
});
