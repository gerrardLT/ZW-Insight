/**
 * PathAuditor 单元测试
 *
 * 验证路径前缀规范校验、RESTful 命名风格校验、模块名一致性校验
 * Validates: Requirements 10.1, 10.2, 10.3, 10.4
 */

import { describe, it, expect } from 'vitest';
import { PathAuditor } from '../../src/auditors/path-auditor.js';
import type { BackendApiEntry, FrontendApiEntry } from '../../src/types.js';

function makeBackendEntry(overrides: Partial<BackendApiEntry> = {}): BackendApiEntry {
  return {
    module: 'finance',
    controllerClass: 'FinanceController',
    methodName: 'getInvoiceList',
    httpMethod: 'GET',
    fullPath: '/api/v1/finance/invoice-apply',
    filePath: 'src/finance/controller/FinanceController.java',
    lineNumber: 10,
    ...overrides,
  };
}

function makeFrontendEntry(overrides: Partial<FrontendApiEntry> = {}): FrontendApiEntry {
  return {
    source: 'pc-web',
    fileName: 'finance.ts',
    functionName: 'getInvoiceList',
    httpMethod: 'GET',
    requestPath: '/v1/finance/invoice-apply',
    pathParams: [],
    module: 'finance',
    filePath: 'src/api/finance.ts',
    lineNumber: 5,
    ...overrides,
  };
}

