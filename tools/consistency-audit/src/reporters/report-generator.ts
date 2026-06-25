/**
 * 审核报告生成器
 *
 * 职责：
 * - 按模块分组不一致项
 * - 按严重程度分类（Critical / Major / Minor）
 * - 输出 JSON 和 Markdown 双格式报告
 * - 计算审核统计数据
 */

import type { IReportGenerator } from '../interfaces.js';
import type {
  InconsistencyItem,
  InconsistencyType,
  Severity,
  AuditStats,
  AuditReport,
  ModuleReport,
} from '../types.js';

export class ReportGenerator implements IReportGenerator {
  /**
   * 生成完整审核报告
   *
   * 将不一致项按模块分组，构建 AuditReport 结构
   */
  generate(items: InconsistencyItem[], stats: AuditStats): AuditReport {
    const moduleMap = this.groupByModule(items);
    const moduleReports: ModuleReport[] = [];

    for (const [moduleName, moduleItems] of moduleMap.entries()) {
      moduleReports.push({
        moduleName,
        backendApiCount: 0, // 由外部 Engine 填充
        pcWebApiCount: 0,
        mobileApiCount: 0,
        inconsistencies: moduleItems,
      });
    }

    const report: AuditReport = {
      summary: {
        scanTime: new Date().toISOString(),
        duration: 0,
        consistencyRate: stats.consistencyRate,
        stats,
      },
      moduleReports,
      platformDifferences: {
        pcOnly: [],
        mobileOnly: [],
        pendingIntegration: [],
      },
    };

    return report;
  }

  /**
   * 按模块分组不一致项
   */
  groupByModule(items: InconsistencyItem[]): Map<string, InconsistencyItem[]> {
    const map = new Map<string, InconsistencyItem[]>();
    for (const item of items) {
      const key = item.module;
      if (!map.has(key)) {
        map.set(key, []);
      }
      map.get(key)!.push(item);
    }
    return map;
  }

  /**
   * 严重程度分类规则
   *
   * Critical: FRONTEND_EXTRA_API, FEATURE_MISSING
   * Major:    HTTP_METHOD_MISMATCH, FIELD_REQUIRED_MISSING, MOBILE_FEATURE_MISSING, PC_FEATURE_MISSING
   * Minor:    BACKEND_ORPHAN_API, FEATURE_EXTRA, FIELD_NAME_MISMATCH, FIELD_EXTRA_FRONTEND,
   *           PATH_NAMING_VIOLATION, RESTFUL_NAMING_VIOLATION
   */
  classifySeverity(type: InconsistencyType): Severity {
    switch (type) {
      case 'FRONTEND_EXTRA_API':
      case 'FEATURE_MISSING':
        return 'Critical';

      case 'HTTP_METHOD_MISMATCH':
      case 'FIELD_REQUIRED_MISSING':
      case 'MOBILE_FEATURE_MISSING':
      case 'PC_FEATURE_MISSING':
        return 'Major';

      case 'BACKEND_ORPHAN_API':
      case 'FEATURE_EXTRA':
      case 'FIELD_NAME_MISMATCH':
      case 'FIELD_EXTRA_FRONTEND':
      case 'PATH_NAMING_VIOLATION':
      case 'RESTFUL_NAMING_VIOLATION':
        return 'Minor';
    }
  }

  /**
   * 计算审核统计数据
   *
   * 不变量保证：
   * - totalInconsistencies === items.length
   * - bySeverity 各值之和 === totalInconsistencies
   * - byType 各值之和 === totalInconsistencies
   * - consistencyRate = 1 - (totalInconsistencies / totalChecks)
   */
  computeStats(
    items: InconsistencyItem[],
    totalBackendApis: number,
    totalPcWebApis: number,
    totalMobileApis: number,
    totalModules: number,
    auditedModules: number
  ): AuditStats {
    const totalInconsistencies = items.length;

    // 按类型统计
    const byType: Record<InconsistencyType, number> = {
      FRONTEND_EXTRA_API: 0,
      BACKEND_ORPHAN_API: 0,
      HTTP_METHOD_MISMATCH: 0,
      FEATURE_MISSING: 0,
      FEATURE_EXTRA: 0,
      FIELD_NAME_MISMATCH: 0,
      FIELD_EXTRA_FRONTEND: 0,
      FIELD_REQUIRED_MISSING: 0,
      PATH_NAMING_VIOLATION: 0,
      RESTFUL_NAMING_VIOLATION: 0,
      MOBILE_FEATURE_MISSING: 0,
      PC_FEATURE_MISSING: 0,
    };

    // 按严重程度统计
    const bySeverity: Record<Severity, number> = {
      Critical: 0,
      Major: 0,
      Minor: 0,
    };

    for (const item of items) {
      byType[item.type]++;
      bySeverity[item.severity]++;
    }

    // totalChecks 合理估算：后端API数 + PC前端API数 + 移动端API数
    const totalChecks = totalBackendApis + totalPcWebApis + totalMobileApis;
    const consistencyRate =
      totalChecks > 0 ? 1 - totalInconsistencies / totalChecks : 1;

    return {
      totalBackendModules: totalModules,
      auditedBackendModules: auditedModules,
      totalBackendApis,
      totalPcWebApis,
      totalMobileApis,
      totalInconsistencies,
      byType,
      bySeverity,
      consistencyRate,
    };
  }

