/**
 * 功能覆盖率审核器 (CoverageAuditor)
 *
 * 基于 REQ-031 功能表定义，验证各端功能实现的完整性。
 *
 * 审核策略：
 * 1. 遍历每个 FeatureMapping 配置项
 * 2. 检查后端 API 列表中是否存在匹配 expectedApiPatterns 的条目
 * 3. 若 pcRequired=true，检查 PC 前端 API 列表中是否存在匹配条目
 * 4. 若 mobileRequired=true，检查移动端 API 列表中是否存在匹配条目
 * 5. 检测前端存在但功能表中未定义的超范围实现
 *
 * 匹配逻辑：前端 API 的 requestPath 以 expectedApiPatterns 中的某个模式为前缀
 *
 * Requirements: 5.1, 5.2, 5.3, 5.4, 5.5
 */

import type {
  BackendApiEntry,
  FrontendApiEntry,
  FeatureMapping,
  InconsistencyItem,
  CoverageCheckResult,
  PlatformDifferences,
} from '../types.js';

/**
 * 功能覆盖率审核器
 *
 * 检测功能缺失（FEATURE_MISSING）和超范围实现（FEATURE_EXTRA）
 */
export class CoverageAuditor {
  constructor(private featureMappings: FeatureMapping[]) {}

  /**
   * 执行功能覆盖审核
   *
   * @param backend - 后端 API 条目列表
   * @param pcWeb - PC 前端 API 条目列表
   * @param mobile - 移动端 API 条目列表
   * @returns 不一致项列表
   */
  audit(
    backend: BackendApiEntry[],
    pcWeb: FrontendApiEntry[],
    mobile: FrontendApiEntry[]
  ): InconsistencyItem[] {
    const inconsistencies: InconsistencyItem[] = [];

    // 1. 检测功能缺失
    const missingItems = this.detectFeatureMissing(backend, pcWeb, mobile);
    inconsistencies.push(...missingItems);

    // 2. 检测超范围实现
    const extraItems = this.detectFeatureExtra(pcWeb, mobile);
    inconsistencies.push(...extraItems);

    // 3. 检测平台差异（移动端/PC端功能缺失）
    const platformDiffs = this.analyzePlatformDifferences(pcWeb, mobile);
    inconsistencies.push(...platformDiffs.inconsistencies);

    return inconsistencies;
  }

  /**
   * 获取功能覆盖检查结果列表
   *
   * @param backend - 后端 API 条目列表
   * @param pcWeb - PC 前端 API 条目列表
   * @param mobile - 移动端 API 条目列表
   * @returns 功能覆盖检查结果列表
   */
  getCoverageResults(
    backend: BackendApiEntry[],
    pcWeb: FrontendApiEntry[],
    mobile: FrontendApiEntry[]
  ): CoverageCheckResult[] {
    return this.featureMappings.map((mapping) => {
      const backendImplemented = this.hasMatchingBackendApi(backend, mapping);
      const pcWebImplemented = this.hasMatchingFrontendApi(pcWeb, mapping);
      const mobileImplemented = this.hasMatchingFrontendApi(mobile, mapping);

      const expectedPlatforms: ('pc' | 'mobile')[] = [];
      if (mapping.pcRequired) expectedPlatforms.push('pc');
      if (mapping.mobileRequired) expectedPlatforms.push('mobile');

      return {
        featureId: mapping.featureId,
        featureName: mapping.featureName,
        backendImplemented,
        pcWebImplemented,
        mobileImplemented,
        expectedPlatforms,
      };
    });
  }

