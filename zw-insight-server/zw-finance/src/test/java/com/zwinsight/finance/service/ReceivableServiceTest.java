package com.zwinsight.finance.service;

import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.finance.domain.BizProjectSettlement;
import com.zwinsight.finance.domain.BizReceivable;
import com.zwinsight.finance.domain.BizReceivableWriteOff;
import com.zwinsight.finance.mapper.BizReceivableMapper;
import com.zwinsight.finance.mapper.BizReceivableWriteOffMapper;
import com.zwinsight.project.mapper.BizProjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ReceivableService 单元测试（V2026_57 应收台账）
 * <p>核心断言：结算生成应收（幂等/正差额守卫）、FIFO 核销落明细、
 * 反冲对称恢复、账龄分桶、项目应收单值同事务双写。</p>
 */
@ExtendWith(MockitoExtension.class)
class ReceivableServiceTest {

    @Mock private BizReceivableMapper receivableMapper;
    @Mock private BizReceivableWriteOffMapper writeOffMapper;
    @Mock private BizProjectMapper projectMapper;

    @InjectMocks
    private ReceivableService receivableService;

    @BeforeEach
    void setUp() {
        // @Value 字段在纯 Mockito 环境不注入，显式设默认账期
        ReflectionTestUtils.setField(receivableService, "defaultCreditDays", 30);
    }

    private BizProjectSettlement settlement(Long id, String output, String received, String finalAmount) {
        BizProjectSettlement s = new BizProjectSettlement();
        s.setId(id);
        s.setProjectId(10L);
        s.setSettlementCode("JS-TEST-" + id);
        s.setCumulativeOutput(output != null ? new BigDecimal(output) : null);
        s.setCumulativeReceived(received != null ? new BigDecimal(received) : null);
        s.setFinalSettlementAmount(finalAmount != null ? new BigDecimal(finalAmount) : null);
        return s;
    }

    private BizReceivable openReceivable(Long id, String amount, String writtenOff, LocalDate dueDate) {
        BizReceivable r = new BizReceivable();
        r.setId(id);
        r.setProjectId(10L);
        r.setReceivableAmount(new BigDecimal(amount));
        r.setWrittenOffAmount(new BigDecimal(writtenOff));
        r.setDueDate(dueDate);
        r.setStatus(BizReceivable.STATUS_OPEN);
        return r;
    }

    @Nested
    @DisplayName("generateFromSettlement() 结算生成应收")
    class GenerateTests {

        @Test
        @DisplayName("正常路径 — 最终结算额优先，正差额入台账并双写项目应收")
        void generate_positiveDiff_insertsAndWritesBack() {
            when(receivableMapper.selectCount(any())).thenReturn(0L);

            BizReceivable result = receivableService.generateFromSettlement(
                    settlement(100L, "800000", "300000", "1000000"));

            ArgumentCaptor<BizReceivable> captor = ArgumentCaptor.forClass(BizReceivable.class);
            verify(receivableMapper).insert(captor.capture());
            // 应收 = 最终结算 100万 − 累计收款 30万 = 70万（finalSettlementAmount 优先于 cumulativeOutput）
            assertThat(captor.getValue().getReceivableAmount()).isEqualByComparingTo("700000");
            assertThat(captor.getValue().getDueDate()).isEqualTo(LocalDate.now().plusDays(30));
            assertThat(captor.getValue().getStatus()).isEqualTo(BizReceivable.STATUS_OPEN);
            verify(projectMapper).addReceivableAmount(10L, captor.getValue().getReceivableAmount());
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("正常路径 — 无最终结算额时回退累计产值口径")
        void generate_noFinalAmount_fallbackToOutput() {
            when(receivableMapper.selectCount(any())).thenReturn(0L);

            receivableService.generateFromSettlement(settlement(101L, "500000", "200000", null));

            ArgumentCaptor<BizReceivable> captor = ArgumentCaptor.forClass(BizReceivable.class);
            verify(receivableMapper).insert(captor.capture());
            assertThat(captor.getValue().getReceivableAmount()).isEqualByComparingTo("300000");
        }

        @Test
        @DisplayName("幂等守卫 — 同结算单已有台账记录时跳过，不重复双写")
        void generate_alreadyExists_skipped() {
            when(receivableMapper.selectCount(any())).thenReturn(1L);

            BizReceivable result = receivableService.generateFromSettlement(
                    settlement(100L, "800000", "300000", "1000000"));

            assertThat(result).isNull();
            verify(receivableMapper, never()).insert(any());
            verify(projectMapper, never()).addReceivableAmount(any(), any());
        }

        @Test
        @DisplayName("边界守卫 — 已收足（差额≤0）不生成应收，不伪造负数台账")
        void generate_nonPositiveDiff_skipped() {
            when(receivableMapper.selectCount(any())).thenReturn(0L);

            BizReceivable result = receivableService.generateFromSettlement(
                    settlement(102L, "500000", "500000", null));

            assertThat(result).isNull();
            verify(receivableMapper, never()).insert(any());
            verify(projectMapper, never()).addReceivableAmount(any(), any());
        }

        @Test
        @DisplayName("异常路径 — 结算单为空被拒绝")
        void generate_nullSettlement_rejected() {
            assertThatThrownBy(() -> receivableService.generateFromSettlement(null))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("结算单不存在");
        }
    }