describe('PathAuditor', () => {
  const auditor = new PathAuditor();

  describe('validatePathPrefix', () => {
    it('应通过 /api/v1/{module}/... 格式的路径', () => {
      const entry = makeBackendEntry({ fullPath: '/api/v1/finance/invoice-apply' });
      expect(auditor.validatePathPrefix(entry)).toBeNull();
    });

    it('应通过 /v1/{module}/... 格式的路径', () => {
      const entry = makeBackendEntry({ fullPath: '/v1/finance/invoice-apply' });
      expect(auditor.validatePathPrefix(entry)).toBeNull();
    });

    it('应通过 /api/v1/{module} 格式的路径（无尾部路径）', () => {
      const entry = makeBackendEntry({ fullPath: '/api/v1/finance' });
      expect(auditor.validatePathPrefix(entry)).toBeNull();
    });

    it('应通过含连字符的模块名', () => {
      const entry = makeBackendEntry({ fullPath: '/api/v1/base-data/list' });
      expect(auditor.validatePathPrefix(entry)).toBeNull();
    });

    it('应拒绝缺少版本号的路径', () => {
      const entry = makeBackendEntry({ fullPath: '/api/finance/invoice-apply' });
      const result = auditor.validatePathPrefix(entry);
      expect(result).not.toBeNull();
      expect(result!.type).toBe('PATH_NAMING_VIOLATION');
      expect(result!.severity).toBe('Minor');
    });

    it('应拒绝模块名含大写字母的路径', () => {
      const entry = makeBackendEntry({ fullPath: '/api/v1/Finance/invoice-apply' });
      const result = auditor.validatePathPrefix(entry);
      expect(result).not.toBeNull();
      expect(result!.type).toBe('PATH_NAMING_VIOLATION');
    });

    it('应拒绝模块名含下划线的路径', () => {
      const entry = makeBackendEntry({ fullPath: '/api/v1/base_data/list' });
      const result = auditor.validatePathPrefix(entry);
      expect(result).not.toBeNull();
      expect(result!.type).toBe('PATH_NAMING_VIOLATION');
    });

    it('应拒绝无任何前缀的路径', () => {
      const entry = makeBackendEntry({ fullPath: '/finance/invoice-apply' });
      const result = auditor.validatePathPrefix(entry);
      expect(result).not.toBeNull();
      expect(result!.type).toBe('PATH_NAMING_VIOLATION');
    });

    it('应拒绝根路径', () => {
      const entry = makeBackendEntry({ fullPath: '/' });
      const result = auditor.validatePathPrefix(entry);
      expect(result).not.toBeNull();
      expect(result!.type).toBe('PATH_NAMING_VIOLATION');
    });
  });

  describe('validateRestfulNaming', () => {
    it('应通过 kebab-case 命名的路径', () => {
      const entry = makeBackendEntry({ fullPath: '/api/v1/finance/invoice-apply' });
      expect(auditor.validateRestfulNaming(entry)).toBeNull();
    });

    it('应通过多段 kebab-case 路径', () => {
      const entry = makeBackendEntry({ fullPath: '/api/v1/finance/invoice-apply/detail' });
      expect(auditor.validateRestfulNaming(entry)).toBeNull();
    });

    it('应跳过路径参数段 {id}', () => {
      const entry = makeBackendEntry({ fullPath: '/api/v1/finance/invoice-apply/{id}' });
      expect(auditor.validateRestfulNaming(entry)).toBeNull();
    });

    it('应拒绝含大写字母的资源段', () => {
      const entry = makeBackendEntry({ fullPath: '/api/v1/finance/invoiceApply' });
      const result = auditor.validateRestfulNaming(entry);
      expect(result).not.toBeNull();
      expect(result!.type).toBe('RESTFUL_NAMING_VIOLATION');
      expect(result!.severity).toBe('Minor');
      expect(result!.description).toContain('invoiceApply');
    });

    it('应拒绝含下划线的资源段', () => {
      const entry = makeBackendEntry({ fullPath: '/api/v1/finance/invoice_apply' });
      const result = auditor.validateRestfulNaming(entry);
      expect(result).not.toBeNull();
      expect(result!.type).toBe('RESTFUL_NAMING_VIOLATION');
      expect(result!.description).toContain('invoice_apply');
    });

    it('应跳过版本段和模块名段', () => {
      // v1 和 finance 都应被跳过，不参与检查
      const entry = makeBackendEntry({ fullPath: '/api/v1/finance/valid-resource' });
      expect(auditor.validateRestfulNaming(entry)).toBeNull();
    });

    it('应检测多个违规段', () => {
      const entry = makeBackendEntry({ fullPath: '/api/v1/finance/invoiceApply/getDetail' });
      const result = auditor.validateRestfulNaming(entry);
      expect(result).not.toBeNull();
      expect(result!.description).toContain('invoiceApply');
      expect(result!.description).toContain('getDetail');
    });

    it('应通过纯数字结尾的 kebab-case', () => {
      const entry = makeBackendEntry({ fullPath: '/api/v1/finance/invoice-v2' });
      expect(auditor.validateRestfulNaming(entry)).toBeNull();
    });
  });

  describe('validateModuleConsistency', () => {
    const backendModules = ['finance', 'site', 'material', 'machine', 'project'];

    it('应通过模块名与后端一致的路径', () => {
      const entry = makeFrontendEntry({ requestPath: '/v1/finance/invoice-apply' });
      expect(auditor.validateModuleConsistency(entry, backendModules)).toBeNull();
    });

    it('应检测前端路径中与后端不一致的模块名', () => {
      const entry = makeFrontendEntry({
        requestPath: '/v1/unknown-module/some-api',
        module: 'unknown',
      });
      const result = auditor.validateModuleConsistency(entry, backendModules);
      expect(result).not.toBeNull();
      expect(result!.type).toBe('PATH_NAMING_VIOLATION');
      expect(result!.description).toContain('unknown-module');
    });

    it('当路径格式不匹配时应跳过校验', () => {
      const entry = makeFrontendEntry({ requestPath: '/some/random/path' });
      expect(auditor.validateModuleConsistency(entry, backendModules)).toBeNull();
    });

    it('应通过含 /api 前缀的前端路径', () => {
      const entry = makeFrontendEntry({ requestPath: '/api/v1/material/list' });
      expect(auditor.validateModuleConsistency(entry, backendModules)).toBeNull();
    });
  });

  describe('audit', () => {
    it('应对所有条目执行前缀和命名校验', () => {
      const entries: BackendApiEntry[] = [
        makeBackendEntry({ fullPath: '/api/v1/finance/invoice-apply' }),
        makeBackendEntry({ fullPath: '/api/v1/finance/invoiceApply' }),
        makeBackendEntry({ fullPath: '/finance/invoice-apply' }),
      ];

      const results = auditor.audit(entries);
      // 第1条：通过
      // 第2条：命名违规（invoiceApply）
      // 第3条：前缀违规（无 v1）+ 命名可能也有问题
      expect(results.length).toBeGreaterThanOrEqual(2);
      expect(results.some((r) => r.type === 'RESTFUL_NAMING_VIOLATION')).toBe(true);
      expect(results.some((r) => r.type === 'PATH_NAMING_VIOLATION')).toBe(true);
    });

    it('空数组应返回空结果', () => {
      expect(auditor.audit([])).toEqual([]);
    });

    it('所有路径符合规范时应返回空结果', () => {
      const entries: BackendApiEntry[] = [
        makeBackendEntry({ fullPath: '/api/v1/finance/invoice-apply' }),
        makeBackendEntry({ fullPath: '/v1/site/daily-log' }),
        makeBackendEntry({ fullPath: '/api/v1/material/inbound/{id}' }),
      ];

      expect(auditor.audit(entries)).toEqual([]);
    });
  });
});