  /**
   * 分析移动端与 PC 端的功能差异
   *
   * 审核策略：
   * 1. 遍历每个 FeatureMapping 配置项
   * 2. 检查该功能在 PC 前端和移动端分别是否有实现
   * 3. 仅在 PC 端有实现：
   *    - 若 mobileRequired=true → 不合理差异，记录 MOBILE_FEATURE_MISSING (Major)
   *    - 若 mobileRequired=false → 合理差异（如平台管理仅需 PC 端）
   * 4. 仅在移动端有实现：
   *    - 若 pcRequired=true → 不合理差异，记录 PC_FEATURE_MISSING (Major)
   *    - 若 pcRequired=false → 合理差异（如定位签到仅需移动端）
   *
   * Requirements: 9.1, 9.2, 9.3, 9.4
   *
   * @param pcWeb - PC 前端 API 条目列表
   * @param mobile - 移动端 API 条目列表
   * @returns 平台差异分析结果
   */
  analyzePlatformDifferences(
    pcWeb: FrontendApiEntry[],
    mobile: FrontendApiEntry[]
  ): PlatformDifferences {
    const pcOnly: string[] = [];
    const mobileOnly: string[] = [];
    const inconsistencies: InconsistencyItem[] = [];

    for (const mapping of this.featureMappings) {
      const pcImplemented = this.hasMatchingFrontendApi(pcWeb, mapping);
      const mobileImplemented = this.hasMatchingFrontendApi(mobile, mapping);

      // 功能仅在 PC 端有实现
      if (pcImplemented && !mobileImplemented) {
        pcOnly.push(mapping.featureName);

        // 若移动端也需要该功能 → 不合理差异
        if (mapping.mobileRequired) {
          inconsistencies.push({
            type: 'MOBILE_FEATURE_MISSING',
            severity: 'Major',
            module: mapping.backendModule,
            description: `移动端功能缺失（平台差异）: [${mapping.featureId}] ${mapping.featureName}（分类: ${mapping.category}），该功能已在PC端实现但移动端缺失`,
            suggestion: `根据 REQ-031 功能表，该功能移动端也需实现，请在移动端补充对 ${mapping.expectedApiPatterns.join(', ')} 的调用`,
          });
        }
        // 若 mobileRequired=false → 合理差异（如平台管理功能仅需 PC 端），不记录不一致项
      }

      // 功能仅在移动端有实现
      if (mobileImplemented && !pcImplemented) {
        mobileOnly.push(mapping.featureName);

        // 若 PC 端也需要该功能 → 不合理差异
        if (mapping.pcRequired) {
          inconsistencies.push({
            type: 'PC_FEATURE_MISSING',
            severity: 'Major',
            module: mapping.backendModule,
            description: `PC端功能缺失（平台差异）: [${mapping.featureId}] ${mapping.featureName}（分类: ${mapping.category}），该功能已在移动端实现但PC端缺失`,
            suggestion: `根据 REQ-031 功能表，该功能PC端也需实现，请在PC前端补充对 ${mapping.expectedApiPatterns.join(', ')} 的调用`,
          });
        }
        // 若 pcRequired=false → 合理差异（如定位签到仅需移动端），不记录不一致项
      }
    }

    return { pcOnly, mobileOnly, inconsistencies };
  }

  /**
   * 检测功能缺失
   *
   * 遍历功能映射，对每个功能点：
   * - 若 pcRequired=true 且 PC 前端无匹配 → FEATURE_MISSING (Critical)
   * - 若 mobileRequired=true 且移动端无匹配 → FEATURE_MISSING (Critical)
   *
   * @param _backend - 后端 API 条目（保留用于未来后端实现状态检测）
   * @param pcWeb - PC 前端 API 条目列表
   * @param mobile - 移动端 API 条目列表
   */
  private detectFeatureMissing(
    _backend: BackendApiEntry[],
    pcWeb: FrontendApiEntry[],
    mobile: FrontendApiEntry[]
  ): InconsistencyItem[] {
    const items: InconsistencyItem[] = [];

    for (const mapping of this.featureMappings) {
      // 检查 PC 端覆盖
      if (mapping.pcRequired) {
        const pcMatched = this.hasMatchingFrontendApi(pcWeb, mapping);
        if (!pcMatched) {
          items.push({
            type: 'FEATURE_MISSING',
            severity: 'Critical',
            module: mapping.backendModule,
            description: `PC端功能缺失: [${mapping.featureId}] ${mapping.featureName}（分类: ${mapping.category}），预期API模式: ${mapping.expectedApiPatterns.join(', ')}`,
            suggestion: `在 PC 前端 API 文件中实现对 ${mapping.expectedApiPatterns.join(', ')} 的调用`,
          });
        }
      }

      // 检查移动端覆盖
      if (mapping.mobileRequired) {
        const mobileMatched = this.hasMatchingFrontendApi(mobile, mapping);
        if (!mobileMatched) {
          items.push({
            type: 'FEATURE_MISSING',
            severity: 'Critical',
            module: mapping.backendModule,
            description: `移动端功能缺失: [${mapping.featureId}] ${mapping.featureName}（分类: ${mapping.category}），预期API模式: ${mapping.expectedApiPatterns.join(', ')}`,
            suggestion: `在移动端 API 文件中实现对 ${mapping.expectedApiPatterns.join(', ')} 的调用`,
          });
        }
      }
    }

    return items;
  }

