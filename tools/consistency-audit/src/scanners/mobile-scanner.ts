/**
 * 移动端 uni-app API 扫描器
 *
 * 扫描 zw-insight-app/src/api/ 目录下的 TypeScript 文件，
 * 提取 request({ url, method }) 调用模式中的 API 信息。
 *
 * 使用 TypeScript Compiler API 进行精确 AST 解析：
 * 1. 识别 request() 函数调用（参数为对象字面量）
 * 2. 提取对象字面量中的 url 和 method 属性
 * 3. method 缺省时默认为 GET
 * 4. 模板字符串 ${xxx} 规范化为 {xxx}
 */

import * as ts from 'typescript';
import * as fs from 'node:fs';
import * as path from 'node:path';
import type { IScanner } from '../interfaces.js';
import type { FrontendApiEntry, HttpMethod } from '../types.js';

export class MobileScanner implements IScanner<FrontendApiEntry> {
  /** 默认扫描的 API 目录（相对于项目根路径） */
  private readonly apiDir = 'zw-insight-app/src/api';

  /**
   * 扫描指定根路径下移动端 API 文件，返回结构化条目列表
   */
  async scan(rootPath: string): Promise<FrontendApiEntry[]> {
    const apiDirPath = path.join(rootPath, this.apiDir);

    if (!fs.existsSync(apiDirPath)) {
      return [];
    }

    const files = fs.readdirSync(apiDirPath).filter((f) => f.endsWith('.ts'));
    const entries: FrontendApiEntry[] = [];

    for (const fileName of files) {
      const filePath = path.join(apiDirPath, fileName);
      const content = fs.readFileSync(filePath, 'utf-8');
      const fileEntries = this.parseFile(content, fileName, filePath);
      entries.push(...fileEntries);
    }

    return entries;
  }

  /**
   * 解析单个 TypeScript 文件，提取所有 request() 调用
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
      true
    );

    const entries: FrontendApiEntry[] = [];
    this.visitNode(sourceFile, sourceFile, entries, fileName, filePath);
    return entries;
  }

  /**
   * 递归遍历 AST 节点，查找 request() 调用
   */
  private visitNode(
    node: ts.Node,
    sourceFile: ts.SourceFile,
    entries: FrontendApiEntry[],
    fileName: string,
    filePath: string
  ): void {
    if (ts.isCallExpression(node)) {
      const entry = this.tryExtractRequestCall(
        node,
        sourceFile,
        fileName,
        filePath
      );
      if (entry) {
        entries.push(entry);
      }
    }

    ts.forEachChild(node, (child) =>
      this.visitNode(child, sourceFile, entries, fileName, filePath)
    );
  }

  /**
   * 尝试从 CallExpression 中提取 request() 调用信息
   * 仅识别 request({ url: '...', method: '...' }) 形式
   */
  private tryExtractRequestCall(
    callExpr: ts.CallExpression,
    sourceFile: ts.SourceFile,
    fileName: string,
    filePath: string
  ): FrontendApiEntry | null {
    // 验证是 request(...) 调用（不是 request.get 等属性调用）
    if (!ts.isIdentifier(callExpr.expression)) {
      return null;
    }
    if (callExpr.expression.text !== 'request') {
      return null;
    }

    // 参数应为一个对象字面量
    if (callExpr.arguments.length === 0) {
      return null;
    }
    const arg = callExpr.arguments[0];
    if (!ts.isObjectLiteralExpression(arg)) {
      return null;
    }

    // 从对象字面量中提取 url 和 method
    const urlResult = this.extractUrlProperty(arg, sourceFile);
    if (!urlResult) {
      return null;
    }

    const httpMethod = this.extractMethodProperty(arg, sourceFile);
    const functionName = this.findEnclosingFunctionName(callExpr, sourceFile);
    const module = this.extractModuleFromPath(urlResult.path);
    const lineNumber =
      sourceFile.getLineAndCharacterOfPosition(callExpr.getStart(sourceFile))
        .line + 1;

    return {
      source: 'mobile',
      fileName,
      functionName,
      httpMethod,
      requestPath: urlResult.path,
      pathParams: urlResult.params,
      module,
      filePath,
      lineNumber,
    };
  }

