/**
 * 统一 REST 约定判定函数单元测试（具体样例与边界）
 *
 * 以 rest-convention.md 判定表中的典型路径为样例，校验
 * classifyRestOperation 对各操作类型与边界情形的判定。
 *
 * Validates: Requirements 1.2, 1.3, 1.4, 1.5, 1.6, 1.7, 2.2, 3.2
 */

import { describe, it, expect } from 'vitest';
import {
  classifyRestOperation,
  normalizeMethod,
  normalizePath,
  REST_OPERATION_LABELS,
} from '../../src/auditors/rest-convention.js';

describe('classifyRestOperation — 判定表样例', () => {
  it('列表（根 GET）', () => {
    expect(classifyRestOperation('GET', '/v1/basedata/material')).toBe('list');
    expect(classifyRestOperation('GET', '/api/v1/basedata/material')).toBe('list');
  });

  it('详情（GET /{id}）', () => {
    expect(classifyRestOperation('GET', '/v1/basedata/material/12')).toBe('detail');
    expect(classifyRestOperation('GET', '/v1/basedata/material/{id}')).toBe('detail');
  });

  it('新增（POST 根）', () => {
    expect(classifyRestOperation('POST', '/v1/basedata/material')).toBe('create');
  });

  it('更新（PUT /{id}）', () => {
    expect(classifyRestOperation('PUT', '/v1/basedata/material/12')).toBe('update');
  });

  it('单条删除（DELETE /{id}）', () => {
    expect(classifyRestOperation('DELETE', '/v1/basedata/material/12')).toBe('delete');
  });

  it('批量删除（DELETE /batch）', () => {
    expect(classifyRestOperation('DELETE', '/v1/basedata/material/batch')).toBe('batchDelete');
  });

  it('动作（POST /{id}/<action>）', () => {
    expect(classifyRestOperation('POST', '/v1/subcontract/settlement/12/submit')).toBe('action');
    expect(classifyRestOperation('POST', '/v1/system/user/12/status')).toBe('action');
  });

  it('典型错位：PUT 根路径（id 入 body）→ 不符合约定', () => {
    expect(classifyRestOperation('PUT', '/v1/basedata/material')).toBe('nonCompliant');
  });

  it('GET /batch 非批量删除 → 不符合约定', () => {
    expect(classifyRestOperation('GET', '/v1/basedata/material/batch')).toBe('nonCompliant');
  });
});

describe('规范化辅助', () => {
  it('normalizeMethod 大小写归一并去空白', () => {
    expect(normalizeMethod(' get ')).toBe('GET');
    expect(normalizeMethod('Delete')).toBe('DELETE');
  });

  it('normalizePath 去尾部斜杠', () => {
    expect(normalizePath('/v1/basedata/material/')).toBe('/v1/basedata/material');
    expect(normalizePath('/v1/basedata/material///')).toBe('/v1/basedata/material');
    expect(normalizePath('/')).toBe('/');
  });

  it('尾部斜杠不改变判定（列表）', () => {
    expect(classifyRestOperation('get', '/v1/basedata/material/')).toBe('list');
  });
});

describe('REST_OPERATION_LABELS 中文标签完整', () => {
  it('覆盖全部八类操作', () => {
    expect(REST_OPERATION_LABELS.list).toBe('列表');
    expect(REST_OPERATION_LABELS.detail).toBe('详情');
    expect(REST_OPERATION_LABELS.create).toBe('新增');
    expect(REST_OPERATION_LABELS.update).toBe('更新');
    expect(REST_OPERATION_LABELS.delete).toBe('删除');
    expect(REST_OPERATION_LABELS.batchDelete).toBe('批量删除');
    expect(REST_OPERATION_LABELS.action).toBe('动作');
    expect(REST_OPERATION_LABELS.nonCompliant).toBe('不符合约定');
  });
});
