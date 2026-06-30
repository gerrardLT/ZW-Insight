/**
 * 真实数据不变式属性测试（Property 4 + Property 5）
 *
 * Property 4 — 真实数据不变式：
 *   任一通过验证的请求其响应来自真实后端与真实 DB，不存在前端假数据兜底路径。
 *   请求失败时显式返回错误而非静默回退到本地假数据。
 *   验证方式：对前端 src/api/*.ts 做静态分析——随机抽取 api 文件中的函数实现，
 *   断言不存在 catch 块中返回 hardcoded 数据的模式；断言 error 分支要么
 *   throw/reject 要么返回含 error 信息的对象。
 *
 * Property 5 — 登录可达性：
 *   真实验证码（Redis captcha:<uuid>）+ admin/123456 必能完成真实登录并取得动态菜单。
 *   验证方式：对 auth 流程（captcha image → redis get → login → menu）逻辑做合约测试，
 *   断言 login 函数不含任何 mock/fallback/hardcoded token 路径。
 *
 * 框架：vitest + fast-check
 *
 * **Validates: Requirements 5.1, 5.3, 5.5, 5.6, 9.6, 9.7**
 */

import { describe, it, expect } from 'vitest';
import fc from 'fast-check';
import { readFileSync, readdirSync } from 'node:fs';
import { join, resolve } from 'node:path';

// ===================== 常量与工具函数 =====================

/** 前端 api 目录路径 */
const API_DIR = resolve(__dirname, '../../../../zw-insight-web/src/api');

/** 登录页面文件路径 */
const LOGIN_VIEW_PATH = resolve(
  __dirname,
  '../../../../zw-insight-web/src/views/login/index.vue'
);

/** 请求工具路径 */
const REQUEST_UTIL_PATH = resolve(
  __dirname,
  '../../../../zw-insight-web/src/utils/request.ts'
);

/** 读取 API 目录下所有 .ts 文件 */
function getApiFiles(): string[] {
  return readdirSync(API_DIR)
    .filter((f) => f.endsWith('.ts'))
    .map((f) => join(API_DIR, f));
}

/** 读取文件内容 */
function readSource(filePath: string): string {
  return readFileSync(filePath, 'utf-8');
}

/**
 * 提取文件中所有导出函数体（简单正则提取 export function ... { ... } 块）
 * 返回 { name, body } 数组
 */
