import { describe, it, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { PcWebScanner } from '../../src/scanners/pc-web-scanner.js';

describe('PcWebScanner', () => {
  const scanner = new PcWebScanner();

  describe('parseFile - 解析 fixture pc-finance.ts', () => {
    const fixturesDir = resolve(__dirname, '../fixtures/typescript');
    const fileName = 'pc-finance.ts';
    const filePath = resolve(fixturesDir, fileName);
    const content = readFileSync(filePath, 'utf-8');
    const entries = scanner.parseFile(content, fileName, filePath);

    it('应解析出所有 request.get/post/put/delete 调用', () => {
      // pc-finance.ts 中有 19 个 API 调用
      expect(entries.length).toBe(19);
    });

    it('所有条目 source 应为 pc-web', () => {
      for (const entry of entries) {
        expect(entry.source).toBe('pc-web');
      }
    });

    it('应正确识别 GET 方法', () => {
      const getPage = entries.find(
        (e) => e.functionName === 'getInvoiceApplyPage'
      );
      expect(getPage).toBeDefined();
      expect(getPage!.httpMethod).toBe('GET');
      expect(getPage!.requestPath).toBe('/v1/finance/invoice-apply/page');
    });

    it('应正确识别 POST 方法', () => {
      const create = entries.find(
        (e) => e.functionName === 'createInvoiceApply'
      );
      expect(create).toBeDefined();
      expect(create!.httpMethod).toBe('POST');
      expect(create!.requestPath).toBe('/v1/finance/invoice-apply');
    });

    it('应正确识别 PUT 方法', () => {
      const update = entries.find(
        (e) => e.functionName === 'updateInvoiceApply'
      );
      expect(update).toBeDefined();
      expect(update!.httpMethod).toBe('PUT');
      expect(update!.requestPath).toBe('/v1/finance/invoice-apply');
    });

    it('应正确识别 DELETE 方法', () => {
      const del = entries.find(
        (e) => e.functionName === 'deleteInvoiceApply'
      );
      expect(del).toBeDefined();
      expect(del!.httpMethod).toBe('DELETE');
      expect(del!.requestPath).toBe('/v1/finance/invoice-apply/{id}');
      expect(del!.pathParams).toEqual(['id']);
    });

    it('模板字符串中的变量应转为路径参数', () => {
      const detail = entries.find(
        (e) => e.functionName === 'getInvoiceApplyDetail'
      );
      expect(detail).toBeDefined();
      expect(detail!.requestPath).toBe('/v1/finance/invoice-apply/{id}');
      expect(detail!.pathParams).toEqual(['id']);
      expect(detail!.httpMethod).toBe('GET');
    });

    it('带多段路径参数的模板字符串应正确转换', () => {
      const submit = entries.find(
        (e) => e.functionName === 'submitInvoiceApply'
      );
      expect(submit).toBeDefined();
      expect(submit!.requestPath).toBe('/v1/finance/invoice-apply/{id}/submit');
      expect(submit!.pathParams).toEqual(['id']);
      expect(submit!.httpMethod).toBe('PUT');
    });

    it('应正确提取函数名', () => {
      const functionNames = entries.map((e) => e.functionName);
      expect(functionNames).toContain('getInvoiceApplyPage');
      expect(functionNames).toContain('getInvoiceApplyDetail');
      expect(functionNames).toContain('createInvoiceApply');
      expect(functionNames).toContain('updateInvoiceApply');
      expect(functionNames).toContain('deleteInvoiceApply');
      expect(functionNames).toContain('submitInvoiceApply');
      expect(functionNames).toContain('getPaymentReceivedPage');
      expect(functionNames).toContain('getPaymentApplyPage');
      expect(functionNames).toContain('getReimbursementPage');
      expect(functionNames).toContain('createReimbursement');
    });

    it('应从路径中正确推断模块名', () => {
      for (const entry of entries) {
        expect(entry.module).toBe('finance');
      }
    });

    it('fileName 应正确设置', () => {
      for (const entry of entries) {
        expect(entry.fileName).toBe(fileName);
      }
    });

    it('lineNumber 应为正整数', () => {
      for (const entry of entries) {
        expect(entry.lineNumber).toBeGreaterThan(0);
      }
    });

    it('pathParams 对于普通字符串路径应为空数组', () => {
      const getPage = entries.find(
        (e) => e.functionName === 'getInvoiceApplyPage'
      );
      expect(getPage!.pathParams).toEqual([]);
    });
  });

  describe('parseFile - 箭头函数和常量导出', () => {
    const content = `
import request from '@/utils/request'

export const getUserList = (params: any) => {
  return request.get('/v1/system/user/list', { params })
}

export const createUser = (data: any) => request.post('/v1/system/user', data)
`;
    const entries = scanner.parseFile(content, 'arrow.ts', '/arrow.ts');

    it('应正确识别箭头函数中的 API 调用', () => {
      expect(entries.length).toBe(2);
    });

    it('应正确提取箭头函数名', () => {
      const getUserList = entries.find((e) => e.functionName === 'getUserList');
      expect(getUserList).toBeDefined();
      expect(getUserList!.httpMethod).toBe('GET');
      expect(getUserList!.requestPath).toBe('/v1/system/user/list');

      const createUser = entries.find((e) => e.functionName === 'createUser');
      expect(createUser).toBeDefined();
      expect(createUser!.httpMethod).toBe('POST');
      expect(createUser!.requestPath).toBe('/v1/system/user');
    });
  });

  describe('parseFile - 无 API 调用文件', () => {
    const content = `
import { ref } from 'vue'

export function useCounter() {
  const count = ref(0)
  return { count }
}
`;
    const entries = scanner.parseFile(content, 'empty.ts', '/empty.ts');

    it('无 request 调用应返回空数组', () => {
      expect(entries).toEqual([]);
    });
  });

  describe('parseFile - request 非 HTTP 方法调用应忽略', () => {
    const content = `
import request from '@/utils/request'

// 非标准方法，应忽略
export function customMethod() {
  return request.interceptors.request.use(() => {})
}

// 标准方法，应识别
export function getUser() {
  return request.get('/v1/system/user')
}
`;
    const entries = scanner.parseFile(content, 'mixed.ts', '/mixed.ts');

    it('应只识别标准 HTTP 方法调用', () => {
      expect(entries.length).toBe(1);
      expect(entries[0].functionName).toBe('getUser');
      expect(entries[0].httpMethod).toBe('GET');
    });
  });

  describe('extractModuleFromPath', () => {
    it('标准路径应提取正确模块名', () => {
      expect(scanner.extractModuleFromPath('/v1/finance/invoice-apply')).toBe('finance');
      expect(scanner.extractModuleFromPath('/v1/system/user/page')).toBe('system');
      expect(scanner.extractModuleFromPath('/v1/site/construction-log')).toBe('site');
      expect(scanner.extractModuleFromPath('/v1/material/inbound')).toBe('material');
    });

    it('带路径参数的路径应提取正确模块名', () => {
      expect(scanner.extractModuleFromPath('/v1/finance/invoice-apply/{id}')).toBe('finance');
    });

    it('无法识别的路径应返回 unknown', () => {
      expect(scanner.extractModuleFromPath('')).toBe('unknown');
    });
  });

  describe('normalizeTemplateLiteral - 多变量模板字符串', () => {
    const content = `
import request from '@/utils/request'

export function getModuleDetail(moduleId: string, recordId: number) {
  return request.get(\`/v1/project/\${moduleId}/detail/\${recordId}\`)
}
`;
    const entries = scanner.parseFile(content, 'multi-param.ts', '/multi-param.ts');

    it('多个模板变量应全部转换', () => {
      expect(entries.length).toBe(1);
      expect(entries[0].requestPath).toBe('/v1/project/{moduleId}/detail/{recordId}');
      expect(entries[0].pathParams).toEqual(['moduleId', 'recordId']);
    });
  });

  describe('scan - 目录扫描', () => {
    it('不存在的目录应返回空数组', async () => {
      const entries = await scanner.scan('/non-existent-path');
      expect(entries).toEqual([]);
    });

    it('应正确扫描项目根目录下的 PC 前端 API 文件', async () => {
      const rootPath = resolve(__dirname, '../../../..');
      const entries = await scanner.scan(rootPath);

      // 验证所有条目的基本结构
      for (const entry of entries) {
        expect(entry.source).toBe('pc-web');
        expect(entry.httpMethod).toMatch(/^(GET|POST|PUT|DELETE)$/);
        expect(entry.requestPath).toBeTruthy();
        expect(entry.module).toBeTruthy();
        expect(entry.functionName).toBeTruthy();
        expect(entry.fileName).toMatch(/\.ts$/);
        expect(entry.lineNumber).toBeGreaterThan(0);
      }
    });
  });
});
