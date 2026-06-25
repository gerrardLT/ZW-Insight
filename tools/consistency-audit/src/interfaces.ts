/**
 * 一致性审核引擎 - 核心接口定义
 *
 * 定义扫描器、比对器、报告生成器的抽象接口，
 * 支持模块化设计和可扩展性。
 */

import type {
  BackendApiEntry,
  FrontendApiEntry,
  InconsistencyItem,
  AuditStats,
  AuditReport,
} from './types.js';

/** 扫描器接口 - 泛型参数 T 为扫描产出的条目类型 */
export interface IScanner<T> {
  /** 扫描指定根路径下的源码，返回结构化条目列表 */
  scan(rootPath: string): Promise<T[]>;
}

/** 比对器接口 - 比对后端与前端 API 条目，产出不一致项 */
export interface IComparator {
  /** 比对后端、PC前端、移动端 API 条目，返回不一致项列表 */
  compare(
    backend: BackendApiEntry[],
    pcWeb: FrontendApiEntry[],
    mobile: FrontendApiEntry[]
  ): InconsistencyItem[];
}

/** 报告生成器接口 - 基于不一致项和统计数据生成审核报告 */
export interface IReportGenerator {
  /** 生成审核报告 */
  generate(
    items: InconsistencyItem[],
    stats: AuditStats
  ): AuditReport;
}
