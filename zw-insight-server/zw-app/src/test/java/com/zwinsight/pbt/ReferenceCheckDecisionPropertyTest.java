package com.zwinsight.pbt;

import net.jqwik.api.*;
import org.assertj.core.api.Assertions;

/**
 * Property 14：引用校验决策逻辑
 * <p>
 * 验证：
 * - 引用计数 > 0 → 删除被阻止
 * - 引用计数 == 0 → 删除被允许
 * <p>
 * Validates: Requirements 6.1
 */
@Tag("Feature: p1-system-integrity, Property 14: 引用校验决策逻辑")
class ReferenceCheckDecisionPropertyTest {

    enum DeleteDecision {
        ALLOWED,
        BLOCKED
    }

    /**
     * 核心业务逻辑：根据引用计数决定是否允许删除
     */
    static DeleteDecision checkDeleteAllowed(long referenceCount) {
        if (referenceCount > 0) {
            return DeleteDecision.BLOCKED;
        }
        return DeleteDecision.ALLOWED;
    }

    @Property(tries = 100)
    void positiveReferenceCount_blocksDelete(
            @ForAll("positiveCount") long count) {
        DeleteDecision decision = checkDeleteAllowed(count);
        Assertions.assertThat(decision).isEqualTo(DeleteDecision.BLOCKED);
    }

    @Property(tries = 100)
    void zeroReferenceCount_allowsDelete() {
        DeleteDecision decision = checkDeleteAllowed(0L);
        Assertions.assertThat(decision).isEqualTo(DeleteDecision.ALLOWED);
    }

    @Property(tries = 100)
    void decision_isDeterministic(@ForAll("anyCount") long count) {
        DeleteDecision first = checkDeleteAllowed(count);
        DeleteDecision second = checkDeleteAllowed(count);
        Assertions.assertThat(first).isEqualTo(second);
    }

    @Property(tries = 100)
    void decision_isBinary(@ForAll("anyCount") long count) {
        DeleteDecision decision = checkDeleteAllowed(count);
        Assertions.assertThat(decision).isIn(DeleteDecision.ALLOWED, DeleteDecision.BLOCKED);
    }

    @Provide
    Arbitrary<Long> positiveCount() {
        return Arbitraries.longs().between(1L, 10000L);
    }

    @Provide
    Arbitrary<Long> anyCount() {
        return Arbitraries.longs().between(0L, 10000L);
    }
}