    @Nested
    @DisplayName("writeOff() FIFO 核销")
    class WriteOffTests {

        @Test
        @DisplayName("正常路径 — 按到期日 FIFO 冲减：先到期结清 CLOSED，后到期部分核销，明细逐笔落库")
        void writeOff_fifoAcrossTwoReceivables() {
            BizReceivable first = openReceivable(1L, "300000", "0", LocalDate.now().minusDays(10));
            BizReceivable second = openReceivable(2L, "500000", "0", LocalDate.now().plusDays(10));
            when(receivableMapper.selectList(any())).thenReturn(List.of(first, second));

            BigDecimal written = receivableService.writeOff(900L, 10L, new BigDecimal("400000"));

            assertThat(written).isEqualByComparingTo("400000");
            // 台账更新走原子 SQL（addWrittenOffAmount：累加 + 状态切换收敛在 DB 层，防并发丢失更新）
            verify(receivableMapper).addWrittenOffAmount(1L, new BigDecimal("300000"));
            verify(receivableMapper).addWrittenOffAmount(2L, new BigDecimal("100000"));
            // 明细逐笔落库（2 笔）+ 项目应收单值同事务冲减
            verify(writeOffMapper, org.mockito.Mockito.times(2)).insert(any(BizReceivableWriteOff.class));
            verify(projectMapper).addReceivableAmount(eq(10L), eq(new BigDecimal("400000").negate()));
        }

        @Test
        @DisplayName("边界路径 — 回款超 OPEN 余额：核销以余额为上限，超出部分不冲减（预收事实）")
        void writeOff_exceedsOpenBalance_cappedAtBalance() {
            BizReceivable only = openReceivable(1L, "100000", "0", LocalDate.now());
            when(receivableMapper.selectList(any())).thenReturn(List.of(only));

            BigDecimal written = receivableService.writeOff(901L, 10L, new BigDecimal("150000"));

            assertThat(written).isEqualByComparingTo("100000");
            verify(receivableMapper).addWrittenOffAmount(1L, new BigDecimal("100000"));
            verify(projectMapper).addReceivableAmount(eq(10L), eq(new BigDecimal("100000").negate()));
        }

        @Test
        @DisplayName("边界路径 — 无 OPEN 台账：核销 0，不动项目应收单值")
        void writeOff_noOpenReceivable_noOp() {
            when(receivableMapper.selectList(any())).thenReturn(List.of());

            BigDecimal written = receivableService.writeOff(902L, 10L, new BigDecimal("50000"));

            assertThat(written).isEqualByComparingTo(BigDecimal.ZERO);
            verify(receivableMapper, never()).addWrittenOffAmount(any(), any());
            verify(projectMapper, never()).addReceivableAmount(any(), any());
        }

        @Test
        @DisplayName("异常路径 — 非正核销金额被拒绝")
        void writeOff_nonPositiveAmount_rejected() {
            assertThatThrownBy(() -> receivableService.writeOff(903L, 10L, BigDecimal.ZERO))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("核销金额必须大于0");
        }
    }

    @Nested
    @DisplayName("reverseWriteOff() 反冲核销")
    class ReverseTests {

        @Test
        @DisplayName("正常路径 — 按明细回退：余额原子冲减、项目应收回增、明细删除")
        void reverse_restoresState() {
            BizReceivable closed = openReceivable(1L, "300000", "300000", LocalDate.now());
            closed.setStatus(BizReceivable.STATUS_CLOSED);
            when(receivableMapper.selectById(1L)).thenReturn(closed);
            BizReceivableWriteOff detail = new BizReceivableWriteOff();
            detail.setId(500L);
            detail.setReceivableId(1L);
            detail.setPaymentReceivedId(900L);
            detail.setAmount(new BigDecimal("300000"));
            when(writeOffMapper.selectList(any())).thenReturn(List.of(detail));

            BigDecimal total = receivableService.reverseWriteOff(900L);

            assertThat(total).isEqualByComparingTo("300000");
            // 负 delta 原子冲减，状态由 SQL 层 IF 自动切回 OPEN（不再依赖内存对象改写）
            verify(receivableMapper).addWrittenOffAmount(1L, new BigDecimal("300000").negate());
            verify(projectMapper).addReceivableAmount(10L, new BigDecimal("300000"));
            verify(writeOffMapper).deleteById(500L);
        }

