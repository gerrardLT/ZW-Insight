package com.zwinsight.common.event.contract;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 变更事件「已批准」领域事件契约单元测试。
 *
 * <p>本契约是 zw-common 共享内核里的 Published Language，生产方为 zw-contract，
 * 消费方横跨 zw-budget（CBS 调整）/ zw-dashboard（看板读模型）。字段语义一旦漂移，
 * 下游会算出相反的账，故此处逐方法钉住行为。</p>
 *
 * <p>重点覆盖 {@link ChangeEventApprovedEvent.AccountDelta#toSignedAmount()} 的
 * 符号约定——方向（deltaType）与数值（deltaAmount 绝对值）分离存储，
 * 计算时必须收敛为单一符号量。</p>
 */
class ChangeEventApprovedEventTest {

    // ==================== AccountDelta.toSignedAmount ====================

    @Nested
    @DisplayName("AccountDelta.toSignedAmount — 方向与数值收敛为带符号金额")
    class ToSignedAmountTests {

        @Test
        @DisplayName("正常：INCREASE 保持正号")
        void increase_keepsPositive() {
            ChangeEventApprovedEvent.AccountDelta delta = delta("INCREASE", "5000.00");

            assertThat(delta.toSignedAmount()).isEqualByComparingTo("5000.00");
        }

        @Test
        @DisplayName("正常：DECREASE 翻转为负号")
        void decrease_negates() {
            ChangeEventApprovedEvent.AccountDelta delta = delta("DECREASE", "5000.00");

            assertThat(delta.toSignedAmount()).isEqualByComparingTo("-5000.00");
        }

        @Test
        @DisplayName("边界：deltaType 大小写不敏感（decrease 同样翻转）")
        void decrease_isCaseInsensitive() {
            assertThat(delta("decrease", "100").toSignedAmount()).isEqualByComparingTo("-100");
            assertThat(delta("DeCrEaSe", "100").toSignedAmount()).isEqualByComparingTo("-100");
        }

        @Test
        @DisplayName("边界：deltaAmount 为 null 时按 ZERO 处理，不抛 NPE")
        void nullAmount_treatedAsZero() {
            ChangeEventApprovedEvent.AccountDelta delta = new ChangeEventApprovedEvent.AccountDelta();
            delta.setDeltaType("INCREASE");
            delta.setDeltaAmount(null);

            assertThat(delta.toSignedAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("边界：DECREASE + null 金额 → ZERO（negate 后仍为 0）")
        void decreaseWithNullAmount_isZero() {
            ChangeEventApprovedEvent.AccountDelta delta = new ChangeEventApprovedEvent.AccountDelta();
            delta.setDeltaType("DECREASE");
            delta.setDeltaAmount(null);

            assertThat(delta.toSignedAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("异常：deltaType 为 null 时走默认正号分支（非 DECREASE 即视为增加）")
        void nullDeltaType_defaultsToPositive() {
            ChangeEventApprovedEvent.AccountDelta delta = new ChangeEventApprovedEvent.AccountDelta();
            delta.setDeltaType(null);
            delta.setDeltaAmount(new BigDecimal("300"));

            assertThat(delta.toSignedAmount()).isEqualByComparingTo("300");
        }

        @Test
        @DisplayName("异常：未知 deltaType 同样走正号分支（宽容读取，不抛异常）")
        void unknownDeltaType_defaultsToPositive() {
            assertThat(delta("UNKNOWN", "300").toSignedAmount()).isEqualByComparingTo("300");
        }

        @Test
        @DisplayName("边界：DECREASE 传入已是负数的金额会双重取负变正（契约要求存绝对值）")
        void decreaseWithNegativeInput_doubleNegates() {
            // 契约约定 deltaAmount 存绝对值、方向由 deltaType 决定。
            // 若生产方违约存入负数，DECREASE 会 negate 成正数——此用例钉住该行为，
            // 提醒消费方不能靠它纠正生产方的符号错误。
            assertThat(delta("DECREASE", "-500").toSignedAmount()).isEqualByComparingTo("500");
        }
    }

    // ==================== hasCostImpact ====================

    @Nested
    @DisplayName("hasCostImpact — 是否存在需传导的成本影响")
    class HasCostImpactTests {

        @Test
        @DisplayName("正常：costDelta 为正 → true")
        void positiveDelta_isTrue() {
            ChangeEventApprovedEvent event = new ChangeEventApprovedEvent();
            event.setCostDelta(new BigDecimal("1000"));

            assertThat(event.hasCostImpact()).isTrue();
        }

        @Test
        @DisplayName("正常：costDelta 为负（节约）→ true")
        void negativeDelta_isTrue() {
            ChangeEventApprovedEvent event = new ChangeEventApprovedEvent();
            event.setCostDelta(new BigDecimal("-1000"));

            assertThat(event.hasCostImpact()).isTrue();
        }

        @Test
        @DisplayName("边界：costDelta 为 0 → false（纯范围/工期变更短路，不触碰 CBS）")
        void zeroDelta_isFalse() {
            ChangeEventApprovedEvent event = new ChangeEventApprovedEvent();
            event.setCostDelta(BigDecimal.ZERO);

            assertThat(event.hasCostImpact()).isFalse();
        }

        @Test
        @DisplayName("边界：costDelta 为 0.00（不同标度）→ false，signum 不受标度影响")
        void zeroWithScale_isFalse() {
            ChangeEventApprovedEvent event = new ChangeEventApprovedEvent();
            event.setCostDelta(new BigDecimal("0.00"));

            assertThat(event.hasCostImpact()).isFalse();
        }

        @Test
        @DisplayName("异常：costDelta 为 null → false，不抛 NPE")
        void nullDelta_isFalse() {
            ChangeEventApprovedEvent event = new ChangeEventApprovedEvent();
            event.setCostDelta(null);

            assertThat(event.hasCostImpact()).isFalse();
        }
    }

    // ==================== hasAccountDetail ====================

    @Nested
    @DisplayName("hasAccountDetail — 是否存在明细级账户调整指令")
    class HasAccountDetailTests {

        @Test
        @DisplayName("正常：含一条明细 → true")
        void withOneDetail_isTrue() {
            ChangeEventApprovedEvent event = new ChangeEventApprovedEvent();
            event.getAffectedAccounts().add(delta("INCREASE", "100"));

            assertThat(event.hasAccountDetail()).isTrue();
        }

        @Test
        @DisplayName("边界：字段默认初始化为空列表（非 null）→ false")
        void defaultEmptyList_isFalse() {
            ChangeEventApprovedEvent event = new ChangeEventApprovedEvent();

            assertThat(event.getAffectedAccounts()).isNotNull().isEmpty();
            assertThat(event.hasAccountDetail()).isFalse();
        }

        @Test
        @DisplayName("异常：被显式置为 null → false，不抛 NPE")
        void explicitNull_isFalse() {
            ChangeEventApprovedEvent event = new ChangeEventApprovedEvent();
            event.setAffectedAccounts(null);

            assertThat(event.hasAccountDetail()).isFalse();
        }
    }

    // ==================== sumAccountDeltas ====================

    @Nested
    @DisplayName("sumAccountDeltas — 明细合计（与 costDelta 交叉校验用）")
    class SumAccountDeltasTests {

        @Test
        @DisplayName("正常：INCREASE 与 DECREASE 混合求和得净额")
        void mixedDeltas_sumToNet() {
            ChangeEventApprovedEvent event = new ChangeEventApprovedEvent();
            event.setAffectedAccounts(new ArrayList<>(Arrays.asList(
                    delta("INCREASE", "5000.00"),
                    delta("DECREASE", "2000.00"),
                    delta("INCREASE", "500.50"))));

            // 5000 - 2000 + 500.50 = 3500.50
            assertThat(event.sumAccountDeltas()).isEqualByComparingTo("3500.50");
        }

        @Test
        @DisplayName("正常：合计可与 costDelta 交叉校验（自洽数据两者相等）")
        void sumMatchesCostDelta_whenConsistent() {
            ChangeEventApprovedEvent event = new ChangeEventApprovedEvent();
            event.setCostDelta(new BigDecimal("3000"));
            event.setAffectedAccounts(new ArrayList<>(Arrays.asList(
                    delta("INCREASE", "5000"),
                    delta("DECREASE", "2000"))));

            assertThat(event.sumAccountDeltas()).isEqualByComparingTo(event.getCostDelta());
        }

        @Test
        @DisplayName("边界：空列表 → ZERO")
        void emptyList_isZero() {
            ChangeEventApprovedEvent event = new ChangeEventApprovedEvent();

            assertThat(event.sumAccountDeltas()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("异常：列表含 null 元素时跳过，不抛 NPE")
        void listWithNullElement_skipped() {
            ChangeEventApprovedEvent event = new ChangeEventApprovedEvent();
            List<ChangeEventApprovedEvent.AccountDelta> list = new ArrayList<>();
            list.add(delta("INCREASE", "1000"));
            list.add(null);
            list.add(delta("DECREASE", "300"));
            event.setAffectedAccounts(list);

            assertThat(event.sumAccountDeltas()).isEqualByComparingTo("700");
        }

        @Test
        @DisplayName("异常：affectedAccounts 为 null → ZERO，不抛 NPE")
        void nullList_isZero() {
            ChangeEventApprovedEvent event = new ChangeEventApprovedEvent();
            event.setAffectedAccounts(null);

            assertThat(event.sumAccountDeltas()).isEqualByComparingTo(BigDecimal.ZERO);
        }
    }

    // ==================== 契约稳定性 ====================

    @Test
    @DisplayName("契约：schemaVersion 默认 1（消费方据此做兼容分支，缺省视为 1）")
    void schemaVersion_defaultsToOne() {
        assertThat(new ChangeEventApprovedEvent().getSchemaVersion()).isEqualTo(1);
    }

    @Test
    @DisplayName("契约：affectedWbsIds 默认初始化为空列表（非 null），消费方可直接遍历")
    void affectedWbsIds_defaultsToEmptyList() {
        assertThat(new ChangeEventApprovedEvent().getAffectedWbsIds()).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("契约：全字段可读写（Lombok @Data 生成的访问器齐备，供 Jackson 反序列化）")
    void allFields_areReadWrite() {
        ChangeEventApprovedEvent event = new ChangeEventApprovedEvent();
        LocalDateTime approvedAt = LocalDateTime.of(2026, 9, 4, 12, 30);

        event.setSchemaVersion(2);
        event.setEventId(9001L);
        event.setEventNumber("BG20260904001");
        event.setProjectId(90001L);
        event.setTenantId(9999L);
        event.setCostDelta(new BigDecimal("1234.56"));
        event.setScheduleDelayDays(3);
        event.setAffectedWbsIds(Arrays.asList(1L, 2L));
        event.setApprovedAt(approvedAt);
        event.setApprovedBy(100L);

        assertThat(event.getSchemaVersion()).isEqualTo(2);
        assertThat(event.getEventId()).isEqualTo(9001L);
        assertThat(event.getEventNumber()).isEqualTo("BG20260904001");
        assertThat(event.getProjectId()).isEqualTo(90001L);
        assertThat(event.getTenantId()).isEqualTo(9999L);
        assertThat(event.getCostDelta()).isEqualByComparingTo("1234.56");
        assertThat(event.getScheduleDelayDays()).isEqualTo(3);
        assertThat(event.getAffectedWbsIds()).containsExactly(1L, 2L);
        assertThat(event.getApprovedAt()).isEqualTo(approvedAt);
        assertThat(event.getApprovedBy()).isEqualTo(100L);
    }

    // ==================== 辅助 ====================

    private static ChangeEventApprovedEvent.AccountDelta delta(String type, String amount) {
        ChangeEventApprovedEvent.AccountDelta d = new ChangeEventApprovedEvent.AccountDelta();
        d.setAccountId(1L);
        d.setDeltaType(type);
        d.setDeltaAmount(new BigDecimal(amount));
        return d;
    }
}
