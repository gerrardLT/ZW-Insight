/**
 * API 路径一致性比对引擎
 *
 * 比对策略：
 * 1. 规范化路径（统一移除 /api 前缀）
 * 2. 将路径参数统一为 {param} 格式
 * 3. 逐一匹配前端路径 → 后端注册表
 * 4. 记录各类不一致项
 */

import type { IComparator } from '../interfaces.js';
import type {
  BackendApiEntry,
  FrontendApiEntry,
  InconsistencyItem,
} from '../types.js';

export class ConsistencyComparator implements IComparator {
  /**
   * 路径规范化
   *
   * 规则：
   * - 移除路径开头的 `/api` 前缀（后端可能通过网关添加）
   * - 将路径参数统一为 `{param}` 格式（支持 :param、{param}、${param}）
   * - 移除尾部斜杠（根路径 "/" 除外）
   * - 转为小写以忽略大小写差异
   *
   * 幂等性保证：normalizePath(normalizePath(P)) === normalizePath(P)
   * 前缀无关性：normalizePath("/api" + P) === normalizePath(P)
   */
  normalizePath(path: string): string {
    let normalized = path;

    // 移除开头的 /api 前缀（仅移除第一层 /api，不影响路径中其他部分）
    if (normalized.startsWith('/api/')) {
      normalized = normalized.slice(4); // "/api/v1/xxx" → "/v1/xxx"
    } else if (normalized === '/api') {
      normalized = '/';
    }

    // 将 :param 风格的路径参数统一为 {param} 格式
    // 匹配 :paramName（后跟 / 或字符串结尾）
    normalized = normalized.replace(/:([a-zA-Z_][a-zA-Z0-9_]*)/g, '{$1}');

    // 将 ${param} 风格（模板字符串残留）统一为 {param} 格式
    normalized = normalized.replace(/\$\{([^}]+)\}/g, '{$1}');

    // 移除尾部斜杠（根路径除外）
    if (normalized.length > 1 && normalized.endsWith('/')) {
      normalized = normalized.slice(0, -1);
    }

    // 确保以 / 开头
    if (!normalized.startsWith('/')) {
      normalized = '/' + normalized;
    }

