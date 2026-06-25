import { describe, it, expect } from 'vitest';
import { test, fc } from '@fast-check/vitest';

describe('项目初始化验证', () => {
  it('Vitest 配置正常工作', () => {
    expect(1 + 1).toBe(2);
  });

  test.prop([fc.integer(), fc.integer()])('fast-check 配置正常工作：加法交换律', (a, b) => {
    expect(a + b).toBe(b + a);
  });
});
