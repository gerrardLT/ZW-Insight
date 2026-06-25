/**
 * PC 前端 TypeScript API 扫描器
 *
 * 使用 TypeScript Compiler API 进行精确 AST 解析：
 * 1. 使用 ts.createSourceFile() 解析每个 API 文件
 * 2. 遍历 AST 节点，查找 request.get/post/put/delete 调用
 * 3. 提取路径字符串（包括模板字符串中的变量）
 * 4. 将模板变量转换为路径参数占位符
 */

import ts from 'typescript';
import { readFileSync, readdirSync, existsSync } from 'node:fs';
import { join } from 'node:path';
import type { IScanner } from '../interfaces.js';
import type { FrontendApiEntry, HttpMethod } from '../types.js';

/** PC 前端需要扫描的 16 个 API 文件 */
const PC_WEB_API_FILES = [
  'system.ts',
  'project.ts',
  'finance.ts',
  'site.ts',
  'material.ts',
  'machine.ts',
  'contract.ts',
  'budget.ts',
  'purchase.ts',
  'labor.ts',
  'subcontract.ts',
  'hr.ts',
  'tender.ts',
  'archive.ts',
  'dashboard.ts',
  'basedata.ts',
];

/** HTTP 方法名到枚举值的映射 */
const METHOD_MAP: Record<string, HttpMethod> = {
  get: 'GET',
  post: 'POST',
  put: 'PUT',
  delete: 'DELETE',
};

export class PcWebScanner implements IScanner<FrontendApiEntry> {
  /** PC 前端 API 目录相对路径 */
  private readonly apiRelativePath = 'zw-insight-web/src/api';

  /**
   * 扫描指定根路径下的 PC 前端 API 文件，返回结构化条目列表
   */
  async scan(rootPath: string): Promise<FrontendApiEntry[]> {
    const apiDir = join(rootPath, this.apiRelativePath);

    if (!existsSync(apiDir)) {
      console.warn(`[PcWebScanner] API 目录不存在: ${apiDir}`);
      return [];
    }

    const allFiles = readdirSync(apiDir).filter((f) => f.endsWith('.ts'));
    const targetFiles = allFiles.filter((f) => PC_WEB_API_FILES.includes(f));

    const entries: FrontendApiEntry[] = [];

    for (const fileName of targetFiles) {
      const filePath = join(apiDir, fileName);
      try {
        const content = readFileSync(filePath, 'utf-8');
        const fileEntries = this.parseFile(content, fileName, filePath);
        entries.push(...fileEntries);
      } catch (err) {
        console.warn(`[PcWebScanner] 文件读取失败，已跳过: ${filePath}`, err);
      }
    }

    return entries;
  }

  /**
   * 解析单个 TypeScript 文件，提取所有 API 调用
   */
  parseFile(
    content: string,
    fileName: string,
    filePath: string
  ): FrontendApiEntry[] {
    const sourceFile = ts.createSourceFile(
      fileName,
      content,
      ts.ScriptTarget.Latest,
      true,
      ts.ScriptKind.TS
    );

    const entries: FrontendApiEntry[] = [];
    this.visitNode(sourceFile, sourceFile, fileName, filePath, entries);
    return entries;
  }

  /**
   * 递归遍历 AST 节点，查找 request.get/post/put/delete 调用
   */
  private visitNode(
    node: ts.Node,
    sourceFile: ts.SourceFile,
    fileName: string,
    filePath: string,
    entries: FrontendApiEntry[]
  ): void {
    // 检查是否为 request.xxx() 调用表达式
    if (ts.isCallExpression(node)) {
      const entry = this.tryExtractApiCall(node, sourceFile, fileName, filePath);
      if (entry) {
        entries.push(entry);
      }
    }

    ts.forEachChild(node, (child) => {
      this.visitNode(child, sourceFile, fileName, filePath, entries);
    });
  }

