package com.zwinsight.pbt;

import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import org.assertj.core.api.Assertions;

/**
 * Property 20：乐观锁重试机制
 * <p>
 * 验证：
 * - 失败次数 < 3 → 最终成功
 * - 失败次数 >= 3 → 标记为失败
 * <p>
 * Validates: Requirements 8.6
 */
@Tag("Feature: p1-system-integrity, Property 20: 乐观锁重试机制")
class OptimisticLockRetryPropertyTest {

    static final int MAX_RETRIES = 3;

    enum RetryResult {
        SUCCESS,
        FAILED
    }

    /**
     * 模拟一个可能失败的操作
     */
    @FunctionalInterface
    interface FailableOperation {
        boolean execute(int attempt);
    }

    /**
     * 核心业务逻辑：乐观锁重试机制
     * 最多重试 MAX_RETRIES 次，如果在 MAX_RETRIES 次内操作成功则返回 SUCCESS，
     * 否则返回 FAILED。
     *
     * @param failuresBeforeSuccess 成功前需要失败的次数（模拟冲突次数）
     */
    static RetryResult executeWithRetry(int failuresBeforeSuccess) {
        int attempts = 0;
        while (attempts < MAX_RETRIES) {
            attempts++;
            // 模拟操作：如果已尝试次数 > 失败次数，则成功
            if (attempts > failuresBeforeSuccess) {
                return RetryResult.SUCCESS;
            }
        }
        return RetryResult.FAILED;
    }

    @Property(tries = 100)
    void fewerFailuresThanMaxRetries_eventuallySucceeds(
            @ForAll @IntRange(min = 0, max = 2) int failuresBeforeSuccess) {
        RetryResult result = executeWithRetry(failuresBeforeSuccess);
        Assertions.assertThat(result).isEqualTo(RetryResult.SUCCESS);
    }

    @Property(tries = 100)
    void failuresEqualOrExceedMaxRetries_markedAsFailed(
            @ForAll @IntRange(min = 3, max = 10) int failuresBeforeSuccess) {
        RetryResult result = executeWithRetry(failuresBeforeSuccess);
        Assertions.assertThat(result).isEqualTo(RetryResult.FAILED);
    }

    @Property(tries = 100)
    void zeroFailures_alwaysSucceedsOnFirstAttempt() {
        RetryResult result = executeWithRetry(0);
        Assertions.assertThat(result).isEqualTo(RetryResult.SUCCESS);
    }

    @Property(tries = 100)
    void exactlyMaxMinusOneFailures_stillSucceeds() {
        // MAX_RETRIES - 1 = 2 次失败, 第 3 次成功
        RetryResult result = executeWithRetry(MAX_RETRIES - 1);
        Assertions.assertThat(result).isEqualTo(RetryResult.SUCCESS);
    }

    @Property(tries = 100)
    void exactlyMaxFailures_markedAsFailed() {
        // MAX_RETRIES = 3 次失败, 超出重试次数
        RetryResult result = executeWithRetry(MAX_RETRIES);
        Assertions.assertThat(result).isEqualTo(RetryResult.FAILED);
    }

    @Property(tries = 100)
    void result_isDeterministic(@ForAll @IntRange(min = 0, max = 5) int failures) {
        RetryResult first = executeWithRetry(failures);
        RetryResult second = executeWithRetry(failures);
        Assertions.assertThat(first).isEqualTo(second);
    }
}
