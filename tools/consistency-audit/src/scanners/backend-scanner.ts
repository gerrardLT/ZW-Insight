/**
 * 后端 Java Controller 扫描器
 *
 * 扫描策略：
 * 1. 遍历 zw-insight-server 下各模块的 controller 目录
 * 2. 读取每个 .java 文件内容
 * 3. 用正则提取类级 @RequestMapping 前缀
 * 4. 用正则提取方法级 @XxxMapping 注解的路径和 HTTP 方法
 * 5. 拼接完整路径（避免双斜杠）
 *
 * Requirements: 1.1, 1.2, 1.3, 1.4
 */

import * as fs from 'node:fs';
import * as path from 'node:path';
import type { IScanner } from '../interfaces.js';
import type { BackendApiEntry, HttpMethod } from '../types.js';

/** 全部 20 个业务模块 */
const BACKEND_MODULES = [
  'system',
  'finance',
  'site',
  'material',
  'machine',
  'project',
  'workflow',
  'dashboard',
  'archive',
  'contract',
  'budget',
  'purchase',
  'labor',
  'subcontract',
  'hr',
  'tender',
  'basedata',
  'message',
  'security',
  'file',
] as const;

/**
 * 拼接类级路径与方法级路径，避免双斜杠
 *
 * @param classPrefix - 类级 @RequestMapping 声明的前缀路径
 * @param methodPath - 方法级 @XxxMapping 声明的路径
 * @returns 拼接后的完整路径，不含双斜杠
 */
export function joinPaths(classPrefix: string, methodPath: string): string {
  // 确保 classPrefix 以 / 开头
  const normalizedPrefix = classPrefix.startsWith('/')
    ? classPrefix
    : '/' + classPrefix;

  // 如果方法级路径为空，直接返回类级路径
  if (!methodPath) {
    return normalizedPrefix;
  }

  // 移除 classPrefix 尾部斜杠
  const prefix = normalizedPrefix.endsWith('/')
    ? normalizedPrefix.slice(0, -1)
    : normalizedPrefix;

  // 确保 methodPath 以 / 开头
  const suffix = methodPath.startsWith('/') ? methodPath : '/' + methodPath;

  return prefix + suffix;
}

/**
 * 从 @XxxMapping 注解类型推断 HTTP 方法
 */
function mappingTypeToHttpMethod(mappingType: string): HttpMethod {
  switch (mappingType.toLowerCase()) {
    case 'get':
      return 'GET';
    case 'post':
      return 'POST';
    case 'put':
      return 'PUT';
    case 'delete':
      return 'DELETE';
    default:
      return 'GET';
  }
}

/**
 * 从 @RequestMapping 的 method 属性中提取 HTTP 方法
 * 如：method = RequestMethod.POST
 */
function extractRequestMethodFromAnnotation(annotationText: string): HttpMethod | null {
  const methodMatch = annotationText.match(
    /method\s*=\s*(?:RequestMethod\.)?(\w+)/
  );
  if (methodMatch) {
    return mappingTypeToHttpMethod(methodMatch[1]);
  }
  return null;
}

/** 方法级注解解析结果 */
interface MethodMappingResult {
  httpMethod: HttpMethod;
  path: string;
  lineNumber: number;
}

/**
 * 从 Java 类名中提取 Controller 类名
 */
function extractClassName(content: string): string {
  const classMatch = content.match(
    /public\s+class\s+(\w+)/
  );
  return classMatch ? classMatch[1] : 'UnknownController';
}

/**
 * 提取类级 @RequestMapping 前缀
 *
 * 支持格式：
 * - @RequestMapping("/api/v1/finance")
 * - @RequestMapping(value = "/api/v1/finance")
 * - @RequestMapping(value = "/api/v1/finance", produces = ...)
 */
