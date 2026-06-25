package com.zwinsight.pbt;

import net.jqwik.api.*;
import org.assertj.core.api.Assertions;

/**
 * Property 19：回滚冲突检测
 * <p>
 * 验证：
 * - 快照值与当前值不同 → 标记冲突
 * - 快照值与当前值相同 → 无冲突
 * <p>
 * Validates: Requirements 8.8
 */
@Tag("Feature: p1-system-integrity, Property 19: 回滚冲突检测")
class RollbackConflictDetectionPropertyTest {

    enum ConflictStatus {
        NO_CONFLICT,
        CONFLICT_DETECTED
    }

    /**
     * 核心业务逻辑：检测回滚冲突
     * 比较快照值（审批提交时的原始值）和当前数据库值：
     * - 如果相同，说明未被第三方修改，可安全回滚
     * - 如果不同，说明已被其他操作修改，存在冲突
     */
    static ConflictStatus detectConflict(String snapshotValue, String currentValue) {
        if (snapshotValue == null && currentValue == null) {
            return ConflictStatus.NO_CONFLICT;
        }
        if (snapshotValue != null && snapshotValue.equals(currentValue)) {
            return ConflictStatus.NO_CONFLICT;
        }
        return ConflictStatus.CONFLICT_DETECTED;
    }

    @Property(tries = 100)
    void sameValues_noConflict(@ForAll("fieldValues") String value) {
        ConflictStatus status = detectConflict(value, value);
        Assertions.assertThat(status).isEqualTo(ConflictStatus.NO_CONFLICT);
    }

    @Property(tries = 100)
    void differentValues_conflict(
            @ForAll("fieldValues") String snapshotValue,
            @ForAll("fieldValues") String currentValue) {
        // 确保两个值确实不同
        if (snapshotValue.equals(currentValue)) {
            return; // skip 相等的情况
        }
        ConflictStatus status = detectConflict(snapshotValue, currentValue);
        Assertions.assertThat(status).isEqualTo(ConflictStatus.CONFLICT_DETECTED);
    }

    @Property(tries = 100)
    void nullSnapshot_withNonNullCurrent_isConflict(
            @ForAll("fieldValues") String currentValue) {
        ConflictStatus status = detectConflict(null, currentValue);
        Assertions.assertThat(status).isEqualTo(ConflictStatus.CONFLICT_DETECTED);
    }

    @Property(tries = 100)
    void nonNullSnapshot_withNullCurrent_isConflict(
            @ForAll("fieldValues") String snapshotValue) {
        ConflictStatus status = detectConflict(snapshotValue, null);
        Assertions.assertThat(status).isEqualTo(ConflictStatus.CONFLICT_DETECTED);
    }

    @Property(tries = 100)
    void bothNull_noConflict() {
        ConflictStatus status = detectConflict(null, null);
        Assertions.assertThat(status).isEqualTo(ConflictStatus.NO_CONFLICT);
    }

    @Provide
    Arbitrary<String> fieldValues() {
        return Arbitraries.oneOf(
                Arbitraries.integers().between(0, 999999).map(String::valueOf),
                Arbitraries.of("DRAFT", "SUBMITTED", "APPROVED", "REJECTED"),
                Arbitraries.bigDecimals()
                        .between(java.math.BigDecimal.ZERO, java.math.BigDecimal.valueOf(1000000))
                        .ofScale(2).map(Object::toString)
        );
    }
}
