/**
 * AuditEngine - 一致性审核引擎主类
 *
 * 职责：
 * 1. 编排并行扫描（BackendScanner、PcWebScanner、MobileScanner）
 * 2. 编排顺序执行比对和审核（ConsistencyComparator、CoverageAuditor、PathAuditor）
 * 3. 汇总不一致项并调用 ReportGenerator 生成报告
 * 4. 模块清单校验（验证覆盖全部 20 个模块）和"待前端对接"状态标注
 * 5. 将报告写入文件（JSON + Markdown，根据 config.outputFormat）
 *
 * Requirements: 8.1, 8.2, 8.3, 8.4
 */

import * as fs from 'node:fs';
import * as path from 'node:path';
import type {
  AuditConfig,
  AuditReport,
  BackendApiEntry,
  FrontendApiEntry,
  InconsistencyItem,
} from './types.js';
import { BackendScanner } from './scanners/backend-scanner.js';
import { PcWebScanner } from './scanners/pc-web-scanner.js';
import { MobileScanner } from './scanners/mobile-scanner.js';
import { ConsistencyComparator } from './comparators/consistency-comparator.js';
import { CoverageAuditor } from './auditors/coverage-auditor.js';
import { PathAuditor } from './auditors/path-auditor.js';
import { ReportGenerator } from './reporters/report-generator.js';
import { featureMappings } from './config/feature-mappings.js';