function extractExportedFunctions(
  source: string
): { name: string; body: string }[] {
  const results: { name: string; body: string }[] = [];
  // 匹配 export function xxx(...) { ... } 或 export async function xxx(...) { ... }
  const funcRegex =
    /export\s+(?:async\s+)?function\s+(\w+)\s*\([^)]*\)\s*(?::\s*[^{]+?)?\s*\{/g;
  let match: RegExpExecArray | null;
  while ((match = funcRegex.exec(source)) !== null) {
    const name = match[1];
    const startIdx = match.index + match[0].length - 1; // 定位到 '{'
    const body = extractBracedBlock(source, startIdx);
    if (body) {
      results.push({ name, body });
    }
  }
  return results;
}

/** 从 source[startIdx] 开始（startIdx 指向 '{'）提取整个 {} 块 */
function extractBracedBlock(source: string, startIdx: number): string | null {
  if (source[startIdx] !== '{') return null;
  let depth = 0;
  for (let i = startIdx; i < source.length; i++) {
    if (source[i] === '{') depth++;
    else if (source[i] === '}') {
      depth--;
      if (depth === 0) {
        return source.slice(startIdx, i + 1);
      }
    }
  }
  return null;
}

// ===================== 假数据兜底检测模式 =====================

/**
 * 检测函数体内 catch 块中是否存在返回 hardcoded 数据的反模式。
 *
 * 反模式特征：
 * - catch 块内包含 `return { data: [...]}`
 * - catch 块内包含 `return []` 或 `return {}`（非错误对象）
 * - catch 块内包含 `Promise.resolve(` 后跟非错误数据
 * - catch 块内包含 `return` + 字面量数组/对象且不含 error/message/code 关键字
 *
 * 允许的模式：
 * - `throw` / `Promise.reject`
 * - `return Promise.reject(...)`
 * - 返回含 error / message / code 字段的对象
 */
function detectFakeDataInCatch(body: string): {
  hasFallback: boolean;
  detail: string;
} {
  // 提取 catch 块
  const catchRegex = /catch\s*\([^)]*\)\s*\{/g;
  let catchMatch: RegExpExecArray | null;
  while ((catchMatch = catchRegex.exec(body)) !== null) {
    const catchStart = catchMatch.index + catchMatch[0].length - 1;
    const catchBody = extractBracedBlock(body, catchStart);
    if (!catchBody) continue;

    // 模式 1: catch 块中 return 后跟字面量数组
    if (/return\s*\[\s*[^;\n]*\]/.test(catchBody)) {
      // 排除 return [] 作为空错误标识已被其他地方处理的情况
      // 但如果 catch 块里有 return [] 且没有显式 throw 或 reject，则为兜底
      if (
        !catchBody.includes('throw') &&
        !catchBody.includes('reject') &&
        !catchBody.includes('Error')
      ) {
        return {
          hasFallback: true,
          detail: 'catch 块中 return 字面量数组，缺少显式错误传播',
        };
      }
    }

    // 模式 2: catch 块中 return { data: ... } 且无 error/message 字段
    if (/return\s*\{\s*data\s*:/.test(catchBody)) {
      if (
        !/error|message|code/.test(catchBody) ||
        /return\s*\{\s*data\s*:\s*\[/.test(catchBody)
      ) {
        return {
          hasFallback: true,
          detail: 'catch 块中 return { data: [...] }，疑似 hardcoded 假数据兜底',
        };
      }
    }

    // 模式 3: catch 块中 Promise.resolve 后跟非错误数据
    if (
      /Promise\.resolve\s*\(/.test(catchBody) &&
      !/Promise\.resolve\s*\(\s*(?:new\s+Error|null|undefined)/.test(catchBody)
    ) {
      if (!catchBody.includes('throw') && !catchBody.includes('reject')) {
        return {
          hasFallback: true,
          detail:
            'catch 块中使用 Promise.resolve 返回数据，疑似静默兜底',
        };
      }
    }

    // 模式 4: catch 块仅有 return（无 throw/reject/Error），且返回对象不含错误字段
    const returnObjMatch = catchBody.match(
      /return\s*\{([^}]*)\}/
    );
    if (returnObjMatch) {
      const returnedContent = returnObjMatch[1];
      if (
        !catchBody.includes('throw') &&
        !catchBody.includes('reject') &&
        !/error|message|code|err|msg/.test(returnedContent.toLowerCase()) &&
        /data|list|items|records|rows|result/.test(
          returnedContent.toLowerCase()
        )
      ) {
        return {
          hasFallback: true,
          detail:
            'catch 块中 return 含业务数据字段的对象（无错误传播），疑似假数据兜底',
        };
      }
    }
  }

  return { hasFallback: false, detail: '' };
}

/**
 * 检测函数体中是否存在非 catch 位置的静默回退模式：
 * - `.catch(() => defaultData)` 形式的链式静默吞异常
 * - 使用本地常量/变量作为 fallback 响应
 */
function detectSilentFallback(body: string): {
  hasFallback: boolean;
  detail: string;
} {
  // 模式: .catch(() => [...]) 或 .catch(() => ({...})) 且无 throw/reject
  const catchChainRegex =
    /\.catch\s*\(\s*(?:\([^)]*\))?\s*=>\s*(?:\{[^}]*return\s+(?:\[|\{(?!.*(?:error|message|code))))|\.catch\s*\(\s*(?:\([^)]*\))?\s*=>\s*(?:\[|\(\s*\{(?!.*(?:error|message|code)))/;
  if (catchChainRegex.test(body)) {
    return {
      hasFallback: true,
      detail: '.catch 链式静默返回非错误数据',
    };
  }

  // 模式: .catch(() => fakeData) 或 .catch(() => mockData)
  if (
    /\.catch\s*\([^)]*\)\s*=>\s*(?:fake|mock|default|local|placeholder|static)/i.test(
      body
    )
  ) {
    return {
      hasFallback: true,
      detail: '.catch 链式返回疑似假数据变量',
    };
  }

  return { hasFallback: false, detail: '' };
}

/**
 * 检测函数体中是否存在 mock/硬编码 token 的反模式（用于 Property 5）。
 *
 * 反模式：
 * - 硬编码 token 字符串赋值：token = 'xxx' / Bearer xxx
 * - 使用 mock/fake/stub 标识的变量或函数
 * - 跳过验证码验证：不发真实请求直接使用预置 token
 */
function detectMockTokenPath(source: string): {
  hasMock: boolean;
  detail: string;
} {
  // 硬编码 token（排除类型注解和注释）
  const lines = source.split('\n');
  for (const line of lines) {
    const trimmed = line.trim();
    // 跳过注释行
    if (trimmed.startsWith('//') || trimmed.startsWith('*') || trimmed.startsWith('/*')) {
      continue;
    }

    // 检测硬编码 token 赋值
    if (
      /(?:token|authorization)\s*[=:]\s*['"`](?:Bearer\s+)?[A-Za-z0-9._-]{20,}['"`]/i.test(
        trimmed
      )
    ) {
      return {
        hasMock: true,
        detail: `疑似硬编码 token: ${trimmed.slice(0, 80)}`,
      };
    }

    // 检测 mock/fake/stub 函数或变量用于 token
    if (
      /(?:mock|fake|stub|dummy)(?:Token|Auth|Login|Captcha)/i.test(trimmed) &&
      !trimmed.startsWith('//')
    ) {
      return {
        hasMock: true,
        detail: `疑似 mock/fake token 路径: ${trimmed.slice(0, 80)}`,
      };
    }
  }

  // 检测是否有 fallback token 路径（try 失败后使用本地 token）
  if (
    /catch[^{]*\{[^}]*(?:token|setToken|localStorage\.setItem\s*\(\s*['"]token['"])/s.test(
      source
    )
  ) {
    // 在 catch 块中设置 token 说明可能在登录失败时伪造凭证
    // 进一步检查是否是合法的清除 token 操作
    const catchBlocks = source.match(/catch[^{]*\{[^}]*\}/gs) || [];
    for (const block of catchBlocks) {
      if (
        /(?:setToken|localStorage\.setItem\s*\(\s*['"]token['"])/.test(block) &&
        !/removeItem|''|""/.test(block)
      ) {
        return {
          hasMock: true,
          detail: 'catch 块中设置非空 token，疑似伪造凭证',
        };
      }
    }
  }

  return { hasMock: false, detail: '' };
}

// ===================== Property 4: 真实数据不变式 =====================

describe('Property 4: 真实数据不变式 — 前端 api/*.ts 不存在假数据兜底路径', () => {
  const apiFiles = getApiFiles();

  // 预加载所有文件及其导出函数
  const allFunctions: { file: string; name: string; body: string }[] = [];
  for (const file of apiFiles) {
    const source = readSource(file);
    const funcs = extractExportedFunctions(source);
    for (const fn of funcs) {
      allFunctions.push({ file, name: fn.name, body: fn.body });
    }
  }

  it('随机抽取 api 函数，断言 catch 块中无 hardcoded 假数据兜底（需求 5.5, 9.6）', () => {
    // 当函数列表非空时，用 fast-check 随机抽样验证
    fc.assert(
      fc.property(
        fc.integer({ min: 0, max: Math.max(0, allFunctions.length - 1) }),
        (idx) => {
          fc.pre(allFunctions.length > 0);
          const fn = allFunctions[idx];
          const result = detectFakeDataInCatch(fn.body);
          expect(
            result.hasFallback,
            `函数 ${fn.name} (${fn.file}): ${result.detail}`
          ).toBe(false);
        }
      ),
      { numRuns: Math.min(200, allFunctions.length * 3) }
    );
  });

  it('随机抽取 api 函数，断言无链式静默回退模式（需求 9.6, 9.7）', () => {
    fc.assert(
      fc.property(
        fc.integer({ min: 0, max: Math.max(0, allFunctions.length - 1) }),
        (idx) => {
          fc.pre(allFunctions.length > 0);
          const fn = allFunctions[idx];
          const result = detectSilentFallback(fn.body);
          expect(
            result.hasFallback,
            `函数 ${fn.name} (${fn.file}): ${result.detail}`
          ).toBe(false);
        }
      ),
      { numRuns: Math.min(200, allFunctions.length * 3) }
    );
  });

  it('全量扫描所有 api 文件——error 分支要么 throw/reject 要么返回含 error 信息的对象（需求 5.5, 9.7）', () => {
    // 对所有 api 文件的完整源码做宽泛扫描
    for (const file of apiFiles) {
      const source = readSource(file);

      // 检查文件级的 catch 链式写法
      const silentCatchPattern =
        /\.catch\s*\(\s*\(\s*\)\s*=>\s*(?:\[.*?\]|\{(?!.*(?:error|message|code|throw|reject))[^}]*\})/gs;
      const matches = source.match(silentCatchPattern);
      expect(
        matches,
        `文件 ${file} 存在链式 .catch 静默吞异常返回非错误数据`
      ).toBeNull();
    }
  });

  it('全量扫描所有 api 文件——不存在 hardcoded 假数据常量用于兜底（需求 5.6, 9.6）', () => {
    // 扫描文件顶层是否存在 fakeData / mockData / defaultResponse 等用于兜底的常量
    const suspiciousPatterns = [
      /(?:const|let|var)\s+(?:fake|mock|stub|dummy|placeholder|fallback|default)(?:Data|Response|Result|List|Items)/i,
      /(?:const|let|var)\s+\w+\s*=\s*\[[\s\S]{10,}\]\s*(?:\/\/.*fallback|\/\/.*mock|\/\/.*假数据|\/\/.*兜底)/i,
    ];

    for (const file of apiFiles) {
      const source = readSource(file);
      for (const pattern of suspiciousPatterns) {
        const match = source.match(pattern);
        expect(
          match,
          `文件 ${file} 包含疑似用于兜底的假数据常量: ${match?.[0]?.slice(0, 60)}`
        ).toBeNull();
      }
    }
  });
});

// ===================== Property 5: 登录可达性 =====================

describe('Property 5: 登录可达性 — login 函数不含 mock/fallback/hardcoded token 路径', () => {
  it('登录页面 login 实现通过真实 POST /v1/auth/login 完成登录（需求 5.1）', () => {
    const loginSource = readSource(LOGIN_VIEW_PATH);

    // 断言：登录页面中存在对 /v1/auth/login 的真实 POST 请求
    expect(
      loginSource,
      '登录页面应包含对 /v1/auth/login 的 POST 请求'
    ).toMatch(/request\.post\s*\(\s*['"`]\/v1\/auth\/login['"`]/);

    // 断言：登录请求携带 captchaCode 和 captchaUuid 参数（真实验证码）
    expect(
      loginSource,
      '登录请求应携带 captchaCode 参数'
    ).toMatch(/captchaCode/);
    expect(
      loginSource,
      '登录请求应携带 captchaUuid 参数'
    ).toMatch(/captchaUuid/);
  });

  it('登录页面不含 mock/fake/hardcoded token 路径（需求 5.1, 9.6）', () => {
    const loginSource = readSource(LOGIN_VIEW_PATH);
    const result = detectMockTokenPath(loginSource);
    expect(
      result.hasMock,
      `登录页面存在 mock token 路径: ${result.detail}`
    ).toBe(false);
  });

  it('验证码获取通过真实 GET /v1/captcha/image 接口（需求 5.1）', () => {
    const captchaSource = readSource(
      join(API_DIR, 'captcha.ts')
    );

    // 断言：captcha API 使用真实 GET 请求
    expect(captchaSource).toMatch(
      /request\.get\s*\(\s*['"`]\/v1\/captcha\/image['"`]\s*\)/
    );

    // 断言：captcha API 不含 fallback/mock 模式
    const result = detectMockTokenPath(captchaSource);
    expect(
      result.hasMock,
      `captcha.ts 存在 mock 路径: ${result.detail}`
    ).toBe(false);
  });

  it('请求工具（request.ts）错误分支传播错误而非返回假数据（需求 5.5, 9.7）', () => {
    const requestSource = readSource(REQUEST_UTIL_PATH);

    // 断言：响应拦截器错误分支使用 Promise.reject
    expect(
      requestSource,
      '响应拦截器应使用 Promise.reject 传播错误'
    ).toMatch(/Promise\.reject/);

    // 断言：不存在在错误处理中返回假数据的模式
    expect(
      requestSource,
      '请求工具不应在错误处理中返回 hardcoded 数据'
    ).not.toMatch(
      /catch[^{]*\{[^}]*return\s+\{[^}]*data\s*:\s*\[/s
    );

    // 断言：不存在 fallback 相关变量
    expect(requestSource).not.toMatch(
      /(?:fallback|fakeData|mockResponse|defaultData)\s*=/i
    );
  });

  it('登录流程不绕过验证码——不存在跳过 captcha 的条件分支（需求 5.1, 5.2）', () => {
    const loginSource = readSource(LOGIN_VIEW_PATH);

    // 断言：不存在跳过验证码的条件逻辑
    expect(loginSource).not.toMatch(
      /(?:skip|bypass|disable|ignore)(?:Captcha|Verification|验证码)/i
    );

    // 断言：不存在默认/预设验证码值
    expect(loginSource).not.toMatch(
      /captchaCode\s*[=:]\s*['"`]\d{4,}['"`]/
    );

    // 断言：验证码 uuid 来自真实接口响应而非硬编码
    expect(loginSource).not.toMatch(
      /captchaUuid\s*[=:]\s*['"`][0-9a-f-]{36}['"`]/
    );
  });

  it('登录失败时不伪造 token 继续——login catch 分支不设置非空 token（需求 5.2, 9.6）', () => {
    const loginSource = readSource(LOGIN_VIEW_PATH);

    // 提取 handleLogin 函数体
    const handleLoginMatch = loginSource.match(
      /(?:async\s+)?function\s+handleLogin\s*\(\s*\)\s*\{/
    );
    if (handleLoginMatch && handleLoginMatch.index !== undefined) {
      const startIdx =
        handleLoginMatch.index + handleLoginMatch[0].length - 1;
      const fnBody = extractBracedBlock(loginSource, startIdx);
      if (fnBody) {
        // 检查 catch 块中是否有 setToken 或 localStorage.setItem('token', 非空值)
        const catchBlocks = fnBody.match(/catch[^{]*\{[^}]*\}/gs) || [];
        for (const block of catchBlocks) {
          // 允许 catch 中清除 token（removeItem / 设为空字符串）
          if (/setToken|localStorage\.setItem\s*\(\s*['"]token['"]/.test(block)) {
            expect(
              block,
              'catch 块中设置 token 应为清除操作'
            ).toMatch(/removeItem|['"]\s*['"]\s*\)|''\s*\)/);
          }
        }
      }
    }
  });
});
