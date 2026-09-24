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
import static org.mockito.Mockito.times;
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

    // =====================================================================
    // §10 下钻 8 级链（V2026_69）
    // =====================================================================

    @Nested
    @DisplayName("getDrillChain()/updateDrillInfo() §10 下钻八级链")
    class DrillChainTests {

        private com.zwinsight.finance.dto.ReceivableDrillInfoRequest request() {
            return new com.zwinsight.finance.dto.ReceivableDrillInfoRequest();
        }

        @SuppressWarnings("unchecked")
        private List<Map<String, Object>> chainOf(Map<String, Object> result) {
            return (List<Map<String, Object>>) result.get("chain");
        }

        @Test
        @DisplayName("正常路径 — 八级链按 §10 顺序与标签返回，已登记项带真实值")
        void drillChain_eightLevelsInOrder() {
            BizReceivable r = openReceivable(1L, "5200000", "2000000", LocalDate.now().minusDays(47));
            r.setProjectId(90001L);
            r.setMilestoneNode("主体结构封顶");
            r.setApplyDate(LocalDate.now().minusDays(60));
            r.setOwnerReviewStatus(BizReceivable.REVIEW_UNDER_REVIEW);
            r.setOwnerReviewDate(LocalDate.now().minusDays(40));
            r.setOwnerName("张伟");
            r.setOwnerId(90071L);
            r.setNextAction("本周内找甲方财务对账");
            when(receivableMapper.selectById(1L)).thenReturn(r);
            com.zwinsight.project.domain.BizProject project = new com.zwinsight.project.domain.BizProject();
            project.setId(90001L);
            project.setProjectName("滨江花园一期");
            when(projectMapper.selectById(90001L)).thenReturn(project);

            Map<String, Object> result = receivableService.getDrillChain(1L);

            List<Map<String, Object>> chain = chainOf(result);
            assertThat(chain).hasSize(8);
            assertThat(chain).extracting(l -> l.get("label")).containsExactly(
                    "项目", "应收款", "对应工程节点", "应收日期", "实际申请日期",
                    "甲方审核状态", "负责人", "下一步动作");
            assertThat(chain).extracting(l -> l.get("level")).containsExactly(1, 2, 3, 4, 5, 6, 7, 8);
            assertThat(chain.get(0).get("value")).isEqualTo("滨江花园一期");
            assertThat(chain.get(2).get("value")).isEqualTo("主体结构封顶");
            // 甲方审核状态返回中文标签（不直接把 SUBMITTED/UNDER_REVIEW 丢给老板看）
            assertThat(chain.get(5).get("value")).isEqualTo("甲方审核中");
            assertThat(chain.get(7).get("value")).isEqualTo("本周内找甲方财务对账");
            assertThat(result.get("complete")).isEqualTo(true);
            assertThat(result.get("unregisteredCount")).isEqualTo(0L);
        }

        @Test
        @DisplayName("口径纪律 — 未登记项标 registered=false 且 value 为 null，不用默认值冒充已登记")
        void drillChain_unregisteredNotDefaulted() {
            BizReceivable r = openReceivable(2L, "1000000", "0", LocalDate.now().plusDays(10));
            r.setProjectId(90002L);
            when(receivableMapper.selectById(2L)).thenReturn(r);
            when(projectMapper.selectById(90002L)).thenReturn(null);

            Map<String, Object> result = receivableService.getDrillChain(2L);

            List<Map<String, Object>> chain = chainOf(result);
            // 工程节点/申请日期/审核状态/负责人/下一步动作 均未登记
            assertThat(chain.get(2).get("registered")).isEqualTo(false);
            assertThat(chain.get(2).get("value")).isNull();
            assertThat(chain.get(4).get("registered")).isEqualTo(false);
            assertThat(chain.get(5).get("value")).isNull();
            assertThat(chain.get(6).get("registered")).isEqualTo(false);
            assertThat(chain.get(7).get("registered")).isEqualTo(false);
            // 项目名为 null（项目不存在）也如实标未登记，不拿 ID 字符串冒充名称
            assertThat(chain.get(0).get("registered")).isEqualTo(false);
            // 未到期且 OPEN → overdue=false、agingBucket 为 null（不给“未逾期”以外的分档）
            assertThat(result.get("overdue")).isEqualTo(false);
            assertThat(result.get("agingBucket")).isNull();
            assertThat(result.get("ownerStayDays")).isNull();
            assertThat(result.get("complete")).isEqualTo(false);
            assertThat(result.get("unregisteredCount")).isEqualTo(6L);
        }

        @Test
        @DisplayName("派生值 — 未结清余额下限 0、逾期天数与账龄分档、甲方停留天数")
        void drillChain_derivedValues() {
            // 超收场景：已核销 > 应收 → 余额归 0（不出负数，超收事实由回款单据体现）
            BizReceivable over = openReceivable(3L, "100000", "150000", LocalDate.now());
            when(receivableMapper.selectById(3L)).thenReturn(over);
            assertThat((BigDecimal) receivableService.getDrillChain(3L).get("openBalance"))
                    .isEqualByComparingTo("0");

            // 逾期 47 天 → D31_60 分档
            BizReceivable r = openReceivable(4L, "5200000", "2000000", LocalDate.now().minusDays(47));
            r.setApplyDate(LocalDate.now().minusDays(60));
            r.setOwnerReviewDate(LocalDate.now().minusDays(40));
            when(receivableMapper.selectById(4L)).thenReturn(r);
            Map<String, Object> result = receivableService.getDrillChain(4L);
            assertThat(result.get("overdue")).isEqualTo(true);
            assertThat(result.get("overdueDays")).isEqualTo(47L);
            assertThat(result.get("agingBucket")).isEqualTo("D31_60");
            assertThat((BigDecimal) result.get("openBalance")).isEqualByComparingTo("3200000");
            // 甲方停留 = 审核日 − 申请日 = 20 天
            assertThat(result.get("ownerStayDays")).isEqualTo(20L);

            // 已结清（CLOSED）即使到期日已过也不算逾期
            BizReceivable closed = openReceivable(5L, "100000", "100000", LocalDate.now().minusDays(30));
            closed.setStatus(BizReceivable.STATUS_CLOSED);
            when(receivableMapper.selectById(5L)).thenReturn(closed);
            assertThat(receivableService.getDrillChain(5L).get("overdue")).isEqualTo(false);
        }

        @Test
        @DisplayName("维护语义 — null 保留原值、空串清空登记（不得把“只想改一项”变成抹掉其他项）")
        void updateDrillInfo_nullKeeps_emptyClears() {
            BizReceivable r = openReceivable(6L, "100000", "0", LocalDate.now());
            r.setMilestoneNode("地基验收");
            r.setNextAction("原动作");
            r.setOwnerName("李娜");
            r.setOwnerId(90072L);
            when(receivableMapper.selectById(6L)).thenReturn(r);

            // 只改下一步动作：其余字段传 null → 保留原值
            com.zwinsight.finance.dto.ReceivableDrillInfoRequest req = request();
            req.setNextAction("新动作");
            BizReceivable updated = receivableService.updateDrillInfo(6L, req);
            assertThat(updated.getNextAction()).isEqualTo("新动作");
            assertThat(updated.getMilestoneNode()).isEqualTo("地基验收");
            assertThat(updated.getOwnerName()).isEqualTo("李娜");
            assertThat(updated.getOwnerId()).isEqualTo(90072L);

            // 传空串 → 清空工程节点；同时清负责人姓名则 ownerId 一并撤回
            com.zwinsight.finance.dto.ReceivableDrillInfoRequest clear = request();
            clear.setMilestoneNode("   ");
            clear.setOwnerName("");
            BizReceivable cleared = receivableService.updateDrillInfo(6L, clear);
            assertThat(cleared.getMilestoneNode()).isNull();
            assertThat(cleared.getOwnerName()).isNull();
            assertThat(cleared.getOwnerId()).isNull();
            // 未传的下一步动作仍保留（不被本次清空操作误伤）
            assertThat(cleared.getNextAction()).isEqualTo("新动作");
            verify(receivableMapper, times(2)).updateById(any(BizReceivable.class));
        }

        @Test
        @DisplayName("口径纪律 — 非法审核状态报 400（不静默当作未登记）；清空状态时连带清审核日期")
        void updateDrillInfo_reviewStatusValidation() {
            BizReceivable r = openReceivable(7L, "100000", "0", LocalDate.now());
            r.setOwnerReviewStatus(BizReceivable.REVIEW_CONFIRMED);
            r.setOwnerReviewDate(LocalDate.now().minusDays(5));
            when(receivableMapper.selectById(7L)).thenReturn(r);

            com.zwinsight.finance.dto.ReceivableDrillInfoRequest bad = request();
            bad.setOwnerReviewStatus("WHATEVER");
            assertThatThrownBy(() -> receivableService.updateDrillInfo(7L, bad))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("甲方审核状态不合法");
            // 非法值不得落库
            verify(receivableMapper, never()).updateById(any(BizReceivable.class));

            // 合法值大小写不敏感（统一存大写）
            com.zwinsight.finance.dto.ReceivableDrillInfoRequest ok = request();
            ok.setOwnerReviewStatus("disputed");
            assertThat(receivableService.updateDrillInfo(7L, ok).getOwnerReviewStatus())
                    .isEqualTo(BizReceivable.REVIEW_DISPUTED);

            // 清空状态 → 审核日期一并撤回（不残留孤立日期）
            com.zwinsight.finance.dto.ReceivableDrillInfoRequest clear = request();
            clear.setOwnerReviewStatus("");
            BizReceivable cleared = receivableService.updateDrillInfo(7L, clear);
            assertThat(cleared.getOwnerReviewStatus()).isNull();
            assertThat(cleared.getOwnerReviewDate()).isNull();
        }

        @Test
        @DisplayName("异常路径 — 记录不存在报 404，缺 ID/请求体报 400（不静默返回空链）")
        void drill_invalidArgs_rejected() {
            when(receivableMapper.selectById(999L)).thenReturn(null);
            assertThatThrownBy(() -> receivableService.getDrillChain(999L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("不存在");
            assertThatThrownBy(() -> receivableService.getDrillChain(null))
                    .isInstanceOf(BusinessException.class);
            assertThatThrownBy(() -> receivableService.updateDrillInfo(null, request()))
                    .isInstanceOf(BusinessException.class);
            assertThatThrownBy(() -> receivableService.updateDrillInfo(1L, null))
                    .isInstanceOf(BusinessException.class);
        }

        /**
         * ORM 契约测试（针对 2026-09-24 L3 实测发现的真实缺陷）。
         * <p>MyBatis-Plus {@code updateById} 默认字段策略 NOT_NULL：实体中置 null 的列不会
         * 出现在 UPDATE 语句里 → 「空串=清空登记」在 Service 层正确置了 null，但库里仍是旧值。
         * 该缺陷<b>单测无法发现</b>（mock 的 updateById 不模拟 ORM 字段策略），是 L3 契约测试抛出的。
         * 本用例用反射钉住注解，防止后人“清理冗余注解”时静默退回该缺陷。</p>
         */
        @Test
        @DisplayName("ORM 契约 — 可清空字段必须标 FieldStrategy.ALWAYS，否则 updateById 会忽略 null 使清空失效")
        void clearableFieldsMustUseAlwaysUpdateStrategy() throws Exception {
            for (String name : List.of("milestoneNode", "ownerReviewStatus", "ownerReviewDate",
                    "ownerId", "ownerName", "nextAction")) {
                java.lang.reflect.Field f = BizReceivable.class.getDeclaredField(name);
                com.baomidou.mybatisplus.annotation.TableField tf =
                        f.getAnnotation(com.baomidou.mybatisplus.annotation.TableField.class);
                assertThat(tf).as("%s 必须标 @TableField（否则清空不生效）", name).isNotNull();
                assertThat(tf.updateStrategy())
                        .as("%s 的 updateStrategy 必须为 ALWAYS（null 也要写入 UPDATE）", name)
                        .isEqualTo(com.baomidou.mybatisplus.annotation.FieldStrategy.ALWAYS);
            }
            // applyDate 故意不标 ALWAYS：日期字段不支持清空，默认 NOT_NULL 策略恰好实现「null=不修改」
            java.lang.reflect.Field applyDate = BizReceivable.class.getDeclaredField("applyDate");
            com.baomidou.mybatisplus.annotation.TableField tf =
                    applyDate.getAnnotation(com.baomidou.mybatisplus.annotation.TableField.class);
            assertThat(tf == null || tf.updateStrategy()
                    != com.baomidou.mybatisplus.annotation.FieldStrategy.ALWAYS)
                    .as("applyDate 不应标 ALWAYS（日期不支持清空，标了会把「不修改」变成「置空」）")
                    .isTrue();
        }
    }
}