        @Test
        @DisplayName("异常容错路径 — 明细额超当前已核销额时按实际可反冲额回加（不变量严格保持，不静默）")
        void reverse_detailExceedsCurrentWrittenOff_cappedToActual() {
            // 构造数据异常：明细记 500000，但台账当前仅核销 300000（并发或人工改库）
            BizReceivable inconsistent = openReceivable(1L, "600000", "300000", LocalDate.now());
            when(receivableMapper.selectById(1L)).thenReturn(inconsistent);
            BizReceivableWriteOff detail = new BizReceivableWriteOff();
            detail.setId(501L);
            detail.setReceivableId(1L);
            detail.setPaymentReceivedId(901L);
            detail.setAmount(new BigDecimal("500000"));
            when(writeOffMapper.selectList(any())).thenReturn(List.of(detail));

            BigDecimal total = receivableService.reverseWriteOff(901L);

            // 返回与回加的是实际可反冲额（300000），否则项目单值会虚高于台账 OPEN 合计
            assertThat(total).isEqualByComparingTo("300000");
            verify(projectMapper).addReceivableAmount(10L, new BigDecimal("300000"));
            verify(receivableMapper).addWrittenOffAmount(1L, new BigDecimal("500000").negate());
        }

        @Test
        @DisplayName("边界路径 — 无核销明细时返回 0，不产生任何回写")
        void reverse_noDetails_noOp() {
            when(writeOffMapper.selectList(any())).thenReturn(List.of());

            BigDecimal total = receivableService.reverseWriteOff(999L);

            assertThat(total).isEqualByComparingTo(BigDecimal.ZERO);
            verify(projectMapper, never()).addReceivableAmount(any(), any());
        }
    }

    @Nested
    @DisplayName("aging() 账龄分析")
    class AgingTests {

        @Test
        @DisplayName("正常路径 — 按逾期天数正确分桶并汇总逾期余额")
        @SuppressWarnings("unchecked")
        void aging_bucketsComputed() {
            LocalDate today = LocalDate.now();
            BizReceivable notDue = openReceivable(1L, "100000", "0", today.plusDays(5));
            BizReceivable overdue20 = openReceivable(2L, "200000", "50000", today.minusDays(20));
            BizReceivable overdue100 = openReceivable(3L, "300000", "0", today.minusDays(100));
            when(receivableMapper.selectList(any())).thenReturn(List.of(notDue, overdue20, overdue100));

            Map<String, Object> result = receivableService.aging(null);

            assertThat((BigDecimal) result.get("totalOpen")).isEqualByComparingTo("550000");
            assertThat((BigDecimal) result.get("totalOverdue")).isEqualByComparingTo("450000");
            List<Map<String, Object>> projects = (List<Map<String, Object>>) result.get("projects");
            assertThat(projects).hasSize(1);
            Map<String, BigDecimal> buckets = (Map<String, BigDecimal>) projects.get(0).get("buckets");
            assertThat(buckets.get("NOT_DUE")).isEqualByComparingTo("100000");
            assertThat(buckets.get("D0_30")).isEqualByComparingTo("150000");
            assertThat(buckets.get("OVER_90")).isEqualByComparingTo("300000");
            assertThat((Long) projects.get(0).get("maxOverdueDays")).isEqualTo(100L);
        }
    }

    @Nested
    @DisplayName("openBalanceByDueMonth() 预测收款侧数据源")
    class DueMonthTests {

        @Test
        @DisplayName("正常路径 — OPEN 余额按到期日落月聚合，已结清/零余额不计入")
        void dueMonth_aggregated() {
            LocalDate next = LocalDate.now().plusMonths(1);
            BizReceivable r1 = openReceivable(1L, "100000", "0", next);
            BizReceivable r2 = openReceivable(2L, "200000", "50000", next);
            BizReceivable closed = openReceivable(3L, "300000", "300000", next);
            closed.setStatus(BizReceivable.STATUS_CLOSED);
            // selectList 已按 OPEN 过滤，此处仅模拟返回（CLOSED 不会出现，验证零余额守卫用 r3 置 OPEN 零余额）
            closed.setStatus(BizReceivable.STATUS_OPEN);
            when(receivableMapper.selectList(any())).thenReturn(List.of(r1, r2, closed));

            Map<String, BigDecimal> byMonth = receivableService.openBalanceByDueMonth(null);

            String monthKey = String.format("%d-%02d", next.getYear(), next.getMonthValue());
            // 100000 + 150000 + 0（零余额不计） = 250000
            assertThat(byMonth.get(monthKey)).isEqualByComparingTo("250000");
        }
    }
}
