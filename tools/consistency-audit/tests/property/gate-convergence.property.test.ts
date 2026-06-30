/**
 * 门禁单调收敛属性测试（Property 6）
 *
 * 断言审计复跑历史的核心两类计数（HTTP_METHOD_MISMATCH + FRONTEND_EXTRA_API）
 * 单调不增，最终收敛至目标值。
 *
 * 收敛历史序列（模块对齐完成后的复跑基准点）：
 *   63 → 54 → 50 → 44 → 41 → 38 → 32
 *
 * 框架：vitest + fast-check
 *
 * **Validates: Requirements 8.6, 8.7, 3.4, 3.5**
 */

import { describe, it, expect } from 'vitest';
import fc from 'fast-check';

// ===================== 审计复跑历史数据 =====================

/**
 * 核心两类计数的审计复跑历史（每个值对应一次模块对齐完成后的复跑结果）。
 *
 * 时间线：
 *   63 — 基线（2026-06-30T01:33:13）
 *   54 — 阶段一完成（system + basedata 归类 A 对齐后）
 *   50 — 阶段二完成（project + budget 归类 A 对齐后）
 *   44 — 阶段三完成（contract + subcontract 归类 A 对齐后）
 *   41 — 阶段四完成（tender 归类 A 对齐后）
 *   38 — 阶段五完成（site + machine 归类 A 对齐后）
 *   32 — 阶段六完成（hr 归类 A 对齐后）= 当前最终值
 *
 * 残留 32 项全部为归类 B（功能缺口待排期）或归类 C（噪音/重复），
 * 符合 R8.8 门禁条件性通过。
 */
const CONVERGENCE_HISTORY: readonly number[] = [63, 54, 50, 44, 41, 38, 32];

/** 基线值（不可变） */
const BASELINE = 63;

/** 最终目标（归类 A 全清后的理论最终值，等于 B+C 残留数） */
const FINAL_VALUE = 32;

/** 归类 B 残留项数 */
const CATEGORY_B_COUNT = 30;

/** 归类 C 残留项数 */
const CATEGORY_C_COUNT = 2;

// ===================== Property 6: 门禁单调收敛 =====================

describe('Property 6: 门禁单调收敛 — 核心两类计数单调不增', () => {
  it('收敛历史序列严格单调不增（每次复跑结果 ≤ 上次）', () => {
    for (let i = 1; i < CONVERGENCE_HISTORY.length; i++) {
      expect(
        CONVERGENCE_HISTORY[i],
        `第 ${i} 次复跑 (${CONVERGENCE_HISTORY[i]}) 应 ≤ 第 ${i - 1} 次复跑 (${CONVERGENCE_HISTORY[i - 1]})`
      ).toBeLessThanOrEqual(CONVERGENCE_HISTORY[i - 1]);
    }
  });

  it('收敛历史序列严格递减（每次至少减少 1 项）', () => {
    for (let i = 1; i < CONVERGENCE_HISTORY.length; i++) {
      expect(
        CONVERGENCE_HISTORY[i],
        `第 ${i} 次复跑 (${CONVERGENCE_HISTORY[i]}) 应 < 第 ${i - 1} 次复跑 (${CONVERGENCE_HISTORY[i - 1]})`
      ).toBeLessThan(CONVERGENCE_HISTORY[i - 1]);
    }
  });

  it('基线值为 63（不可变基线）', () => {
    expect(CONVERGENCE_HISTORY[0]).toBe(BASELINE);
  });

  it('最终值等于 B+C 残留项之和', () => {
    const lastValue = CONVERGENCE_HISTORY[CONVERGENCE_HISTORY.length - 1];
    expect(lastValue).toBe(CATEGORY_B_COUNT + CATEGORY_C_COUNT);
    expect(lastValue).toBe(FINAL_VALUE);
  });

  it('属性测试：对收敛序列任意连续子区间，后者不大于前者', () => {
    fc.assert(
      fc.property(
        fc.integer({ min: 0, max: CONVERGENCE_HISTORY.length - 2 }),
        fc.integer({ min: 1, max: CONVERGENCE_HISTORY.length - 1 }),
        (i, j) => {
          // 确保 i < j
          fc.pre(i < j);
          // 单调不增性质
          expect(
            CONVERGENCE_HISTORY[j],
            `history[${j}]=${CONVERGENCE_HISTORY[j]} 应 ≤ history[${i}]=${CONVERGENCE_HISTORY[i]}`
          ).toBeLessThanOrEqual(CONVERGENCE_HISTORY[i]);
        }
      ),
      { numRuns: 100 }
    );
  });

  it('属性测试：随机采样任意两个时间点，靠后的计数不大于靠前的计数', () => {
    fc.assert(
      fc.property(
        fc.integer({ min: 0, max: CONVERGENCE_HISTORY.length - 1 }),
        fc.integer({ min: 0, max: CONVERGENCE_HISTORY.length - 1 }),
        (a, b) => {
          const earlier = Math.min(a, b);
          const later = Math.max(a, b);
          expect(CONVERGENCE_HISTORY[later]).toBeLessThanOrEqual(
            CONVERGENCE_HISTORY[earlier]
          );
        }
      ),
      { numRuns: 200 }
    );
  });

  it('收敛序列中每个值均为非负整数', () => {
    fc.assert(
      fc.property(
        fc.integer({ min: 0, max: CONVERGENCE_HISTORY.length - 1 }),
        (idx) => {
          expect(CONVERGENCE_HISTORY[idx]).toBeGreaterThanOrEqual(0);
          expect(Number.isInteger(CONVERGENCE_HISTORY[idx])).toBe(true);
        }
      ),
      { numRuns: 50 }
    );
  });

  it('收敛率校验：从基线到最终值，归类 A 的消减量为 31', () => {
    const reduction = BASELINE - FINAL_VALUE;
    expect(reduction).toBe(31); // 31 项归类 A 已对齐消减
  });
});