  /**
   * 尝试从 CallExpression 中提取 API 调用信息
   * 匹配模式: request.get(...), request.post(...), request.put(...), request.delete(...)
   */
  private tryExtractApiCall(
    node: ts.CallExpression,
    sourceFile: ts.SourceFile,
    fileName: string,
    filePath: string
  ): FrontendApiEntry | null {
    const expr = node.expression;

    // 匹配 request.get/post/put/delete 模式
    if (!ts.isPropertyAccessExpression(expr)) {
      return null;
    }

    const obj = expr.expression;
    const method = expr.name.text;

    // 确认调用对象是 request
    if (!ts.isIdentifier(obj) || obj.text !== 'request') {
      return null;
    }

    // 确认方法名是 HTTP 方法
    const httpMethod = METHOD_MAP[method];
    if (!httpMethod) {
      return null;
    }

    // 提取第一个参数（路径）
    const firstArg = node.arguments[0];
    if (!firstArg) {
      return null;
    }

    const pathResult = this.extractPath(firstArg);
    if (!pathResult) {
      return null;
    }

    // 提取函数名（向上查找包含的函数声明或变量声明）
    const functionName = this.findEnclosingFunctionName(node, sourceFile);

    // 从路径中提取模块名（路径的第二段，/v1/ 之后的部分）
    const module = this.extractModuleFromPath(pathResult.path);

    // 获取行号
    const { line } = sourceFile.getLineAndCharacterOfPosition(node.getStart(sourceFile));

    return {
      source: 'pc-web',
      fileName,
      functionName: functionName || '<anonymous>',
      httpMethod,
      requestPath: pathResult.path,
      pathParams: pathResult.params,
      module,
      filePath,
      lineNumber: line + 1, // 转为 1-based
    };
  }

  /**
   * 从 AST 节点中提取路径字符串
   * 支持：普通字符串字面量、模板字符串
   */
  private extractPath(
    node: ts.Node
  ): { path: string; params: string[] } | null {
    // 普通字符串字面量: '/v1/finance/invoice-apply/page'
    if (ts.isStringLiteral(node)) {
      return { path: node.text, params: [] };
    }

    // 无替换模板字符串: `/v1/finance/invoice-apply/page`
    if (ts.isNoSubstitutionTemplateLiteral(node)) {
      return { path: node.text, params: [] };
    }

    // 模板字符串: `/v1/finance/invoice-apply/${id}`
    if (ts.isTemplateExpression(node)) {
      return this.normalizeTemplateLiteral(node);
    }

    return null;
  }

  /**
   * 将模板字符串转换为路径参数格式
   * `/v1/finance/invoice-apply/${id}` -> { path: '/v1/finance/invoice-apply/{id}', params: ['id'] }
   * `/v1/finance/${module}/${id}/submit` -> { path: '/v1/finance/{module}/{id}/submit', params: ['module', 'id'] }
   */
  normalizeTemplateLiteral(node: ts.TemplateExpression): {
    path: string;
    params: string[];
  } {
    const params: string[] = [];
    let path = node.head.text;

    for (const span of node.templateSpans) {
      // 提取变量名
      const paramName = this.extractParamName(span.expression);
      params.push(paramName);

      // 将 ${xxx} 替换为 {xxx}
      path += `{${paramName}}`;
      path += span.literal.text;
    }

    return { path, params };
  }

  /**
   * 从模板表达式节点中提取参数名
   * 支持简单标识符和属性访问表达式
   */
  private extractParamName(node: ts.Expression): string {
    // 简单标识符: ${id}
    if (ts.isIdentifier(node)) {
      return node.text;
    }

    // 属性访问: ${item.id}
    if (ts.isPropertyAccessExpression(node)) {
      return node.getText();
    }

    // 其他复杂表达式，使用源码文本
    return node.getText();
  }

  /**
   * 向上查找包含的函数声明或变量声明，提取函数名
   */
  private findEnclosingFunctionName(
    node: ts.Node,
    sourceFile: ts.SourceFile
  ): string | null {
    let current: ts.Node | undefined = node.parent;

    while (current) {
      // function declaration: export function getInvoiceApplyPage(...)
      if (ts.isFunctionDeclaration(current) && current.name) {
        return current.name.text;
      }

      // variable declaration with arrow function: export const getXxx = (...)  => ...
      if (ts.isVariableDeclaration(current) && ts.isIdentifier(current.name)) {
        return current.name.text;
      }

      // method declaration in class: getXxx() { ... }
      if (ts.isMethodDeclaration(current) && current.name) {
        return current.name.getText(sourceFile);
      }

      current = current.parent;
    }

    return null;
  }

  /**
   * 从请求路径中提取模块名
   * 规则：取路径中 /v1/ 之后的第一段作为模块名
   * 例如：/v1/finance/invoice-apply/page → finance
   *       /v1/sys/user/page → sys
   */
  extractModuleFromPath(path: string): string {
    const match = path.match(/^\/v1\/([^/]+)/);
    if (match) {
      return match[1];
    }

    // 如果路径不以 /v1/ 开头，尝试取第一个有效段
    const segments = path.split('/').filter(Boolean);
    if (segments.length >= 2) {
      return segments[1]; // 跳过版本号段
    }
    if (segments.length >= 1) {
      return segments[0];
    }

    return 'unknown';
  }
}
