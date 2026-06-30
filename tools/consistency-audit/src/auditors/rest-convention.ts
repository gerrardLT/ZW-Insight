/**
 * 统一 REST 约定判定函数 (REST Convention Classifier)
 *
 * 将一个 (method, path) 二元组判定为统一 REST 约定中的操作类型，作为
 * 63 项核心错位逐项归类（A/B/C）与对齐验证的纯函数判定器。
 *
 * 目标契约来源：audit-reports/rest-convention.md（任务 1.2 产物）
 *  - 列表/分页   = 根 GET            `/v1/<module>/<entity>`
 *  - 详情        = GET `/{id}`       `/v1/<module>/<entity>/{id}`
 *  - 新增        = POST 根           `/v1/<module>/<entity>`
 *  - 更新        = PUT `/{id}`       `/v1/<module>/<entity>/{id}`
 *  - 单条删除    = DELETE `/{id}`    `/v1/<module>/<entity>/{id}`
 *  - 批量删除    = DELETE `/batch`   `/v1/<module>/<entity>/batch`
 *  - 动作        = POST `/{id}/<action>` `/v1/<module>/<entity>/{id}/<action>`
 *  - 其余        = 不符合约定
 *
 * 规范化约定（需求 3.2）：
 *  - 方法比较前做大小写归一（统一大写）后逐字符相等；
 *  - 路径比较前去除尾部斜杠后逐字符相等。
 *
 * 占位符约束（需求 1.1）：
 *  - <module> / <entity> / <action>：`^[a-z0-9-]{1,64}$`
 *  - {id}：非空路径段（不含 `/`）
 *
 * 本模块为纯工具代码，不修改任何前后端业务代码，不产生副作用。
 *
 * Validates: Requirements 1.2, 1.3, 1.4, 1.5, 1.6, 1.7, 2.2, 3.2
 */

/** 统一 REST 约定下的操作类型 */
export type RestOperation =
  | 'list' // 列表/分页：根 GET
  | 'detail' // 详情：GET /{id}
  | 'create' // 新增：POST 根
  | 'update' // 更新：PUT /{id}
  | 'delete' // 单条删除：DELETE /{id}
  | 'batchDelete' // 批量删除：DELETE /batch
  | 'action' // 动作：POST /{id}/<action>
  | 'nonCompliant'; // 不符合统一 REST 约定

/** 操作类型 → 中文名（与 rest-convention.md 判定表逐字对应） */
export const REST_OPERATION_LABELS: Record<RestOperation, string> = {
  list: '列表',
  detail: '详情',
  create: '新增',
  update: '更新',
  delete: '删除',
  batchDelete: '批量删除',
  action: '动作',
  nonCompliant: '不符合约定',
};

/** 批量删除的保留字面量尾段 */
export const BATCH_SEGMENT = 'batch';

/**
 * 占位符（<module>/<entity>/<action>）约束正则：
 * 仅小写字母、数字、连字符，长度 1–64（需求 1.1）。
 */
export const SLUG_REGEX = /^[a-z0-9-]{1,64}$/;

/** 版本段约束（如 v1、v2）。统一约定以 /v1/ 为根，此处放宽到 v\d+。 */
const VERSION_REGEX = /^v\d+$/;

/**
 * 方法规范化：大小写归一为大写并去除首尾空白（需求 3.2）。
 */
export function normalizeMethod(method: string): string {
  return method.trim().toUpperCase();
}

/**
 * 路径规范化：去除尾部斜杠（保留根 "/"）后逐字符可比较（需求 3.2）。
 * 仅做尾部斜杠裁剪与首尾空白裁剪，不改变路径段内容与大小写。
 */
export function normalizePath(path: string): string {
  const trimmed = path.trim();
  if (trimmed === '') {
    return '';
  }
  // 去除尾部斜杠，但若整串均为斜杠则归一为单个 '/'
  const stripped = trimmed.replace(/\/+$/, '');
  return stripped === '' ? '/' : stripped;
}

/**
 * 将路径拆分为有效路径段，并剥离可选的 `api` 前缀。
 * 返回 null 表示路径不以 `/v1`（或 `/api/v1`）版本段开头，不属于约定覆盖范围。
 */
function extractSegments(
  normalizedPath: string
): { module: string; entity: string; rest: string[] } | null {
  const segments = normalizedPath.split('/').filter((s) => s.length > 0);
  if (segments.length === 0) {
    return null;
  }

  // 剥离可选 api 前缀
  let idx = 0;
  if (segments[idx] === 'api') {
    idx += 1;
  }

  // 版本段
  const version = segments[idx];
  if (version === undefined || !VERSION_REGEX.test(version)) {
    return null;
  }
  idx += 1;

  const module = segments[idx];
  const entity = segments[idx + 1];
  if (module === undefined || entity === undefined) {
    return null;
  }

  const rest = segments.slice(idx + 2);
  return { module, entity, rest };
}

/**
 * 统一 REST 约定判定函数（纯函数）。
 *
 * @param method HTTP 方法（大小写不敏感）
 * @param path   请求路径（可含/不含 `/api` 前缀、可含尾部斜杠）
 * @returns 该 (method, path) 在统一 REST 约定下的操作类型
 */
export function classifyRestOperation(method: string, path: string): RestOperation {
  const m = normalizeMethod(method);
  const parsed = extractSegments(normalizePath(path));

  if (parsed === null) {
    return 'nonCompliant';
  }

  const { module, entity, rest } = parsed;

  // 占位符约束校验：module/entity 必须满足 slug 规则
  if (!SLUG_REGEX.test(module) || !SLUG_REGEX.test(entity)) {
    return 'nonCompliant';
  }

  // 根路径：/v1/<module>/<entity>
  if (rest.length === 0) {
    if (m === 'GET') return 'list';
    if (m === 'POST') return 'create';
    return 'nonCompliant';
  }

  // 单尾段：/v1/<module>/<entity>/{id} 或 /v1/<module>/<entity>/batch
  if (rest.length === 1) {
    const tail = rest[0];

    // 批量删除：尾段为保留字面量 batch
    if (tail === BATCH_SEGMENT) {
      return m === 'DELETE' ? 'batchDelete' : 'nonCompliant';
    }

    // 其余视为 {id} 详情/更新/删除（{id} 为非空路径段）
    if (m === 'GET') return 'detail';
    if (m === 'PUT') return 'update';
    if (m === 'DELETE') return 'delete';
    return 'nonCompliant';
  }

  // 双尾段：/v1/<module>/<entity>/{id}/<action>
  if (rest.length === 2) {
    const action = rest[1];
    // action 段需满足 slug 约束；{id} 段非空即可（已被 filter 保证）
    if (m === 'POST' && action !== BATCH_SEGMENT && SLUG_REGEX.test(action)) {
      return 'action';
    }
    return 'nonCompliant';
  }

  // 超过约定层级
  return 'nonCompliant';
}
