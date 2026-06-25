/**
 * CoverageAuditor 单元测试
 *
 * 验证功能覆盖率审核器的核心逻辑：
 * - 功能缺失检测 (FEATURE_MISSING)
 * - 超范围实现检测 (FEATURE_EXTRA)
 * - 平台覆盖匹配逻辑
 *
 * Requirements: 5.1, 5.2, 5.3, 5.4, 5.5
 */

import { describe, it, expect } from 'vitest';
import { CoverageAuditor } from '@/auditors/coverage-auditor.js';
import type {
  BackendApiEntry,
  FrontendApiEntry,
  FeatureMapping,
} from '@/types.js';

// ===================== 测试辅助函数 =====================

function createBackendEntry(overrides: Partial<BackendApiEntry> = {}): BackendApiEntry {
  return {
    module: 'finance',
    controllerClass: 'FinanceController',
    methodName: 'list',
    httpMethod: 'GET',
    fullPath: '/api/v1/finance/invoice-apply',
    filePath: '/server/finance/FinanceController.java',
    lineNumber: 10,
    ...overrides,
  };
}

function createFrontendEntry(overrides: Partial<FrontendApiEntry> = {}): FrontendApiEntry {
  return {
    source: 'pc-web',
    fileName: 'finance.ts',
    functionName: 'getInvoiceApplyList',
    httpMethod: 'GET',
    requestPath: '/v1/finance/invoice-apply',
    pathParams: [],
    module: 'finance',
    filePath: '/web/src/api/finance.ts',
    lineNumber: 5,
    ...overrides,
  };
}

function createFeatureMapping(overrides: Partial<FeatureMapping> = {}): FeatureMapping {
  return {
    featureId: 'BIZ-2.5-01',
    featureName: '开票申请',
    category: '财务管理',
    backendModule: 'finance',
    pcRequired: true,
    mobileRequired: true,
    expectedApiPatterns: ['/v1/finance/invoice-apply'],
    ...overrides,
  };
}

// ===================== 测试用例 =====================

