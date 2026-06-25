/**
 * 一致性审核引擎 - 核心类型定义
 *
 * 定义所有数据模型、枚举类型和配置接口。
 * 所有类型严格按照 design.md 规范实现。
 */

// ===================== HTTP 方法 =====================

/** 支持的 HTTP 方法 */
export type HttpMethod = 'GET' | 'POST' | 'PUT' | 'DELETE';

// ===================== 扫描器输出类型 =====================

/** 后端 API 条目 */
export interface BackendApiEntry {
  /** 业务模块名 (e.g., "finance") */
  module: string;
  /** Controller 类名 */
  controllerClass: string;
  /** 方法名 */
  methodName: string;
  /** HTTP 方法 */
  httpMethod: HttpMethod;
  /** 完整路径 (e.g., "/api/v1/finance/invoice-apply") */
  fullPath: string;
  /** 请求参数类型 */
  requestParamType?: string;
  /** 响应类型 */
  responseType?: string;
  /** 源文件路径 */
  filePath: string;
  /** 行号 */
  lineNumber: number;
}

/** 前端 API 条目 */
export interface FrontendApiEntry {
  /** 来源端 */
  source: 'pc-web' | 'mobile';
  /** 文件名 (e.g., "finance.ts") */
  fileName: string;
  /** 函数名 */
  functionName: string;
  /** HTTP 方法 */
  httpMethod: HttpMethod;
  /** 请求路径 */
  requestPath: string;
  /** 路径参数列表 */
  pathParams: string[];
  /** 所属业务模块 */
  module: string;
  /** 源文件路径 */
  filePath: string;
  /** 行号 */
  lineNumber: number;
}

// ===================== 不一致项类型 =====================

/** 不一致项类型（12种） */
export type InconsistencyType =
  | 'FRONTEND_EXTRA_API'       // 前端多余接口
  | 'BACKEND_ORPHAN_API'       // 后端孤立接口
  | 'HTTP_METHOD_MISMATCH'     // HTTP方法不匹配
  | 'FEATURE_MISSING'          // 功能缺失
  | 'FEATURE_EXTRA'            // 超范围实现
  | 'FIELD_NAME_MISMATCH'      // 字段名不匹配
  | 'FIELD_EXTRA_FRONTEND'     // 前端多余字段
  | 'FIELD_REQUIRED_MISSING'   // 必填字段前端缺失
  | 'PATH_NAMING_VIOLATION'    // 路径命名不规范
  | 'RESTFUL_NAMING_VIOLATION' // RESTful命名不规范
  | 'MOBILE_FEATURE_MISSING'   // 移动端功能缺失
  | 'PC_FEATURE_MISSING';      // PC端功能缺失

/** 严重程度 */
export type Severity = 'Critical' | 'Major' | 'Minor';

/** 不一致项 */
export interface InconsistencyItem {
  /** 不一致类型 */
  type: InconsistencyType;
  /** 严重程度 */
  severity: Severity;
  /** 涉及模块 */
  module: string;
  /** 前端文件路径 */
  frontendFilePath?: string;
  /** 后端文件路径 */
  backendFilePath?: string;
  /** 具体差异描述 */
  description: string;
  /** 建议修复方案 */
  suggestion: string;
}

// ===================== 统计与报告 =====================

/** 审核覆盖率统计 */
export interface AuditStats {
  /** 后端总模块数 */
  totalBackendModules: number;
  /** 已审核模块数 */
  auditedBackendModules: number;
  /** 后端 API 总数 */
  totalBackendApis: number;
  /** PC Web 前端 API 总数 */
  totalPcWebApis: number;
  /** 移动端 API 总数 */
  totalMobileApis: number;
  /** 不一致项总数 */
  totalInconsistencies: number;
  /** 按类型分组的不一致项统计 */
  byType: Record<InconsistencyType, number>;
  /** 按严重程度分组的不一致项统计 */
  bySeverity: Record<Severity, number>;
  /** 一致率百分比 */
  consistencyRate: number;
}

