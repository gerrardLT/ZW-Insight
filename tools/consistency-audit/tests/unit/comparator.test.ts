import { describe, it, expect } from 'vitest';
import { ConsistencyComparator } from '../../src/comparators/consistency-comparator.js';
import type { BackendApiEntry, FrontendApiEntry } from '../../src/types.js';

describe('ConsistencyComparator', () => {
  const comparator = new ConsistencyComparator();

  // ===================== normalizePath 测试 =====================

  describe('normalizePath', () => {
    it('应移除 /api 前缀', () => {
      expect(comparator.normalizePath('/api/v1/finance/invoice')).toBe('/v1/finance/invoice');
    });

    it('仅移除开头的 /api，不影响路径中其他部分', () => {
      expect(comparator.normalizePath('/v1/api-config/list')).toBe('/v1/api-config/list');
    });

    it('处理 /api 本身', () => {
      expect(comparator.normalizePath('/api')).toBe('/');
    });

    it('不含 /api 前缀的路径保持不变', () => {
      expect(comparator.normalizePath('/v1/finance/invoice')).toBe('/v1/finance/invoice');
    });

    it('将 :param 格式统一为 {param}', () => {
      expect(comparator.normalizePath('/v1/finance/:id')).toBe('/v1/finance/{id}');
    });

    it('将 ${param} 格式统一为 {param}', () => {
      expect(comparator.normalizePath('/v1/finance/${id}')).toBe('/v1/finance/{id}');
    });

    it('已经是 {param} 格式的保持不变', () => {
      expect(comparator.normalizePath('/v1/finance/{id}')).toBe('/v1/finance/{id}');
    });

    it('移除尾部斜杠', () => {
      expect(comparator.normalizePath('/v1/finance/')).toBe('/v1/finance');
    });

    it('根路径保持不变', () => {
      expect(comparator.normalizePath('/')).toBe('/');
    });

    it('幂等性：normalizePath(normalizePath(P)) === normalizePath(P)', () => {
      const paths = [
        '/api/v1/finance/invoice',
        '/v1/site/:id/detail',
        '/api/v1/material/${batchId}/items',
        '/v1/archive/{docId}',
      ];
      for (const p of paths) {
        const once = comparator.normalizePath(p);
        const twice = comparator.normalizePath(once);
        expect(twice).toBe(once);
      }
    });

    it('前缀无关性：normalizePath("/api" + P) === normalizePath(P)', () => {
      const paths = [
        '/v1/finance/invoice',
        '/v1/site/list',
        '/v1/material/{id}',
      ];
      for (const p of paths) {
        expect(comparator.normalizePath('/api' + p)).toBe(comparator.normalizePath(p));
      }
    });
  });

  // ===================== pathsMatch 测试 =====================

  describe('pathsMatch', () => {
    it('相同路径应匹配', () => {
      expect(comparator.pathsMatch('/v1/finance/invoice', '/v1/finance/invoice')).toBe(true);
    });

    it('路径参数应作为通配符匹配', () => {
      expect(comparator.pathsMatch('/v1/finance/{id}', '/v1/finance/{invoiceId}')).toBe(true);
    });

    it('路径参数与固定路径匹配', () => {
      expect(comparator.pathsMatch('/v1/finance/{id}/detail', '/v1/finance/{invoiceId}/detail')).toBe(true);
    });

    it('不同长度路径不匹配', () => {
      expect(comparator.pathsMatch('/v1/finance', '/v1/finance/invoice')).toBe(false);
    });

    it('不同非参数段不匹配', () => {
      expect(comparator.pathsMatch('/v1/finance/invoice', '/v1/finance/payment')).toBe(false);
    });

    it('带 /api 前缀差异时应匹配', () => {
      expect(comparator.pathsMatch('/api/v1/finance/invoice', '/v1/finance/invoice')).toBe(true);
    });

    it('大小写不敏感匹配', () => {
      expect(comparator.pathsMatch('/v1/Finance/Invoice', '/v1/finance/invoice')).toBe(true);
    });

    it(':param 和 {param} 混合匹配', () => {
      expect(comparator.pathsMatch('/v1/finance/:id', '/v1/finance/{id}')).toBe(true);
    });
  });

  // ===================== compare 测试 =====================

  describe('compare', () => {
    const makeBackend = (
      overrides: Partial<BackendApiEntry> = {}
    ): BackendApiEntry => ({
      module: 'finance',
      controllerClass: 'FinanceController',
      methodName: 'getInvoice',
      httpMethod: 'GET',
      fullPath: '/api/v1/finance/invoice',
      filePath: 'src/finance/FinanceController.java',
      lineNumber: 10,
      ...overrides,
    });

    const makeFrontend = (
      overrides: Partial<FrontendApiEntry> = {}
    ): FrontendApiEntry => ({
      source: 'pc-web',
      fileName: 'finance.ts',
      functionName: 'getInvoice',
      httpMethod: 'GET',
      requestPath: '/v1/finance/invoice',
      pathParams: [],
      module: 'finance',
      filePath: 'src/api/finance.ts',
      lineNumber: 5,
      ...overrides,
    });

    it('前后端完全匹配时不产出不一致项', () => {
      const backend = [makeBackend()];
      const pcWeb = [makeFrontend()];
      const result = comparator.compare(backend, pcWeb, []);
      expect(result).toHaveLength(0);
    });

    it('前端路径在后端不存在 → FRONTEND_EXTRA_API', () => {
      const backend = [makeBackend()];
      const pcWeb = [
        makeFrontend(),
        makeFrontend({
          functionName: 'getPayment',
          requestPath: '/v1/finance/payment',
        }),
      ];
      const result = comparator.compare(backend, pcWeb, []);
      expect(result).toHaveLength(1);
      expect(result[0].type).toBe('FRONTEND_EXTRA_API');
      expect(result[0].severity).toBe('Critical');
    });

    it('后端路径在两个前端均不存在 → BACKEND_ORPHAN_API', () => {
      const backend = [
        makeBackend(),
        makeBackend({
          methodName: 'deleteInvoice',
          httpMethod: 'DELETE',
          fullPath: '/api/v1/finance/invoice/{id}',
        }),
      ];
      const pcWeb = [makeFrontend()];
      const result = comparator.compare(backend, pcWeb, []);
      expect(result).toHaveLength(1);
      expect(result[0].type).toBe('BACKEND_ORPHAN_API');
      expect(result[0].severity).toBe('Minor');
    });

    it('路径匹配但 HTTP 方法不一致 → HTTP_METHOD_MISMATCH', () => {
      const backend = [makeBackend({ httpMethod: 'POST' })];
      const pcWeb = [makeFrontend({ httpMethod: 'GET' })];
      const result = comparator.compare(backend, pcWeb, []);
      expect(result).toHaveLength(1);
      expect(result[0].type).toBe('HTTP_METHOD_MISMATCH');
      expect(result[0].severity).toBe('Major');
    });

    it('移动端接口也能正确比对', () => {
      const backend = [makeBackend()];
      const mobile = [
        makeFrontend({
          source: 'mobile',
          fileName: 'common.ts',
          requestPath: '/v1/finance/not-exist',
        }),
      ];
      const result = comparator.compare(backend, [], mobile);
      expect(result).toHaveLength(2); // 1 FRONTEND_EXTRA + 1 BACKEND_ORPHAN
      const types = result.map((r) => r.type);
      expect(types).toContain('FRONTEND_EXTRA_API');
      expect(types).toContain('BACKEND_ORPHAN_API');
    });

    it('同一后端被 PC 和移动端共同匹配时不算孤立', () => {
      const backend = [makeBackend()];
      const pcWeb = [makeFrontend()];
      const mobile = [
        makeFrontend({ source: 'mobile', fileName: 'common.ts' }),
      ];
      const result = comparator.compare(backend, pcWeb, mobile);
      expect(result).toHaveLength(0);
    });

    it('HTTP 方法不匹配时后端不计为孤立接口', () => {
      const backend = [makeBackend({ httpMethod: 'POST' })];
      const pcWeb = [makeFrontend({ httpMethod: 'GET' })];
      const result = comparator.compare(backend, pcWeb, []);
      // 应只有 HTTP_METHOD_MISMATCH，没有 BACKEND_ORPHAN_API
      expect(result).toHaveLength(1);
      expect(result[0].type).toBe('HTTP_METHOD_MISMATCH');
    });

    it('带路径参数的匹配应正常工作', () => {
      const backend = [
        makeBackend({ fullPath: '/api/v1/finance/invoice/{id}' }),
      ];
      const pcWeb = [
        makeFrontend({ requestPath: '/v1/finance/invoice/{invoiceId}' }),
      ];
      const result = comparator.compare(backend, pcWeb, []);
      expect(result).toHaveLength(0);
    });
  });
});