function extractClassLevelPath(content: string): string {
  // 找到 class 声明位置
  const classIndex = content.search(/public\s+class\s+\w+/);
  if (classIndex === -1) return '';

  // 只在 class 声明之前搜索
  const beforeClass = content.substring(0, classIndex);

  // 匹配 @RequestMapping 注解中的路径
  const regex = /@RequestMapping\s*\(\s*(?:value\s*=\s*)?["']([^"']+)["']/;
  const match = beforeClass.match(regex);

  return match ? match[1] : '';
}

/**
 * 提取方法级 Mapping 注解
 *
 * 支持格式：
 * - @GetMapping("/path")
 * - @GetMapping(value = "/path")
 * - @PostMapping("/path")
 * - @PutMapping("/path")
 * - @DeleteMapping("/path")
 * - @RequestMapping(value = "/path", method = RequestMethod.GET)
 * - @GetMapping (无路径参数，表示直接映射到类级路径)
 * - @GetMapping() (空括号，同样映射到类级路径)
 */
function extractMethodMappings(content: string): MethodMappingResult[] {
  const results: MethodMappingResult[] = [];
  const lines = content.split('\n');

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i];

    // 匹配 @GetMapping/@PostMapping/@PutMapping/@DeleteMapping 有路径参数
    const specificMappingWithPath = line.match(
      /@(Get|Post|Put|Delete)Mapping\s*\(\s*(?:value\s*=\s*)?["']([^"']+)["']/
    );
    if (specificMappingWithPath) {
      results.push({
        httpMethod: mappingTypeToHttpMethod(specificMappingWithPath[1]),
        path: specificMappingWithPath[2],
        lineNumber: i + 1,
      });
      continue;
    }

    // 匹配 @GetMapping/@PostMapping/@PutMapping/@DeleteMapping 无路径
    // 支持: @GetMapping, @GetMapping(), @PostMapping 等
    const specificMappingNoPath = line.match(
      /@(Get|Post|Put|Delete)Mapping\s*(?:\(\s*\))?\s*$/
    );
    if (specificMappingNoPath) {
      results.push({
        httpMethod: mappingTypeToHttpMethod(specificMappingNoPath[1]),
        path: '',
        lineNumber: i + 1,
      });
      continue;
    }

    // 匹配方法级 @RequestMapping（需要从 method 属性推断 HTTP 方法）
    const requestMappingMatch = line.match(
      /@RequestMapping\s*\(([^)]*)\)/
    );
    if (requestMappingMatch) {
      // 确认这不是类级注解：类级注解后面紧跟 class 声明
      // 通过检查后续行是否有方法声明（排除 class 声明）来判断
      const nextLines = lines.slice(i + 1, i + 5).join(' ');
      const isClassLevel = /\bclass\s+\w+/.test(nextLines);
      const isMethodLevel = /(?:public|private|protected)\s+(?!class\b)\S+.*\(/.test(nextLines);

      if (isMethodLevel && !isClassLevel) {
        const annotationContent = requestMappingMatch[1];
        // 提取路径
        const pathMatch = annotationContent.match(
          /(?:value\s*=\s*)?["']([^"']+)["']/
        );
        const methodPath = pathMatch ? pathMatch[1] : '';
        // 提取 HTTP 方法
        const httpMethod = extractRequestMethodFromAnnotation(annotationContent) || 'GET';

        results.push({
          httpMethod,
          path: methodPath,
          lineNumber: i + 1,
        });
      }
    }
  }

  return results;
}

/**
 * 从方法注解的行号附近找到方法名
 */