describe('CoverageAuditor', () => {
  describe('功能缺失检测 - PC端', () => {
    it('pcRequired=true 且 PC 前端有匹配时，不产出 FEATURE_MISSING', () => {
      const mappings: FeatureMapping[] = [
        createFeatureMapping({
          pcRequired: true,
          mobileRequired: false,
          expectedApiPatterns: ['/v1/finance/invoice-apply'],
        }),
      ];

      const pcWeb: FrontendApiEntry[] = [
        createFrontendEntry({
          requestPath: '/v1/finance/invoice-apply',
        }),
      ];

      const auditor = new CoverageAuditor(mappings);
      const result = auditor.audit([], pcWeb, []);

      const missingItems = result.filter((r) => r.type === 'FEATURE_MISSING');
      expect(missingItems).toHaveLength(0);
    });

    it('pcRequired=true 且 PC 前端无匹配时，产出 FEATURE_MISSING (Critical)', () => {
      const mappings: FeatureMapping[] = [
        createFeatureMapping({
          pcRequired: true,
          mobileRequired: false,
          expectedApiPatterns: ['/v1/finance/invoice-apply'],
        }),
      ];

      const pcWeb: FrontendApiEntry[] = [
        createFrontendEntry({
          requestPath: '/v1/finance/other-path',
        }),
      ];

      const auditor = new CoverageAuditor(mappings);
      const result = auditor.audit([], pcWeb, []);

      const missingItems = result.filter((r) => r.type === 'FEATURE_MISSING');
      expect(missingItems).toHaveLength(1);
      expect(missingItems[0].severity).toBe('Critical');
      expect(missingItems[0].module).toBe('finance');
      expect(missingItems[0].description).toContain('PC端功能缺失');
      expect(missingItems[0].description).toContain('开票申请');
    });

    it('pcRequired=false 时即使 PC 前端无匹配也不产出缺失项', () => {
      const mappings: FeatureMapping[] = [
        createFeatureMapping({
          pcRequired: false,
          mobileRequired: false,
          expectedApiPatterns: ['/v1/finance/invoice-apply'],
        }),
      ];

      const auditor = new CoverageAuditor(mappings);
      const result = auditor.audit([], [], []);

      const missingItems = result.filter((r) => r.type === 'FEATURE_MISSING');
      expect(missingItems).toHaveLength(0);
    });
  });

  describe('功能缺失检测 - 移动端', () => {
    it('mobileRequired=true 且移动端有匹配时，不产出 FEATURE_MISSING', () => {
      const mappings: FeatureMapping[] = [
        createFeatureMapping({
          pcRequired: false,
          mobileRequired: true,
          expectedApiPatterns: ['/v1/finance/invoice-apply'],
        }),
      ];

      const mobile: FrontendApiEntry[] = [
        createFrontendEntry({
          source: 'mobile',
          requestPath: '/v1/finance/invoice-apply',
        }),
      ];

      const auditor = new CoverageAuditor(mappings);
      const result = auditor.audit([], [], mobile);

      const missingItems = result.filter((r) => r.type === 'FEATURE_MISSING');
      expect(missingItems).toHaveLength(0);
    });

    it('mobileRequired=true 且移动端无匹配时，产出 FEATURE_MISSING (Critical)', () => {
      const mappings: FeatureMapping[] = [
        createFeatureMapping({
          pcRequired: false,
          mobileRequired: true,
          expectedApiPatterns: ['/v1/finance/invoice-apply'],
        }),
      ];

      const mobile: FrontendApiEntry[] = [];

      const auditor = new CoverageAuditor(mappings);
      const result = auditor.audit([], [], mobile);

      const missingItems = result.filter((r) => r.type === 'FEATURE_MISSING');
      expect(missingItems).toHaveLength(1);
      expect(missingItems[0].severity).toBe('Critical');
      expect(missingItems[0].description).toContain('移动端功能缺失');
    });
  });

  describe('功能缺失检测 - 双端', () => {
    it('双端必须且双端都有匹配时，无缺失项', () => {
      const mappings: FeatureMapping[] = [
        createFeatureMapping({
          pcRequired: true,
          mobileRequired: true,
          expectedApiPatterns: ['/v1/finance/invoice-apply'],
        }),
      ];

      const pcWeb: FrontendApiEntry[] = [
        createFrontendEntry({ requestPath: '/v1/finance/invoice-apply/list' }),
      ];
      const mobile: FrontendApiEntry[] = [
        createFrontendEntry({
          source: 'mobile',
          requestPath: '/v1/finance/invoice-apply',
        }),
      ];

      const auditor = new CoverageAuditor(mappings);
      const result = auditor.audit([], pcWeb, mobile);

      const missingItems = result.filter((r) => r.type === 'FEATURE_MISSING');
      expect(missingItems).toHaveLength(0);
    });

    it('双端必须但仅 PC 端有匹配时，产出移动端缺失', () => {
      const mappings: FeatureMapping[] = [
        createFeatureMapping({
          pcRequired: true,
          mobileRequired: true,
          expectedApiPatterns: ['/v1/finance/invoice-apply'],
        }),
      ];

      const pcWeb: FrontendApiEntry[] = [
        createFrontendEntry({ requestPath: '/v1/finance/invoice-apply' }),
      ];

      const auditor = new CoverageAuditor(mappings);
      const result = auditor.audit([], pcWeb, []);

      const missingItems = result.filter((r) => r.type === 'FEATURE_MISSING');
      expect(missingItems).toHaveLength(1);
      expect(missingItems[0].description).toContain('移动端功能缺失');
    });
  });

  describe('路径前缀匹配逻辑', () => {
    it('requestPath 完全等于 expectedApiPattern 时匹配', () => {
      const mappings: FeatureMapping[] = [
        createFeatureMapping({
          pcRequired: true,
          mobileRequired: false,
          expectedApiPatterns: ['/v1/finance/invoice-apply'],
        }),
      ];

      const pcWeb: FrontendApiEntry[] = [
        createFrontendEntry({ requestPath: '/v1/finance/invoice-apply' }),
      ];

      const auditor = new CoverageAuditor(mappings);
      const result = auditor.audit([], pcWeb, []);
      expect(result.filter((r) => r.type === 'FEATURE_MISSING')).toHaveLength(0);
    });

    it('requestPath 以 expectedApiPattern 为前缀（后跟/）时匹配', () => {
      const mappings: FeatureMapping[] = [
        createFeatureMapping({
          pcRequired: true,
          mobileRequired: false,
          expectedApiPatterns: ['/v1/finance/invoice-apply'],
        }),
      ];

      const pcWeb: FrontendApiEntry[] = [
        createFrontendEntry({ requestPath: '/v1/finance/invoice-apply/list' }),
      ];

      const auditor = new CoverageAuditor(mappings);
      const result = auditor.audit([], pcWeb, []);
      expect(result.filter((r) => r.type === 'FEATURE_MISSING')).toHaveLength(0);
    });

    it('requestPath 部分匹配但不在路径段边界时不匹配', () => {
      const mappings: FeatureMapping[] = [
        createFeatureMapping({
          pcRequired: true,
          mobileRequired: false,
          expectedApiPatterns: ['/v1/finance/invoice'],
        }),
      ];

      // "/v1/finance/invoice-apply" 以 "/v1/finance/invoice" 开头
      // 但下一个字符是 "-" 不是 "/"，所以不应该匹配
      const pcWeb: FrontendApiEntry[] = [
        createFrontendEntry({ requestPath: '/v1/finance/invoice-apply' }),
      ];

      const auditor = new CoverageAuditor(mappings);
      const result = auditor.audit([], pcWeb, []);
      expect(result.filter((r) => r.type === 'FEATURE_MISSING')).toHaveLength(1);
    });

    it('忽略 /api 前缀差异：requestPath 含 /api 但 pattern 不含', () => {
      const mappings: FeatureMapping[] = [
        createFeatureMapping({
          pcRequired: true,
          mobileRequired: false,
          expectedApiPatterns: ['/v1/finance/invoice-apply'],
        }),
      ];

      const pcWeb: FrontendApiEntry[] = [
        createFrontendEntry({ requestPath: '/api/v1/finance/invoice-apply' }),
      ];

      const auditor = new CoverageAuditor(mappings);
      const result = auditor.audit([], pcWeb, []);
      expect(result.filter((r) => r.type === 'FEATURE_MISSING')).toHaveLength(0);
    });

    it('多个 expectedApiPatterns 时任一匹配即视为覆盖', () => {
      const mappings: FeatureMapping[] = [
        createFeatureMapping({
          pcRequired: true,
          mobileRequired: false,
          expectedApiPatterns: ['/v1/finance/invoice-apply', '/v1/finance/invoice-list'],
        }),
      ];

      const pcWeb: FrontendApiEntry[] = [
        createFrontendEntry({ requestPath: '/v1/finance/invoice-list' }),
      ];

      const auditor = new CoverageAuditor(mappings);
      const result = auditor.audit([], pcWeb, []);
      expect(result.filter((r) => r.type === 'FEATURE_MISSING')).toHaveLength(0);
    });
  });

  describe('超范围实现检测', () => {
    it('前端 API 匹配到某个功能映射时不产出 FEATURE_EXTRA', () => {
      const mappings: FeatureMapping[] = [
        createFeatureMapping({
          expectedApiPatterns: ['/v1/finance/invoice-apply'],
        }),
      ];

      const pcWeb: FrontendApiEntry[] = [
        createFrontendEntry({ requestPath: '/v1/finance/invoice-apply/list' }),
      ];

      const auditor = new CoverageAuditor(mappings);
      const result = auditor.audit([], pcWeb, []);

      const extraItems = result.filter((r) => r.type === 'FEATURE_EXTRA');
      expect(extraItems).toHaveLength(0);
    });

    it('前端 API 未匹配任何功能映射时产出 FEATURE_EXTRA (Minor)', () => {
      const mappings: FeatureMapping[] = [
        createFeatureMapping({
          expectedApiPatterns: ['/v1/finance/invoice-apply'],
        }),
      ];

      const pcWeb: FrontendApiEntry[] = [
        createFrontendEntry({
          requestPath: '/v1/finance/unknown-feature',
          functionName: 'getUnknownFeature',
        }),
      ];

      const auditor = new CoverageAuditor(mappings);
      const result = auditor.audit([], pcWeb, []);

      const extraItems = result.filter((r) => r.type === 'FEATURE_EXTRA');
      expect(extraItems).toHaveLength(1);
      expect(extraItems[0].severity).toBe('Minor');
      expect(extraItems[0].description).toContain('PC端超范围实现');
      expect(extraItems[0].description).toContain('getUnknownFeature');
    });

    it('移动端超范围实现也能被正确检测', () => {
      const mappings: FeatureMapping[] = [
        createFeatureMapping({
          expectedApiPatterns: ['/v1/finance/invoice-apply'],
        }),
      ];

      const mobile: FrontendApiEntry[] = [
        createFrontendEntry({
          source: 'mobile',
          requestPath: '/v1/mobile/special-feature',
          functionName: 'getMobileSpecial',
        }),
      ];

      const auditor = new CoverageAuditor(mappings);
      const result = auditor.audit([], [], mobile);

      const extraItems = result.filter((r) => r.type === 'FEATURE_EXTRA');
      expect(extraItems).toHaveLength(1);
      expect(extraItems[0].description).toContain('移动端超范围实现');
    });
  });

  describe('getCoverageResults', () => {
    it('返回每个功能映射的覆盖检查结果', () => {
      const mappings: FeatureMapping[] = [
        createFeatureMapping({
          featureId: 'BIZ-2.5-01',
          featureName: '开票申请',
          pcRequired: true,
          mobileRequired: true,
          expectedApiPatterns: ['/v1/finance/invoice-apply'],
        }),
      ];

      const backend: BackendApiEntry[] = [
        createBackendEntry({ fullPath: '/api/v1/finance/invoice-apply' }),
      ];
      const pcWeb: FrontendApiEntry[] = [
        createFrontendEntry({ requestPath: '/v1/finance/invoice-apply' }),
      ];
      const mobile: FrontendApiEntry[] = [];

      const auditor = new CoverageAuditor(mappings);
      const results = auditor.getCoverageResults(backend, pcWeb, mobile);

      expect(results).toHaveLength(1);
      expect(results[0].featureId).toBe('BIZ-2.5-01');
      expect(results[0].backendImplemented).toBe(true);
      expect(results[0].pcWebImplemented).toBe(true);
      expect(results[0].mobileImplemented).toBe(false);
      expect(results[0].expectedPlatforms).toEqual(['pc', 'mobile']);
    });
  });
});
