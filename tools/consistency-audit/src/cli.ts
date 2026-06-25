#!/usr/bin/env node
/**
 * CLI 入口文件
 * 使用 Commander.js 12.x 实现命令行参数解析
 *
 * 退出码：
 * 0 = 审核完成，无 Critical 级别不一致项
 * 1 = 审核完成，存在 Critical 级别不一致项
 * 2 = 配置错误，审核未执行
 * 3 = 运行时错误，审核中断
 */

import { Command } from 'commander';
import * as path from 'node:path';
import * as fs from 'node:fs';
import type { AuditConfig, AuditReport } from './types.js';
import { AuditEngine } from './engine.js';

/** 全部 20 个业务模块 */
const ALL_MODULES = [
  'system', 'finance', 'site', 'material', 'machine',
  'project', 'workflow', 'dashboard', 'archive', 'contract',
  'budget', 'purchase', 'labor', 'subcontract', 'hr',
  'tender', 'basedata', 'message', 'security', 'file',
] as const;

/** 校验模块名是否合法 */
function validateModules(modules: string[]): string[] {
  const invalid = modules.filter(m => !(ALL_MODULES as readonly string[]).includes(m));
  if (invalid.length > 0) {
    throw new Error(`无效的模块名: ${invalid.join(', ')}。有效模块: ${ALL_MODULES.join(', ')}`);
  }
  return modules;
}

/** 校验输出格式是否合法 */
function validateFormats(formats: string[]): ('json' | 'markdown')[] {
  const validFormats = ['json', 'markdown'];
  const invalid = formats.filter(f => !validFormats.includes(f));
  if (invalid.length > 0) {
    throw new Error(`无效的输出格式: ${invalid.join(', ')}。有效格式: json, markdown`);
  }
  return formats as ('json' | 'markdown')[];
}

/** 输出审核结果摘要 */
function printSummary(report: AuditReport): void {
  const { stats } = report.summary;

  console.log('\n========================================');
  console.log('  一致性审核报告摘要');
  console.log('========================================\n');

  console.log(`扫描时间: ${report.summary.scanTime}`);
  console.log(`耗时: ${report.summary.duration}ms`);
  console.log(`一致率: ${stats.consistencyRate.toFixed(1)}%\n`);

  console.log('--- API 统计 ---');
  console.log(`后端 API: ${stats.totalBackendApis}`);
  console.log(`PC Web API: ${stats.totalPcWebApis}`);
  console.log(`移动端 API: ${stats.totalMobileApis}`);
  console.log(`已审核模块: ${stats.auditedBackendModules}/${stats.totalBackendModules}\n`);

  console.log('--- 不一致项统计 ---');
  console.log(`总计: ${stats.totalInconsistencies}`);
  console.log(`  Critical: ${stats.bySeverity.Critical || 0}`);
  console.log(`  Major: ${stats.bySeverity.Major || 0}`);
  console.log(`  Minor: ${stats.bySeverity.Minor || 0}\n`);

  if (report.platformDifferences.pendingIntegration.length > 0) {
    console.log('--- 待前端对接模块 ---');
    console.log(`  ${report.platformDifferences.pendingIntegration.join(', ')}\n`);
  }

  if (stats.bySeverity.Critical > 0) {
    console.log('⚠️  发现 Critical 级别不一致项，请及时处理！');
  } else {
    console.log('✅ 无 Critical 级别不一致项。');
  }

  console.log('');
}

/** 创建并配置 CLI 程序 */
function createProgram(): Command {
  const program = new Command();

  program
    .name('zw-audit')
    .description('中维智营三端代码一致性审核工具')
    .version('1.0.0')
    .option('--root <path>', '项目根目录', process.cwd())
    .option('--output <dir>', '报告输出目录', './audit-reports')
    .option('--format <formats>', '输出格式（逗号分隔: json,markdown）', 'json,markdown')
    .option('--modules <modules>', '审核模块（逗号分隔，默认全部20个模块）', ALL_MODULES.join(','));

  return program;
}

/** CLI 主函数 */
async function main(): Promise<void> {
  const program = createProgram();
  program.parse(process.argv);

  const opts = program.opts<{
    root: string;
    output: string;
    format: string;
    modules: string;
  }>();

  // 解析并校验参数
  let rootPath: string;
  let outputDir: string;
  let outputFormat: ('json' | 'markdown')[];
  let modules: string[];

  try {
    // 解析根目录（转换为绝对路径）
    rootPath = path.isAbsolute(opts.root)
      ? opts.root
      : path.resolve(process.cwd(), opts.root);

    // 检查根目录是否存在
    if (!fs.existsSync(rootPath)) {
      console.error(`错误: 项目根目录不存在: ${rootPath}`);
      process.exit(2);
    }

    // 解析输出目录
    outputDir = opts.output;

    // 解析输出格式
    const formats = opts.format.split(',').map(f => f.trim()).filter(Boolean);
    outputFormat = validateFormats(formats);

    // 解析审核模块
    modules = opts.modules.split(',').map(m => m.trim()).filter(Boolean);
    validateModules(modules);
  } catch (err) {
    const message = err instanceof Error ? err.message : String(err);
    console.error(`配置错误: ${message}`);
    process.exit(2);
  }

  // 构建 AuditConfig
  const config: AuditConfig = {
    rootPath,
    backendPath: 'zw-insight-server',
    pcWebApiPath: 'zw-insight-web/src/api',
    mobileApiPath: 'zw-insight-app/src/api',
    outputDir,
    outputFormat,
    modules,
  };

  // 执行审核
  try {
    console.log('🔍 开始一致性审核...');
    console.log(`  根目录: ${rootPath}`);
    console.log(`  输出目录: ${outputDir}`);
    console.log(`  输出格式: ${outputFormat.join(', ')}`);
    console.log(`  审核模块: ${modules.length} 个`);
    console.log('');

    const engine = new AuditEngine(config);
    const report = await engine.run();

    // 输出摘要
    printSummary(report);

    // 根据是否有 Critical 级别决定退出码
    const hasCritical = (report.summary.stats.bySeverity.Critical || 0) > 0;
    process.exit(hasCritical ? 1 : 0);
  } catch (err) {
    const message = err instanceof Error ? err.message : String(err);
    console.error(`\n运行时错误: ${message}`);
    if (err instanceof Error && err.stack) {
      console.error(err.stack);
    }
    process.exit(3);
  }
}

// 执行 CLI
main();
