import { describe, it, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { MobileScanner } from '../../src/scanners/mobile-scanner.js';

describe('MobileScanner', () => {
  const scanner = new MobileScanner();

  describe('parseFile - 解析 fixture mobile-common.ts', () => {
    const fixturesDir = resolve(__dirname, '../fixtures/typescript');
    const fileName = 'mobile-common.ts';
    const filePath = resolve(fixturesDir, fileName);
    const content = readFileSync(filePath, 'utf-8');
    const entries = scanner.parseFile(content, fileName, filePath);

    it('应解析出所有 request() 调用', () => {
      // fixture 中有 18 个 request 调用
      expect(entries.length).toBe(18);
    });

    it('所有条目 source 应为 mobile', () => {
      for (const entry of entries) {
        expect(entry.source).toBe('mobile');
      }
    });

    it('无 method 字段时默认为 GET', () => {
      const getTodoTasks = entries.find(
        (e) => e.functionName === 'getTodoTasks'
      );
      expect(getTodoTasks).toBeDefined();
      expect(getTodoTasks!.httpMethod).toBe('GET');
    });

    it('有 method: "POST" 时应正确提取', () => {
      const completeTask = entries.find(
        (e) => e.functionName === 'completeTask'
      );
      expect(completeTask).toBeDefined();
      expect(completeTask!.httpMethod).toBe('POST');
      expect(completeTask!.requestPath).toBe(
        '/v1/workflow/approval/complete'
      );
    });

    it('应正确提取函数名', () => {
      const functionNames = entries.map((e) => e.functionName);
      expect(functionNames).toContain('getTodoTasks');
      expect(functionNames).toContain('getDoneTasks');
      expect(functionNames).toContain('completeTask');
      expect(functionNames).toContain('rejectTask');
      expect(functionNames).toContain('getCompanyOverview');
      expect(functionNames).toContain('saveInvoiceApply');
      expect(functionNames).toContain('getProjectArchive');
    });

    it('模板字符串中的变量应转为路径参数', () => {
      const archiveEntry = entries.find(
        (e) => e.functionName === 'getProjectArchive'
      );
      expect(archiveEntry).toBeDefined();
      expect(archiveEntry!.requestPath).toBe(
        '/v1/archive/project/{projectId}'
      );
      expect(archiveEntry!.pathParams).toEqual(['projectId']);
      expect(archiveEntry!.httpMethod).toBe('GET');
    });

    it('应从路径中正确推断模块名', () => {
      const getTodoTasks = entries.find(
        (e) => e.functionName === 'getTodoTasks'
      );
      expect(getTodoTasks!.module).toBe('workflow');

      const saveInvoiceApply = entries.find(
        (e) => e.functionName === 'saveInvoiceApply'
      );
      expect(saveInvoiceApply!.module).toBe('finance');

      const getCompanyOverview = entries.find(
        (e) => e.functionName === 'getCompanyOverview'
      );
      expect(getCompanyOverview!.module).toBe('dashboard');

      const archiveEntry = entries.find(
        (e) => e.functionName === 'getProjectArchive'
      );
      expect(archiveEntry!.module).toBe('archive');
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
  });

  describe('parseFile - method: PUT/DELETE 支持', () => {
    const content = `
import request from '@/utils/request'

export function updateUser(data: any) {
  return request({ url: '/v1/user/profile', method: 'PUT', data })
}

export function deleteUser(id: number) {
  return request({ url: '/v1/user/delete', method: 'DELETE', data: { id } })
}
`;
    const entries = scanner.parseFile(content, 'test.ts', '/test.ts');

    it('应正确识别 PUT 方法', () => {
      const updateUser = entries.find((e) => e.functionName === 'updateUser');
      expect(updateUser).toBeDefined();
      expect(updateUser!.httpMethod).toBe('PUT');
    });

    it('应正确识别 DELETE 方法', () => {
      const deleteUser = entries.find((e) => e.functionName === 'deleteUser');
      expect(deleteUser).toBeDefined();
      expect(deleteUser!.httpMethod).toBe('DELETE');
    });
  });

  describe('extractModuleFromPath', () => {
    it('标准路径应提取正确模块名', () => {
      expect(scanner.extractModuleFromPath('/v1/finance/invoice-apply')).toBe(
        'finance'
      );
      expect(scanner.extractModuleFromPath('/v1/workflow/approval/todo')).toBe(
        'workflow'
      );
      expect(scanner.extractModuleFromPath('/v1/site/construction-log')).toBe(
        'site'
      );
      expect(scanner.extractModuleFromPath('/v1/material/inbound')).toBe(
        'material'
      );
    });

    it('带路径参数的路径应提取正确模块名', () => {
      expect(
        scanner.extractModuleFromPath('/v1/archive/project/{projectId}')
      ).toBe('archive');
    });

    it('无法识别的路径应返回 unknown', () => {
      expect(scanner.extractModuleFromPath('/unknown')).toBe('unknown');
      expect(scanner.extractModuleFromPath('')).toBe('unknown');
    });
  });

  describe('normalizeTemplateLiteral - 多变量模板字符串', () => {
    const templateContent = `
import request from '@/utils/request'

export function getDetail(moduleId: string, recordId: number) {
  return request({ url: \`/v1/project/\${moduleId}/detail/\${recordId}\` })
}
`;
    const entries = scanner.parseFile(templateContent, 'template.ts', '/template.ts');

    it('多个模板变量应全部转换', () => {
      expect(entries.length).toBe(1);
      expect(entries[0].requestPath).toBe(
        '/v1/project/{moduleId}/detail/{recordId}'
      );
      expect(entries[0].pathParams).toEqual(['moduleId', 'recordId']);
    });
  });

  describe('scan - 实际目录扫描', () => {
    it('应正确扫描项目根目录下的移动端 API 文件', async () => {
      const rootPath = resolve(__dirname, '../../../..');
      const entries = await scanner.scan(rootPath);

      // 项目中有 auth.ts 和 common.ts 两个文件
      expect(entries.length).toBeGreaterThan(0);

      // 验证所有条目的基本结构
      for (const entry of entries) {
        expect(entry.source).toBe('mobile');
        expect(entry.httpMethod).toMatch(/^(GET|POST|PUT|DELETE)$/);
        expect(entry.requestPath).toBeTruthy();
        expect(entry.module).toBeTruthy();
        expect(entry.functionName).toBeTruthy();
        expect(entry.fileName).toMatch(/\.ts$/);
        expect(entry.lineNumber).toBeGreaterThan(0);
      }
    });

    it('不存在的目录应返回空数组', async () => {
      const entries = await scanner.scan('/non-existent-path');
      expect(entries).toEqual([]);
    });
  });
});