  /**
   * 渲染 Markdown 格式报告
   *
   * 输出内容：
   * - 标题和扫描时间
   * - 概要统计（总数、各严重级别数、一致率）
   * - 按模块分组的不一致项列表
   * - 每个不一致项显示：类型、严重程度、描述、建议
   */
  renderMarkdown(report: AuditReport): string {
    const lines: string[] = [];

    // 标题
    lines.push('# 一致性审核报告');
    lines.push('');
    lines.push(`**扫描时间**: ${report.summary.scanTime}`);
    lines.push(`**扫描耗时**: ${report.summary.duration}ms`);
    lines.push('');

    // 概要统计
    lines.push('## 概要统计');
    lines.push('');
    lines.push(`| 指标 | 数值 |`);
    lines.push(`| --- | --- |`);
    lines.push(
      `| 总不一致项数 | ${report.summary.stats.totalInconsistencies} |`
    );
    lines.push(`| Critical | ${report.summary.stats.bySeverity.Critical} |`);
    lines.push(`| Major | ${report.summary.stats.bySeverity.Major} |`);
    lines.push(`| Minor | ${report.summary.stats.bySeverity.Minor} |`);
    lines.push(
      `| 一致率 | ${(report.summary.consistencyRate * 100).toFixed(2)}% |`
    );
    lines.push(
      `| 后端 API 总数 | ${report.summary.stats.totalBackendApis} |`
    );
    lines.push(
      `| PC Web API 总数 | ${report.summary.stats.totalPcWebApis} |`
    );
    lines.push(
      `| 移动端 API 总数 | ${report.summary.stats.totalMobileApis} |`
    );
    lines.push(
      `| 审核模块 | ${report.summary.stats.auditedBackendModules}/${report.summary.stats.totalBackendModules} |`
    );
    lines.push('');

    // 按模块分组的不一致项
    lines.push('## 模块详情');
    lines.push('');

    if (report.moduleReports.length === 0) {
      lines.push('> 未发现不一致项。');
      lines.push('');
    } else {
      for (const moduleReport of report.moduleReports) {
        lines.push(`### ${moduleReport.moduleName}`);
        lines.push('');
        lines.push(
          `不一致项数: **${moduleReport.inconsistencies.length}**`
        );
        lines.push('');

        for (const item of moduleReport.inconsistencies) {
          lines.push(`- **[${item.severity}]** \`${item.type}\``);
          lines.push(`  - 描述: ${item.description}`);
          lines.push(`  - 建议: ${item.suggestion}`);
        }
        lines.push('');
      }
    }

    // 平台差异
    if (
      report.platformDifferences.pcOnly.length > 0 ||
      report.platformDifferences.mobileOnly.length > 0 ||
      report.platformDifferences.pendingIntegration.length > 0
    ) {
      lines.push('## 平台差异');
      lines.push('');

      if (report.platformDifferences.pcOnly.length > 0) {
        lines.push('### PC 端独有功能');
        lines.push('');
        for (const feature of report.platformDifferences.pcOnly) {
          lines.push(`- ${feature}`);
        }
        lines.push('');
      }

      if (report.platformDifferences.mobileOnly.length > 0) {
        lines.push('### 移动端独有功能');
        lines.push('');
        for (const feature of report.platformDifferences.mobileOnly) {
          lines.push(`- ${feature}`);
        }
        lines.push('');
      }

      if (report.platformDifferences.pendingIntegration.length > 0) {
        lines.push('### 待前端对接模块');
        lines.push('');
        for (const mod of report.platformDifferences.pendingIntegration) {
          lines.push(`- ${mod}`);
        }
        lines.push('');
      }
    }

    return lines.join('\n');
  }

  /**
   * 渲染 JSON 格式报告
   */
  renderJson(report: AuditReport): string {
    return JSON.stringify(report, null, 2);
  }
}