/** 完整审核报告 */
export interface AuditReport {
  /** 报告概要 */
  summary: {
    /** 扫描时间 */
    scanTime: string;
    /** 扫描耗时(ms) */
    duration: number;
    /** 总体一致率 */
    consistencyRate: number;
    /** 统计数据 */
    stats: AuditStats;
  };
  /** 模块级报告列表 */
  moduleReports: ModuleReport[];
  /** 平台差异 */
  platformDifferences: {
    /** PC端独有功能 */
    pcOnly: string[];
    /** 移动端独有功能 */
    mobileOnly: string[];
    /** 待前端对接模块 */
    pendingIntegration: string[];
  };
}

/** 模块级报告 */
export interface ModuleReport {
  /** 模块名 */
  moduleName: string;
  /** 后端 API 数量 */
  backendApiCount: number;
  /** PC Web 前端 API 数量 */
  pcWebApiCount: number;
  /** 移动端 API 数量 */
  mobileApiCount: number;
  /** 该模块的不一致项列表 */
  inconsistencies: InconsistencyItem[];
}

// ===================== 配置 =====================

/** CLI 运行配置 */
export interface AuditConfig {
  /** 项目根目录 */
  rootPath: string;
  /** 后端源码路径 (相对 rootPath) */
  backendPath: string;
  /** PC前端API目录 (相对 rootPath) */
  pcWebApiPath: string;
  /** 移动端API目录 (相对 rootPath) */
  mobileApiPath: string;
  /** 报告输出目录 */
  outputDir: string;
  /** 输出格式 */
  outputFormat: ('json' | 'markdown')[];
  /** 审核模块列表 */
  modules: string[];
  /** 功能映射配置文件路径 */
  featureMappingFile?: string;
  /** 忽略的路径模式 */
  ignorePatterns?: string[];
}

// ===================== API 注册表 =====================

/** API 注册表 - 审核基准数据 */
export interface ApiRegistry {
  /** 后端 API 列表 */
  backend: BackendApiEntry[];
  /** PC Web 前端 API 列表 */
  pcWeb: FrontendApiEntry[];
  /** 移动端 API 列表 */
  mobile: FrontendApiEntry[];
  /** 扫描元数据 */
  metadata: {
    /** 扫描时间 */
    scanTime: string;
    /** 后端模块数量 */
    backendModuleCount: number;
    /** PC Web 文件数量 */
    pcWebFileCount: number;
    /** 移动端文件数量 */
    mobileFileCount: number;
  };
}

// ===================== 字段相关 =====================

/** Java 字段信息 */
export interface JavaField {
  /** 字段名 */
  fieldName: string;
  /** 字段类型 */
  fieldType: string;
  /** 校验注解列表 */
  annotations: string[];
  /** 是否标注 @NotNull/@NotBlank */
  isRequired: boolean;
}

// ===================== 功能覆盖 =====================

/** 功能映射配置 */
export interface FeatureMapping {
  /** 功能编号 */
  featureId: string;
  /** 功能名称 */
  featureName: string;
  /** 所属大类 */
  category: string;
  /** 对应后端模块 */
  backendModule: string;
  /** PC端是否必须 */
  pcRequired: boolean;
  /** 移动端是否必须 */
  mobileRequired: boolean;
  /** 预期的 API 路径模式 */
  expectedApiPatterns: string[];
}

/** 平台差异分析结果 */
export interface PlatformDifferences {
  /** PC端独有功能名称列表 */
  pcOnly: string[];
  /** 移动端独有功能名称列表 */
  mobileOnly: string[];
  /** 不合理差异的不一致项 */
  inconsistencies: InconsistencyItem[];
}

/** 功能覆盖检查结果 */
export interface CoverageCheckResult {
  /** 功能编号 */
  featureId: string;
  /** 功能名称 */
  featureName: string;
  /** 后端是否已实现 */
  backendImplemented: boolean;
  /** PC Web 是否已实现 */
  pcWebImplemented: boolean;
  /** 移动端是否已实现 */
  mobileImplemented: boolean;
  /** 预期支持的平台 */
  expectedPlatforms: ('pc' | 'mobile')[];
}