    return normalized;
  }

  /**
   * 路径匹配：支持路径参数通配
   *
   * 规则：
   * - 路径参数 `{xxx}` 视为通配符，匹配任何单段
   * - 其余部分严格匹配（大小写不敏感）
   * - 比对前先做 normalizePath
   */
  pathsMatch(path1: string, path2: string): boolean {
    const normalized1 = this.normalizePath(path1);
    const normalized2 = this.normalizePath(path2);

    const segments1 = normalized1.split('/').filter(Boolean);
    const segments2 = normalized2.split('/').filter(Boolean);

    // 段数不同则不匹配
    if (segments1.length !== segments2.length) {
      return false;
    }

    // 逐段比较
    for (let i = 0; i < segments1.length; i++) {
      const seg1 = segments1[i];
      const seg2 = segments2[i];

      // 任一方为路径参数通配符，视为匹配
      const isParam1 = /^\{[^}]+\}$/.test(seg1);
      const isParam2 = /^\{[^}]+\}$/.test(seg2);

      if (isParam1 || isParam2) {
        continue;
      }

      // 非参数段严格匹配（大小写不敏感）
      if (seg1.toLowerCase() !== seg2.toLowerCase()) {
        return false;
      }
    }

    return true;
  }

  /**
   * 比对后端、PC前端、移动端 API 条目，返回不一致项列表
   *
   * 逻辑：
   * 1. 遍历 PC 前端 API 列表，逐一在后端查找匹配（路径匹配 + HTTP 方法一致）
   * 2. 遍历移动端 API 列表，逐一在后端查找匹配
   * 3. 前端路径在后端不存在 → FRONTEND_EXTRA_API (severity: Critical)
   * 4. 后端路径在两个前端均不存在 → BACKEND_ORPHAN_API (severity: Minor)
   * 5. 路径匹配但 HTTP 方法不一致 → HTTP_METHOD_MISMATCH (severity: Major)
   */
  compare(
    backend: BackendApiEntry[],
    pcWeb: FrontendApiEntry[],
    mobile: FrontendApiEntry[]
  ): InconsistencyItem[] {
    const items: InconsistencyItem[] = [];

    // 记录后端被匹配的索引（用于检测孤立接口）
    const matchedBackendIndices = new Set<number>();

    // 比对 PC 前端
    this.compareFrontendToBackend(pcWeb, backend, matchedBackendIndices, items);

    // 比对移动端
    this.compareFrontendToBackend(mobile, backend, matchedBackendIndices, items);

    // 检测后端孤立接口：后端路径在两个前端均不存在调用
    for (let i = 0; i < backend.length; i++) {
      if (!matchedBackendIndices.has(i)) {
        const entry = backend[i];
        items.push({
          type: 'BACKEND_ORPHAN_API',
          severity: 'Minor',
          module: entry.module,
          backendFilePath: entry.filePath,
          description: `后端接口 ${entry.httpMethod} ${entry.fullPath}（${entry.controllerClass}.${entry.methodName}）在 PC 前端和移动端均无调用`,
          suggestion: `确认该接口是否仍在使用，若已废弃建议添加 @Deprecated 注解或移除`,
        });
      }
    }

    return items;
  }

  /**
   * 比对前端 API 列表与后端 API 列表
   */
  private compareFrontendToBackend(
    frontendEntries: FrontendApiEntry[],
    backendEntries: BackendApiEntry[],
    matchedBackendIndices: Set<number>,
    items: InconsistencyItem[]
  ): void {
    const sourceLabel = frontendEntries.length > 0
      ? (frontendEntries[0]?.source === 'pc-web' ? 'PC前端' : '移动端')
      : '';

    for (const feEntry of frontendEntries) {
      // 查找路径匹配的后端条目
      let pathMatched = false;
      let methodMatched = false;
      let matchedBackendEntry: BackendApiEntry | undefined;

      for (let i = 0; i < backendEntries.length; i++) {
        const beEntry = backendEntries[i];

        if (this.pathsMatch(feEntry.requestPath, beEntry.fullPath)) {
          pathMatched = true;
          matchedBackendEntry = beEntry;

          if (feEntry.httpMethod === beEntry.httpMethod) {
            methodMatched = true;
            matchedBackendIndices.add(i);
            break;
          }
        }
      }

      if (!pathMatched) {
        // 前端路径在后端不存在 → FRONTEND_EXTRA_API
        items.push({
          type: 'FRONTEND_EXTRA_API',
          severity: 'Critical',
          module: feEntry.module,
          frontendFilePath: feEntry.filePath,
          description: `${sourceLabel}接口 ${feEntry.httpMethod} ${feEntry.requestPath}（${feEntry.functionName}）在后端无对应 Controller 方法`,
          suggestion: `检查后端是否缺少该接口实现，或前端路径是否拼写错误`,
        });
      } else if (!methodMatched && matchedBackendEntry) {
        // 路径匹配但 HTTP 方法不一致 → HTTP_METHOD_MISMATCH
        items.push({
          type: 'HTTP_METHOD_MISMATCH',
          severity: 'Major',
          module: feEntry.module,
          frontendFilePath: feEntry.filePath,
          backendFilePath: matchedBackendEntry.filePath,
          description: `${sourceLabel}使用 ${feEntry.httpMethod} 请求 ${feEntry.requestPath}，但后端声明为 ${matchedBackendEntry.httpMethod}`,
          suggestion: `统一 HTTP 方法：前端改为 ${matchedBackendEntry.httpMethod} 或后端添加 ${feEntry.httpMethod} 映射`,
        });
        // HTTP 方法不匹配时，也标记后端为已匹配（路径存在，只是方法不同）
        const backendIndex = backendEntries.indexOf(matchedBackendEntry);
        if (backendIndex !== -1) {
          matchedBackendIndices.add(backendIndex);
        }
      }
    }
  }
}