function extractMethodName(lines: string[], annotationLineIndex: number): string {
  // 从注解行往下搜索方法声明（通常注解后 1-4 行是方法签名）
  for (let i = annotationLineIndex; i < Math.min(annotationLineIndex + 6, lines.length); i++) {
    const methodMatch = lines[i].match(
      /(?:public|private|protected)\s+\S+(?:<[^>]+>)?\s+(\w+)\s*\(/
    );
    if (methodMatch) {
      return methodMatch[1];
    }
  }
  return 'unknownMethod';
}

/**
 * 从方法签名中提取请求参数类型（@RequestBody 标注的参数类型）
 */
function extractRequestParamType(lines: string[], annotationLineIndex: number): string | undefined {
  // 从注解行往下搜索方法签名区域
  const methodLines = lines.slice(annotationLineIndex, Math.min(annotationLineIndex + 8, lines.length));
  const methodText = methodLines.join(' ');

  // 匹配 @RequestBody Type paramName
  const bodyMatch = methodText.match(/@RequestBody\s+(\w+(?:<[^>]+>)?)\s+\w+/);
  if (bodyMatch) {
    return bodyMatch[1];
  }

  return undefined;
}

/**
 * 从方法签名中提取响应类型
 * 解析 public R<PageResult<BizInvoiceApply>> methodName(...) 中的泛型参数
 */
function extractResponseType(lines: string[], annotationLineIndex: number): string | undefined {
  // 从注解行往下搜索方法签名
  for (let i = annotationLineIndex; i < Math.min(annotationLineIndex + 6, lines.length); i++) {
    // 匹配返回类型 R<...> 的泛型参数
    const returnTypeMatch = lines[i].match(
      /(?:public|private|protected)\s+R<([^>]+(?:<[^>]+>)?)>\s+\w+\s*\(/
    );
    if (returnTypeMatch) {
      const innerType = returnTypeMatch[1];
      // 如果是 Void，不返回
      if (innerType === 'Void') return undefined;
      return innerType;
    }
  }

  return undefined;
}

/**
 * 后端 Java Controller 扫描器
 */
export class BackendScanner implements IScanner<BackendApiEntry> {
  /**
   * 扫描项目根路径下的后端 Controller，提取 API 声明
   *
   * @param rootPath - 项目根目录路径（包含 zw-insight-server 目录）
   * @returns BackendApiEntry 数组
   */
  async scan(rootPath: string): Promise<BackendApiEntry[]> {
    const entries: BackendApiEntry[] = [];
    const serverPath = path.join(rootPath, 'zw-insight-server');

    for (const moduleName of BACKEND_MODULES) {
      const controllerDir = path.join(
        serverPath,
        `zw-${moduleName}`,
        'src',
        'main',
        'java',
        'com',
        'zwinsight',
        moduleName,
        'controller'
      );

      // 如果 controller 目录不存在，跳过
      if (!fs.existsSync(controllerDir)) {
        continue;
      }

      const javaFiles = this.getJavaFiles(controllerDir);

      for (const javaFile of javaFiles) {
        const fileEntries = this.parseJavaFile(javaFile, moduleName);
        entries.push(...fileEntries);
      }
    }

    return entries;
  }

  /**
   * 获取目录下所有 .java 文件（递归）
   */
  private getJavaFiles(dir: string): string[] {
    const files: string[] = [];

    if (!fs.existsSync(dir)) {
      return files;
    }

    const items = fs.readdirSync(dir, { withFileTypes: true });
    for (const item of items) {
      const fullPath = path.join(dir, item.name);
      if (item.isDirectory()) {
        files.push(...this.getJavaFiles(fullPath));
      } else if (item.isFile() && item.name.endsWith('.java')) {
        files.push(fullPath);
      }
    }

    return files;
  }

  /**
   * 解析单个 Java 文件，提取 API 声明
   */
  private parseJavaFile(
    filePath: string,
    moduleName: string
  ): BackendApiEntry[] {
    const content = fs.readFileSync(filePath, 'utf-8');
    const entries: BackendApiEntry[] = [];

    // 提取类名
    const controllerClass = extractClassName(content);

    // 提取类级路径前缀
    const classPrefix = extractClassLevelPath(content);

    // 提取方法级注解
    const methodMappings = extractMethodMappings(content);
    const lines = content.split('\n');

    for (const mapping of methodMappings) {
      // 拼接完整路径
      const fullPath = classPrefix
        ? joinPaths(classPrefix, mapping.path)
        : mapping.path
          ? (mapping.path.startsWith('/') ? mapping.path : '/' + mapping.path)
          : '/';

      // 注解行的索引（0-based）
      const annotationLineIndex = mapping.lineNumber - 1;

      // 提取方法名
      const methodName = extractMethodName(lines, annotationLineIndex);

      // 提取请求参数类型
      const requestParamType = extractRequestParamType(lines, annotationLineIndex);

      // 提取响应类型
      const responseType = extractResponseType(lines, annotationLineIndex);

      entries.push({
        module: moduleName,
        controllerClass,
        methodName,
        httpMethod: mapping.httpMethod,
        fullPath,
        requestParamType,
        responseType,
        filePath,
        lineNumber: mapping.lineNumber,
      });
    }

    return entries;
  }
}