/** 全部 20 个业务模块 */
const ALL_MODULES = [
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

export class AuditEngine {
  private readonly config: AuditConfig;
  private readonly backendScanner: BackendScanner;
  private readonly pcWebScanner: PcWebScanner;
  private readonly mobileScanner: MobileScanner;
  private readonly comparator: ConsistencyComparator;
  private readonly coverageAuditor: CoverageAuditor;
  private readonly pathAuditor: PathAuditor;
  private readonly reportGenerator: ReportGenerator;

  constructor(config: AuditConfig) {
    this.config = config;
    this.backendScanner = new BackendScanner();
    this.pcWebScanner = new PcWebScanner();
    this.mobileScanner = new MobileScanner();
    this.comparator = new ConsistencyComparator();
    this.coverageAuditor = new CoverageAuditor(featureMappings);
    this.pathAuditor = new PathAuditor();
    this.reportGenerator = new ReportGenerator();
  }

  /**
   * 执行完整审核流程
   *
   * 流程：
   * 1. 并行扫描三端 API
   * 2. 依次执行比对和审核
   * 3. 汇总不一致项
   * 4. 计算统计数据
   * 5. 生成报告
   * 6. 填充平台差异和待前端对接模块
   * 7. 写入报告文件
   */
  async run(): Promise<AuditReport> {
    const startTime = Date.now();

    // 1. 并行扫描三端 API
    const [backendEntries, pcWebEntries, mobileEntries] = await Promise.all([
      this.backendScanner.scan(this.config.rootPath),
      this.pcWebScanner.scan(this.config.rootPath),
      this.mobileScanner.scan(this.config.rootPath),
    ]);

    // 2. 依次执行比对和审核，汇总所有不一致项
    const allInconsistencies: InconsistencyItem[] = [];

    // 2a. ConsistencyComparator: API 路径一致性比对
    const comparatorItems = this.comparator.compare(
      backendEntries,
      pcWebEntries,
      mobileEntries
    );
    allInconsistencies.push(...comparatorItems);

    // 2b. CoverageAuditor: 功能覆盖审核
    const coverageItems = this.coverageAuditor.audit(
      backendEntries,
      pcWebEntries,
      mobileEntries
    );
    allInconsistencies.push(...coverageItems);

    // 2c. PathAuditor: 路径规范审核
    const pathItems = this.pathAuditor.audit(backendEntries);
    allInconsistencies.push(...pathItems);

    // 注：FieldAuditor 因需要逐文件配对，作为可选步骤，不影响核心流程

    // 3. 计算审核统计
    const auditedModules = this.getAuditedModuleCount(backendEntries);
    const stats = this.reportGenerator.computeStats(
      allInconsistencies,
      backendEntries.length,
      pcWebEntries.length,
      mobileEntries.length,
      ALL_MODULES.length,
      auditedModules
    );

    // 4. 生成报告
    const report = this.reportGenerator.generate(allInconsistencies, stats);

    // 5. 填充报告时间和耗时
    const duration = Date.now() - startTime;
    report.summary.scanTime = new Date().toISOString();
    report.summary.duration = duration;

    // 6. 填充 moduleReports 中的 API 数量
    this.fillModuleApiCounts(report, backendEntries, pcWebEntries, mobileEntries);

    // 7. 填充平台差异（pcOnly, mobileOnly, pendingIntegration）
    const platformDiffs = this.coverageAuditor.analyzePlatformDifferences(
      pcWebEntries,
      mobileEntries
    );
    report.platformDifferences.pcOnly = platformDiffs.pcOnly;
    report.platformDifferences.mobileOnly = platformDiffs.mobileOnly;

    // 8. 模块清单校验：检查后端哪些模块在前端均无调用 → pendingIntegration
    report.platformDifferences.pendingIntegration =
      this.detectPendingIntegrationModules(
        backendEntries,
        pcWebEntries,
        mobileEntries
      );

    // 9. 写入报告文件
    await this.writeReportFiles(report);

    return report;
  }

  /**
   * 获取实际扫描到 API 的后端模块数量
   */
  private getAuditedModuleCount(backendEntries: BackendApiEntry[]): number {
    const modules = new Set(backendEntries.map((e) => e.module));
    return modules.size;
  }

  /**
   * 填充 moduleReports 中每个模块的 API 数量
   */
  private fillModuleApiCounts(
    report: AuditReport,
    backendEntries: BackendApiEntry[],
    pcWebEntries: FrontendApiEntry[],
    mobileEntries: FrontendApiEntry[]
  ): void {
    for (const moduleReport of report.moduleReports) {
      const mod = moduleReport.moduleName;
      moduleReport.backendApiCount = backendEntries.filter(
        (e) => e.module === mod
      ).length;
      moduleReport.pcWebApiCount = pcWebEntries.filter(
        (e) => e.module === mod
      ).length;
      moduleReport.mobileApiCount = mobileEntries.filter(
        (e) => e.module === mod
      ).length;
    }
  }

  /**
   * 模块清单校验：检测后端存在已实现 API 但前端（PC + 移动端）均无调用的模块
   * 这些模块标注为"待前端对接"状态
   *
   * Requirements: 8.4
   */
  private detectPendingIntegrationModules(
    backendEntries: BackendApiEntry[],
    pcWebEntries: FrontendApiEntry[],
    mobileEntries: FrontendApiEntry[]
  ): string[] {
    // 获取后端有 API 的模块集合
    const backendModules = new Set(backendEntries.map((e) => e.module));

    // 获取前端有调用的模块集合（PC + 移动端）
    const frontendModules = new Set<string>();
    for (const entry of pcWebEntries) {
      frontendModules.add(entry.module);
    }
    for (const entry of mobileEntries) {
      frontendModules.add(entry.module);
    }

    // 后端有 API 但前端均无调用的模块 → 待前端对接
    const pendingModules: string[] = [];
    for (const mod of backendModules) {
      if (!frontendModules.has(mod)) {
        pendingModules.push(mod);
      }
    }

    return pendingModules.sort();
  }

  /**
   * 将报告写入文件（JSON + Markdown），根据 config.outputFormat
   */
  private async writeReportFiles(report: AuditReport): Promise<void> {
    const outputDir = path.isAbsolute(this.config.outputDir)
      ? this.config.outputDir
      : path.join(this.config.rootPath, this.config.outputDir);

    // 确保输出目录存在
    if (!fs.existsSync(outputDir)) {
      fs.mkdirSync(outputDir, { recursive: true });
    }

    const timestamp = new Date().toISOString().replace(/[:.]/g, '-').slice(0, 19);

    if (this.config.outputFormat.includes('json')) {
      const jsonContent = this.reportGenerator.renderJson(report);
      const jsonPath = path.join(outputDir, `audit-report-${timestamp}.json`);
      fs.writeFileSync(jsonPath, jsonContent, 'utf-8');
      console.log(`[AuditEngine] JSON 报告已生成: ${jsonPath}`);
    }

    if (this.config.outputFormat.includes('markdown')) {
      const mdContent = this.reportGenerator.renderMarkdown(report);
      const mdPath = path.join(outputDir, `audit-report-${timestamp}.md`);
      fs.writeFileSync(mdPath, mdContent, 'utf-8');
      console.log(`[AuditEngine] Markdown 报告已生成: ${mdPath}`);
    }
  }
}
