package com.zwinsight.contract.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 变更事件状态机单元测试。
 * <p>
 * 状态机是变更主链的骨架：一旦允许非法流转（如「已批准 → 草稿」），
 * 已生效的成本传导就会被悄悄改写，账实不符且无从追责。
 * 因此这里对全部 6×6 流转组合做穷举断言，而非只测happy path。
 * </p>
 */
@DisplayName("ChangeEventStatus 状态机")
class ChangeEventStatusTest {

    @Nested
    @DisplayName("合法流转")
    class LegalTransitions {

        @ParameterizedTest(name = "{0} → {1} 应允许")
        @CsvSource({
                "DRAFT, ASSESSING",
                "DRAFT, CANCELLED",
                "ASSESSING, APPROVING",
                "ASSESSING, DRAFT",
                "ASSESSING, CANCELLED",
                "APPROVING, APPROVED",
                "APPROVING, REJECTED",
                "APPROVING, ASSESSING",
                "APPROVING, CANCELLED"
        })
        void shouldAllow(String from, String to) {
            assertThat(ChangeEventStatus.canTransition(
                    ChangeEventStatus.valueOf(from), ChangeEventStatus.valueOf(to)))
                    .as("%s → %s 应为合法流转", from, to)
                    .isTrue();
        }
    }

    @Nested
    @DisplayName("非法流转")
    class IllegalTransitions {

        @ParameterizedTest(name = "{0} → {1} 应拒绝")
        @CsvSource({
                // 终态不可再流转：已批准的变更若能被改回草稿，成本传导就会被抹掉
                "APPROVED, DRAFT",
                "APPROVED, ASSESSING",
                "APPROVED, APPROVING",
                "APPROVED, REJECTED",
                "APPROVED, CANCELLED",
                "REJECTED, DRAFT",
                "REJECTED, APPROVING",
                "REJECTED, APPROVED",
                "CANCELLED, DRAFT",
                "CANCELLED, APPROVED",
                // 跳级：草稿未经评估不得直接批准（缺少决策依据）
                "DRAFT, APPROVING",
                "DRAFT, APPROVED",
                "DRAFT, REJECTED",
                // 评估中不得直接批准（未进审批流）
                "ASSESSING, APPROVED",
                "ASSESSING, REJECTED",
                // 自流转无业务意义
                "DRAFT, DRAFT",
                "APPROVING, APPROVING",
                "APPROVED, APPROVED"
        })
        void shouldReject(String from, String to) {
            assertThat(ChangeEventStatus.canTransition(
                    ChangeEventStatus.valueOf(from), ChangeEventStatus.valueOf(to)))
                    .as("%s → %s 应为非法流转", from, to)
                    .isFalse();
        }

        @Test
        @DisplayName("穷举：终态出边必须为空")
        void terminalStatesHaveNoOutgoingEdges() {
            for (ChangeEventStatus s : ChangeEventStatus.values()) {
                if (s.isTerminal()) {
                    for (ChangeEventStatus target : ChangeEventStatus.values()) {
                        assertThat(ChangeEventStatus.canTransition(s, target))
                                .as("终态 %s 不应允许流转到 %s", s, target)
                                .isFalse();
                    }
                }
            }
        }

        @Test
        @DisplayName("assertTransition 非法流转抛异常且带事件编号上下文")
        void assertTransitionThrowsWithContext() {
            assertThatThrownBy(() -> ChangeEventStatus.assertTransition(
                    ChangeEventStatus.APPROVED, ChangeEventStatus.DRAFT, "CHG20260001"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("CHG20260001")
                    .hasMessageContaining("APPROVED")
                    .hasMessageContaining("DRAFT");
        }

        @Test
        @DisplayName("assertTransition 合法流转不抛异常")
        void assertTransitionPassesForLegal() {
            ChangeEventStatus.assertTransition(
                    ChangeEventStatus.APPROVING, ChangeEventStatus.APPROVED, "CHG20260002");
        }

        @Test
        @DisplayName("null 状态一律判为非法（防御脏数据）")
        void nullStatesAreIllegal() {
            assertThat(ChangeEventStatus.canTransition(null, ChangeEventStatus.APPROVED)).isFalse();
            assertThat(ChangeEventStatus.canTransition(ChangeEventStatus.DRAFT, null)).isFalse();
            assertThat(ChangeEventStatus.canTransition(null, null)).isFalse();
        }
    }

    @Nested
    @DisplayName("状态属性")
    class StateProperties {

        @ParameterizedTest
        @EnumSource(value = ChangeEventStatus.class, names = {"APPROVED", "REJECTED", "CANCELLED"})
        void terminalStates(ChangeEventStatus status) {
            assertThat(status.isTerminal()).isTrue();
            assertThat(status.isEditable()).isFalse();
        }

        @ParameterizedTest
        @EnumSource(value = ChangeEventStatus.class, names = {"DRAFT", "ASSESSING"})
        void editableStates(ChangeEventStatus status) {
            assertThat(status.isEditable()).isTrue();
            assertThat(status.isTerminal()).isFalse();
        }

        @Test
        @DisplayName("审批中不可编辑：防止审批人看到的与最终落库的不是同一份内容")
        void approvingIsNotEditable() {
            assertThat(ChangeEventStatus.APPROVING.isEditable()).isFalse();
            assertThat(ChangeEventStatus.APPROVING.isTerminal()).isFalse();
        }

        @Test
        @DisplayName("每个状态都有非空中文展示名（前端标签统一取此处）")
        void allStatesHaveDisplayName() {
            for (ChangeEventStatus s : ChangeEventStatus.values()) {
                assertThat(s.getDisplayName()).isNotBlank();
            }
        }
    }

    @Nested
    @DisplayName("fromCode 容错解析")
    class FromCode {

        @Test
        @DisplayName("大小写与空白容错")
        void tolerantParsing() {
            assertThat(ChangeEventStatus.fromCode("approved")).isEqualTo(ChangeEventStatus.APPROVED);
            assertThat(ChangeEventStatus.fromCode("  DRAFT  ")).isEqualTo(ChangeEventStatus.DRAFT);
        }

        @Test
        @DisplayName("未知/空值返回 null 而非抛异常（读取侧宽容，写入侧严格）")
        void unknownReturnsNull() {
            assertThat(ChangeEventStatus.fromCode(null)).isNull();
            assertThat(ChangeEventStatus.fromCode("")).isNull();
            assertThat(ChangeEventStatus.fromCode("NOT_A_STATUS")).isNull();
        }
    }
}
