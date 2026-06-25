/**
 * 路径规范审核器 (PathAuditor)
 *
 * 校验规则：
 * 1. 后端路径必须以 /api/v1/{module}/ 或 /v1/{module}/ 为前缀
 * 2. 路径中资源命名使用 kebab-case（小写字母和连字符）
 * 3. 前端路径模块名必须与后端已知模块名一致
 *
 * Validates: Requirements 10.1, 10.2, 10.3, 10.4
 */

import type {
  BackendApiEntry,
  FrontendApiEntry,
  InconsistencyItem,
} from '../types.js';

export class PathAuditor {
  /**
   * 路径前缀正则：匹配 /api/v1/{module}/... 或 /v1/{module}/... 格式
   * module 部分为小写字母开头，可含小写字母、数字和连字符
   */
  private pathPrefixRegex = /^\/(?:api\/)?v1\/([a-z][a-z0-9]*(?:-[a-z0-9]+)*)(?:\/|$)/;

  /**
   * kebab-case 正则：小写字母开头，后接小写字母/数字，可用连字符分隔
   */
  private kebabCaseRegex = /^[a-z][a-z0-9]*(-[a-z0-9]+)*$/;

  /**
   * 校验路径前缀规范
   *
   * 后端路径必须匹配 /api/v1/{module}/... 或 /v1/{module}/... 格式
   * module 部分为小写字母数字加连字符
   * 不符合则产出 PATH_NAMING_VIOLATION (severity: Minor)
   */
  validatePathPrefix(entry: BackendApiEntry): InconsistencyItem | null {
    if (this.pathPrefixRegex.test(entry.fullPath)) {
      return null;
    }

    return {
      type: 'PATH_NAMING_VIOLATION',
      severity: 'Minor',
      module: entry.module,
      backendFilePath: entry.filePath,
      description: `路径 "${entry.fullPath}" 不符合 /api/v1/{module}/... 或 /v1/{module}/... 前缀规范`,
      suggestion: `修改路径为 /api/v1/${entry.module}/... 格式，确保模块名为小写字母数字加连字符`,
    };
  }

  /**
   * 校验 RESTful 命名风格
   *
   * 路径中每个资源段必须匹配 kebab-case: /^[a-z][a-z0-9]*(-[a-z0-9]+)*$/
   * 跳过路径参数段 {xxx}
   * 跳过版本段 v1, v2 等
   * 跳过 "api" 段
   * 跳过模块名段（路径中紧跟版本号后的段）
   * 含大写字母、下划线等则产出 RESTFUL_NAMING_VIOLATION (severity: Minor)
   */
  validateRestfulNaming(entry: BackendApiEntry): InconsistencyItem | null {
    const segments = entry.fullPath.split('/').filter((s) => s.length > 0);

    // 确定需要跳过的段索引
    // 典型格式: /api/v1/{module}/resource-name/action
    // 或: /v1/{module}/resource-name/action
    const skippedIndices = new Set<number>();

    for (let i = 0; i < segments.length; i++) {
      const seg = segments[i];

      // 跳过 "api" 段
      if (seg === 'api') {
        skippedIndices.add(i);
        continue;
      }

      // 跳过版本段 (v1, v2, ...)
      if (/^v\d+$/.test(seg)) {
        skippedIndices.add(i);
        // 版本段后面紧跟的是模块名段，也跳过
        if (i + 1 < segments.length) {
          skippedIndices.add(i + 1);
        }
        continue;
      }
    }

    const violations: string[] = [];

    for (let i = 0; i < segments.length; i++) {
      const seg = segments[i];

      // 跳过已标记的段
      if (skippedIndices.has(i)) {
        continue;
      }

      // 跳过路径参数段 {xxx}
      if (seg.startsWith('{') && seg.endsWith('}')) {
        continue;
      }

      // 检查是否符合 kebab-case
      if (!this.kebabCaseRegex.test(seg)) {
        violations.push(seg);
      }
    }

    if (violations.length === 0) {
      return null;
    }

    return {
      type: 'RESTFUL_NAMING_VIOLATION',
      severity: 'Minor',
      module: entry.module,
      backendFilePath: entry.filePath,
      description: `路径 "${entry.fullPath}" 中以下资源段不符合 kebab-case 命名规范: ${violations.map((v) => `"${v}"`).join(', ')}`,
      suggestion: `将资源命名改为 kebab-case 风格（小写字母和连字符），例如: ${violations.map((v) => `"${v}" → "${this.toKebabCase(v)}"`).join(', ')}`,
    };
  }

  /**
   * 校验前端路径模块名与后端模块名一致性
   *
   * 前端路径中的模块名应与后端已知模块名一致
   * 不一致时记录为 PATH_NAMING_VIOLATION
   */
  validateModuleConsistency(
    frontendEntry: FrontendApiEntry,
    backendModules: string[]
  ): InconsistencyItem | null {
    // 从前端路径中提取模块名
    const moduleMatch = frontendEntry.requestPath.match(this.pathPrefixRegex);
    if (!moduleMatch) {
      // 路径格式不匹配，无法提取模块名，不做一致性校验
      return null;
    }

    const frontendModule = moduleMatch[1];

    // 检查前端路径中的模块名是否在后端已知模块名列表中
    if (backendModules.includes(frontendModule)) {
      return null;
    }

    return {
      type: 'PATH_NAMING_VIOLATION',
      severity: 'Minor',
      module: frontendEntry.module,
      frontendFilePath: frontendEntry.filePath,
      description: `前端路径 "${frontendEntry.requestPath}" 中的模块名 "${frontendModule}" 与后端已知模块名不一致`,
      suggestion: `确认模块名是否正确，后端已知模块: ${backendModules.join(', ')}`,
    };
  }

  /**
   * 对每个后端 API 执行 validatePathPrefix 和 validateRestfulNaming
   * 收集所有不一致项
   */
  audit(entries: BackendApiEntry[]): InconsistencyItem[] {
    const items: InconsistencyItem[] = [];

    for (const entry of entries) {
      const prefixResult = this.validatePathPrefix(entry);
      if (prefixResult) {
        items.push(prefixResult);
      }

      const namingResult = this.validateRestfulNaming(entry);
      if (namingResult) {
        items.push(namingResult);
      }
    }

    return items;
  }

  /**
   * 辅助方法：将非规范命名尝试转换为 kebab-case
   */
  private toKebabCase(str: string): string {
    return str
      // 驼峰转换：在大写字母前插入连字符
      .replace(/([a-z0-9])([A-Z])/g, '$1-$2')
      // 下划线替换为连字符
      .replace(/_/g, '-')
      // 统一小写
      .toLowerCase()
      // 合并多余连字符
      .replace(/-+/g, '-')
      // 去除首尾连字符
      .replace(/^-|-$/g, '');
  }
}