  /**
   * 检测超范围实现
   *
   * 遍历前端 API 调用列表，若某个调用路径未被任何功能映射覆盖，
   * 则标记为 FEATURE_EXTRA (Minor)
   */
  private detectFeatureExtra(
    pcWeb: FrontendApiEntry[],
    mobile: FrontendApiEntry[]
  ): InconsistencyItem[] {
    const items: InconsistencyItem[] = [];

    // 检测 PC 前端超范围实现
    const pcExtraApis = this.findExtraApis(pcWeb);
    for (const api of pcExtraApis) {
      items.push({
        type: 'FEATURE_EXTRA',
        severity: 'Minor',
        module: api.module,
        frontendFilePath: api.filePath,
        description: `PC端超范围实现: ${api.functionName}（路径: ${api.requestPath}），该接口未在功能表中定义`,
        suggestion: `确认该功能是否应添加到功能表中，或移除多余的前端实现`,
      });
    }

    // 检测移动端超范围实现
    const mobileExtraApis = this.findExtraApis(mobile);
    for (const api of mobileExtraApis) {
      items.push({
        type: 'FEATURE_EXTRA',
        severity: 'Minor',
        module: api.module,
        frontendFilePath: api.filePath,
        description: `移动端超范围实现: ${api.functionName}（路径: ${api.requestPath}），该接口未在功能表中定义`,
        suggestion: `确认该功能是否应添加到功能表中，或移除多余的前端实现`,
      });
    }

    return items;
  }

  /**
   * 检查后端 API 列表中是否存在匹配指定功能映射的条目
   *
   * 匹配逻辑：后端 API 的 fullPath 以 expectedApiPatterns 中某个模式为前缀
   */
  private hasMatchingBackendApi(
    backend: BackendApiEntry[],
    mapping: FeatureMapping
  ): boolean {
    return mapping.expectedApiPatterns.some((pattern) =>
      backend.some((entry) => this.pathMatchesPattern(entry.fullPath, pattern))
    );
  }

  /**
   * 检查前端 API 列表中是否存在匹配指定功能映射的条目
   *
   * 匹配逻辑：前端 API 的 requestPath 以 expectedApiPatterns 中某个模式为前缀
   */
  private hasMatchingFrontendApi(
    frontendApis: FrontendApiEntry[],
    mapping: FeatureMapping
  ): boolean {
    return mapping.expectedApiPatterns.some((pattern) =>
      frontendApis.some((entry) =>
        this.pathMatchesPattern(entry.requestPath, pattern)
      )
    );
  }

  /**
   * 路径前缀匹配
   *
   * 检查 actualPath 是否以 pattern 为前缀（忽略 /api 前缀差异）
   * 例如：
   * - actualPath="/v1/finance/invoice-apply/list", pattern="/v1/finance/invoice-apply" → true
   * - actualPath="/api/v1/finance/invoice-apply", pattern="/v1/finance/invoice-apply" → true
   * - actualPath="/v1/finance/other", pattern="/v1/finance/invoice-apply" → false
   */
  private pathMatchesPattern(actualPath: string, pattern: string): boolean {
    // 规范化路径：移除 /api 前缀
    const normalizedActual = this.removePrefixApi(actualPath);
    const normalizedPattern = this.removePrefixApi(pattern);

    // 前缀匹配：actualPath 以 pattern 开头
    // 且匹配位置之后要么结束，要么是 "/" 或其他路径分隔
    if (normalizedActual === normalizedPattern) {
      return true;
    }

    if (normalizedActual.startsWith(normalizedPattern)) {
      // 确保是完整的路径段匹配，而不是部分字符串匹配
      const nextChar = normalizedActual[normalizedPattern.length];
      return nextChar === '/' || nextChar === undefined;
    }

    return false;
  }

  /**
   * 移除路径中的 /api 前缀
   */
  private removePrefixApi(p: string): string {
    if (p.startsWith('/api/')) {
      return p.slice(4); // 移除 "/api" 前缀，保留后续的 "/..."
    }
    return p;
  }

  /**
   * 查找前端 API 中未被任何功能映射覆盖的条目
   *
   * 如果某个前端 API 的 requestPath 没有匹配任何 featureMapping 的 expectedApiPatterns，
   * 则视为超范围实现
   */
  private findExtraApis(frontendApis: FrontendApiEntry[]): FrontendApiEntry[] {
    return frontendApis.filter((api) => {
      // 检查该 API 路径是否被任何功能映射所覆盖
      return !this.featureMappings.some((mapping) =>
        mapping.expectedApiPatterns.some((pattern) =>
          this.pathMatchesPattern(api.requestPath, pattern)
        )
      );
    });
  }
}
