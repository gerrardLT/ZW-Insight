package com.zwinsight.common.event.outbox;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 发件箱投递状态枚举单元测试。
 *
 * <p>{@link OutboxStatus#fromCode(String)} 的契约是「读取侧宽容，写入侧严格」：
 * 未知值返回 null 而非抛异常，避免历史脏数据让消费链路整体崩溃。</p>
 */
class OutboxStatusTest {

    @Test
    @DisplayName("正常：精确匹配大写枚举名")
    void fromCode_exactMatch() {
        assertThat(OutboxStatus.fromCode("PENDING")).isEqualTo(OutboxStatus.PENDING);
        assertThat(OutboxStatus.fromCode("DELIVERING")).isEqualTo(OutboxStatus.DELIVERING);
        assertThat(OutboxStatus.fromCode("DELIVERED")).isEqualTo(OutboxStatus.DELIVERED);
        assertThat(OutboxStatus.fromCode("DEAD")).isEqualTo(OutboxStatus.DEAD);
    }

    @Test
    @DisplayName("正常：大小写不敏感（DB 里可能存小写）")
    void fromCode_caseInsensitive() {
        assertThat(OutboxStatus.fromCode("pending")).isEqualTo(OutboxStatus.PENDING);
        assertThat(OutboxStatus.fromCode("Dead")).isEqualTo(OutboxStatus.DEAD);
        assertThat(OutboxStatus.fromCode("dElIvErEd")).isEqualTo(OutboxStatus.DELIVERED);
    }

    @Test
    @DisplayName("正常：首尾空白被 trim 后再匹配")
    void fromCode_trimsWhitespace() {
        assertThat(OutboxStatus.fromCode("  PENDING  ")).isEqualTo(OutboxStatus.PENDING);
        assertThat(OutboxStatus.fromCode("\tDEAD\n")).isEqualTo(OutboxStatus.DEAD);
    }

    @Test
    @DisplayName("边界：null 返回 null，不抛 NPE")
    void fromCode_null_returnsNull() {
        assertThat(OutboxStatus.fromCode(null)).isNull();
    }

    @Test
    @DisplayName("边界：空串与纯空白返回 null")
    void fromCode_blank_returnsNull() {
        assertThat(OutboxStatus.fromCode("")).isNull();
        assertThat(OutboxStatus.fromCode("   ")).isNull();
        assertThat(OutboxStatus.fromCode("\t\n")).isNull();
    }

    @Test
    @DisplayName("异常：未知状态码返回 null（宽容读取，不抛异常）")
    void fromCode_unknown_returnsNull() {
        assertThat(OutboxStatus.fromCode("FAILED")).isNull();
        assertThat(OutboxStatus.fromCode("UNKNOWN_STATUS")).isNull();
        assertThat(OutboxStatus.fromCode("PENDINGX")).isNull();
    }

    /**
     * 契约钉住：状态集合只有 4 个，且**不存在 FAILED**。
     *
     * <p>{@code OutboxEvent} 的类级 Javadoc 状态机图与 status 字段注释都写了 FAILED，
     * 但枚举里并无该值——投递失败实际由 {@code OutboxDispatcher.handleFailure} 回落为
     * PENDING（安排重试）或转为 DEAD（超上限）。此用例防止有人照着过时注释
     * 新增 FAILED 状态或写出永远匹配不上的分支。</p>
     */
    @Test
    @DisplayName("契约：状态集合恰为 PENDING/DELIVERING/DELIVERED/DEAD 四态，无 FAILED")
    void statusSet_isExactlyFourStates_withoutFailed() {
        List<String> names = Arrays.stream(OutboxStatus.values())
                .map(Enum::name)
                .collect(Collectors.toList());

        assertThat(names).containsExactlyInAnyOrder("PENDING", "DELIVERING", "DELIVERED", "DEAD");
        assertThat(names).doesNotContain("FAILED");
    }

    @Test
    @DisplayName("契约：所有枚举值经 name() 写入后都能被 fromCode 往返解析")
    void allValues_roundTripThroughFromCode() {
        for (OutboxStatus status : OutboxStatus.values()) {
            assertThat(OutboxStatus.fromCode(status.name())).isEqualTo(status);
        }
    }
}