  /**
   * 提取对象字面量中的 url 属性值
   * 支持普通字符串和模板字符串
   */
  private extractUrlProperty(
    obj: ts.ObjectLiteralExpression,
    sourceFile: ts.SourceFile
  ): { path: string; params: string[] } | null {
    for (const prop of obj.properties) {
      if (!ts.isPropertyAssignment(prop)) continue;

      const propName = this.getPropertyName(prop, sourceFile);
      if (propName !== 'url') continue;

      return this.extractPathFromExpression(prop.initializer, sourceFile);
    }
    return null;
  }

  /**
   * 提取对象字面量中的 method 属性值
   * 如果不存在 method 属性，默认返回 'GET'
   */
  private extractMethodProperty(
    obj: ts.ObjectLiteralExpression,
    sourceFile: ts.SourceFile
  ): HttpMethod {
    for (const prop of obj.properties) {
      if (!ts.isPropertyAssignment(prop)) continue;

      const propName = this.getPropertyName(prop, sourceFile);
      if (propName !== 'method') continue;

      if (ts.isStringLiteral(prop.initializer)) {
        const method = prop.initializer.text.toUpperCase();
        if (
          method === 'GET' ||
          method === 'POST' ||
          method === 'PUT' ||
          method === 'DELETE'
        ) {
          return method as HttpMethod;
        }
      }
    }
    // 默认为 GET
    return 'GET';
  }

  /**
   * 从表达式节点提取路径字符串
   * 支持普通字符串字面量和模板字符串
   */
  private extractPathFromExpression(
    expr: ts.Expression,
    _sourceFile: ts.SourceFile
  ): { path: string; params: string[] } | null {
    // 普通字符串字面量
    if (ts.isStringLiteral(expr)) {
      return { path: expr.text, params: [] };
    }

    // 无替换的模板字符串（如 `plain string`）
    if (ts.isNoSubstitutionTemplateLiteral(expr)) {
      return { path: expr.text, params: [] };
    }

    // 带变量的模板字符串（如 `/v1/archive/project/${projectId}`）
    if (ts.isTemplateExpression(expr)) {
      return this.normalizeTemplateLiteral(expr);
    }

    return null;
  }

  /**
   * 将模板字符串转换为路径参数格式
   * `/v1/archive/project/${projectId}` -> { path: '/v1/archive/project/{projectId}', params: ['projectId'] }
   */
  normalizeTemplateLiteral(template: ts.TemplateExpression): {
    path: string;
    params: string[];
  } {
    const params: string[] = [];
    let resultPath = template.head.text;

    for (const span of template.templateSpans) {
      // 提取变量名
      const paramName = this.getExpressionText(span.expression);
      params.push(paramName);
      resultPath += `{${paramName}}`;
      resultPath += span.literal.text;
    }

    return { path: resultPath, params };
  }

  /**
   * 获取表达式的文本表示（变量名）
   */
  private getExpressionText(expr: ts.Expression): string {
    if (ts.isIdentifier(expr)) {
      return expr.text;
    }
    if (ts.isPropertyAccessExpression(expr)) {
      return `${this.getExpressionText(expr.expression)}.${expr.name.text}`;
    }
    // 对于复杂表达式，返回 "expr"
    return 'expr';
  }

  /**
   * 获取属性名
   */
  private getPropertyName(
    prop: ts.PropertyAssignment,
    _sourceFile: ts.SourceFile
  ): string {
    if (ts.isIdentifier(prop.name)) {
      return prop.name.text;
    }
    if (ts.isStringLiteral(prop.name)) {
      return prop.name.text;
    }
    return '';
  }

  /**
   * 查找包围当前节点的最近函数声明名称
   * 支持 export function xxx() 和 function xxx() 形式
   */
  private findEnclosingFunctionName(
    node: ts.Node,
    _sourceFile: ts.SourceFile
  ): string {
    let current: ts.Node | undefined = node.parent;

    while (current) {
      // export function xxx() { ... }
      if (ts.isFunctionDeclaration(current) && current.name) {
        return current.name.text;
      }

      // const xxx = function() { ... }  或 const xxx = () => { ... }
      if (ts.isVariableDeclaration(current) && ts.isIdentifier(current.name)) {
        return current.name.text;
      }

      current = current.parent;
    }

    return '<anonymous>';
  }

  /**
   * 从请求路径中提取模块名
   * /v1/workflow/approval/todo -> workflow
   * /v1/finance/invoice-apply -> finance
   */
  extractModuleFromPath(requestPath: string): string {
    // 路径格式: /v1/{module}/...
    const match = requestPath.match(/^\/v1\/([a-z][a-z0-9-]*)(?:\/|$)/);
    if (match) {
      return match[1];
    }
    return 'unknown';
  }
}
