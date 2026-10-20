package com.zwinsight.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.zwinsight.finance.domain.BizPaymentApply;
import com.zwinsight.finance.domain.BizReceivable;
import com.zwinsight.finance.mapper.BizBankFlowMapper;
import com.zwinsight.finance.mapper.BizInvoiceReceivedMapper;
import com.zwinsight.finance.mapper.BizPaymentApplyMapper;
import com.zwinsight.finance.mapper.BizReceivableMapper;
import com.zwinsight.project.domain.BizProject;
import com.zwinsight.project.mapper.BizProjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FinanceWorkbenchServiceTest {

    @Mock private BizPaymentApplyMapper paymentApplyMapper;
    @Mock private BizReceivableMapper receivableMapper;
    @Mock private BizInvoiceReceivedMapper invoiceReceivedMapper;
    @Mock private BizBankFlowMapper bankFlowMapper;
    @Mock private BizProjectMapper projectMapper;

    @InjectMocks private FinanceWorkbenchService service;

    private Map<String, Object> agg(long count, String amount) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("cnt", count);
        row.put("amt", new BigDecimal(amount));
        return row;
    }

    private BizPaymentApply payment(Long id, Long projectId, String amount, LocalDate date) {
        BizPaymentApply row = new BizPaymentApply();
        row.setId(id);
        row.setProjectId(projectId);
        row.setSupplierName("供应商" + id);
        row.setPaymentAmount(new BigDecimal(amount));
        row.setPaymentDate(date);
        row.setStatus("APPROVED");
        row.setPayStatus("UNPAID");
        return row;
    }

    private BizReceivable receivable(Long id, Long projectId, String amount, String writtenOff, LocalDate dueDate) {
        BizReceivable row = new BizReceivable();
        row.setId(id);
        row.setProjectId(projectId);
        row.setReceivableAmount(new BigDecimal(amount));
        row.setWrittenOffAmount(new BigDecimal(writtenOff));
        row.setDueDate(dueDate);
        row.setStatus("OPEN");
        return row;
    }

    private BizProject project(Long id, String name, String owner) {
        BizProject project = new BizProject();
        project.setId(id);
        project.setProjectName(name);
        project.setOwnerCompanyName(owner);
        return project;
    }

    @Test
    @DisplayName("空数据返回 0 与空数组，不返回 null")
    @SuppressWarnings("unchecked")
    void summary_emptyReturnsZeros() {
        when(paymentApplyMapper.selectMaps(any())).thenReturn(List.of());
        when(receivableMapper.selectMaps(any())).thenReturn(List.of());
        when(invoiceReceivedMapper.selectMaps(any())).thenReturn(List.of());
        when(paymentApplyMapper.selectList(any())).thenReturn(List.of());
        when(receivableMapper.selectList(any())).thenReturn(List.of());

        Map<String, Object> result = service.summary();

        Map<String, Object> payable = (Map<String, Object>) result.get("payable");
        Map<String, Object> receivable = (Map<String, Object>) result.get("receivable");
        assertThat(payable.get("pendingApprovalCount")).isEqualTo(0L);
        assertThat(payable.get("approvedUnpaidAmount")).isEqualTo(BigDecimal.ZERO);
        assertThat(payable.get("unclaimedInvoiceCount")).isEqualTo(0L);
        assertThat(receivable.get("overdueCount")).isEqualTo(0L);
        assertThat((List<?>) result.get("topOverduePayments")).isEmpty();
        assertThat((List<?>) result.get("topOverdueReceivables")).isEmpty();
    }

    @Test
    @DisplayName("待审批按全额聚合，已批未付/逾期按剩余未付额聚合，TOP5 逾期天数正确")
    @SuppressWarnings("unchecked")
    void summary_aggregatesAndTopLists() {
        when(paymentApplyMapper.selectMaps(any())).thenReturn(List.of(agg(2, "3000")));
        when(receivableMapper.selectMaps(any())).thenReturn(
                List.of(agg(4, "10000")),
                List.of(agg(2, "4000")));
        when(invoiceReceivedMapper.selectMaps(any())).thenReturn(List.of(agg(2, "600")));

        LocalDate today = LocalDate.now();
        // 已批未付：3 笔剩余 5000；其中逾期 1 笔剩余 800
        when(paymentApplyMapper.sumApprovedUnpaidAgg(isNull())).thenReturn(agg(3, "5000"));
        when(paymentApplyMapper.sumApprovedUnpaidAgg(today)).thenReturn(agg(1, "800"));
        when(paymentApplyMapper.selectList(any())).thenReturn(List.of(
                payment(1L, 1L, "800", today.minusDays(5))));
        // TOP5 金额按剩余未付额：800 − 已勾稽 300 = 500
        when(bankFlowMapper.matchedAmountByPaymentApply()).thenReturn(Map.of(1L, new BigDecimal("300")));
        when(receivableMapper.selectList(any())).thenReturn(List.of(
                receivable(2L, 2L, "5000", "1000", today.minusDays(10))));
        when(projectMapper.selectBatchIds(any())).thenReturn(List.of(
                project(1L, "滨江花园一期", "建设单位A"),
                project(2L, "城南市政道路", "建设单位B")));

        Map<String, Object> result = service.summary();

        Map<String, Object> payable = (Map<String, Object>) result.get("payable");
        assertThat(payable.get("pendingApprovalCount")).isEqualTo(2L);
        assertThat((BigDecimal) payable.get("pendingApprovalAmount")).isEqualByComparingTo("3000");
        assertThat(payable.get("approvedUnpaidCount")).isEqualTo(3L);
        assertThat((BigDecimal) payable.get("approvedUnpaidAmount")).isEqualByComparingTo("5000");
        assertThat(payable.get("overdueCount")).isEqualTo(1L);
        assertThat((BigDecimal) payable.get("overdueAmount")).isEqualByComparingTo("800");
        assertThat((BigDecimal) payable.get("unclaimedInvoiceAmount")).isEqualByComparingTo("600");

        Map<String, Object> receivable = (Map<String, Object>) result.get("receivable");
        assertThat(receivable.get("openCount")).isEqualTo(4L);
        assertThat((BigDecimal) receivable.get("overdueAmount")).isEqualByComparingTo("4000");

        List<Map<String, Object>> topPayments = (List<Map<String, Object>>) result.get("topOverduePayments");
        assertThat(topPayments).hasSize(1);
        assertThat(topPayments.get(0).get("projectName")).isEqualTo("滨江花园一期");
        assertThat((BigDecimal) topPayments.get(0).get("amount")).isEqualByComparingTo("500");
        assertThat(topPayments.get(0).get("overdueDays")).isEqualTo(5L);

        List<Map<String, Object>> topReceivables = (List<Map<String, Object>>) result.get("topOverdueReceivables");
        assertThat(topReceivables).hasSize(1);
        assertThat(topReceivables.get(0).get("counterpartyName")).isEqualTo("建设单位B");
        assertThat((BigDecimal) topReceivables.get(0).get("amount")).isEqualByComparingTo("4000");
        assertThat(topReceivables.get(0).get("overdueDays")).isEqualTo(10L);
    }

    @Test
    @DisplayName("待审批只查 SUBMITTED；已批未付与逾期走剩余额聚合且以今天为逾期边界")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void summary_payableSqlConditionsIsolated() {
        when(paymentApplyMapper.selectMaps(any())).thenReturn(List.of());
        when(paymentApplyMapper.sumApprovedUnpaidAgg(any())).thenReturn(new LinkedHashMap<>());
        when(receivableMapper.selectMaps(any())).thenReturn(List.of(), List.of());
        when(invoiceReceivedMapper.selectMaps(any())).thenReturn(List.of());
        when(paymentApplyMapper.selectList(any())).thenReturn(List.of());
        when(receivableMapper.selectList(any())).thenReturn(List.of());

        service.summary();

        // 待审批：只按 status 过滤，不带付款状态与付款日期（尚未付款，全额计）
        ArgumentCaptor<QueryWrapper> captor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(paymentApplyMapper).selectMaps(captor.capture());
        QueryWrapper pending = captor.getValue();
        assertThat(pending.getSqlSegment()).contains("status").doesNotContain("pay_status");
        assertThat(pending.getSqlSegment()).doesNotContain("payment_date");
        assertThat(pending.getParamNameValuePairs().values()).contains("SUBMITTED");

        // 已批未付（before=null）与逾期未付（before=今天）走同一剩余额聚合
        ArgumentCaptor<LocalDate> beforeCaptor = ArgumentCaptor.forClass(LocalDate.class);
        verify(paymentApplyMapper, times(2)).sumApprovedUnpaidAgg(beforeCaptor.capture());
        assertThat(beforeCaptor.getAllValues()).containsExactly(null, LocalDate.now());

        // TOP5 清单与卡片同口径：排除已付 + 付款日期逾期 + 金额降序 + LIMIT 5
        ArgumentCaptor<QueryWrapper> listCaptor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(paymentApplyMapper).selectList(listCaptor.capture());
        assertThat(listCaptor.getValue().getSqlSegment())
                .contains("pay_status", "payment_date", "payment_amount", "LIMIT 5");
    }

    @Test
    @DisplayName("TOP5 金额按剩余未付额展示，已勾稽超过申请额时归零")
    @SuppressWarnings("unchecked")
    void summary_topPaymentsUseRemainingAmount() {
        when(paymentApplyMapper.selectMaps(any())).thenReturn(List.of());
        when(paymentApplyMapper.sumApprovedUnpaidAgg(any())).thenReturn(new LinkedHashMap<>());
        when(receivableMapper.selectMaps(any())).thenReturn(List.of(), List.of());
        when(invoiceReceivedMapper.selectMaps(any())).thenReturn(List.of());

        LocalDate today = LocalDate.now();
        when(paymentApplyMapper.selectList(any())).thenReturn(List.of(
                payment(1L, 1L, "800", today.minusDays(2)),
                payment(2L, 1L, "500", today.minusDays(1))));
        when(bankFlowMapper.matchedAmountByPaymentApply()).thenReturn(Map.of(
                1L, new BigDecimal("300"),
                2L, new BigDecimal("900")));
        when(receivableMapper.selectList(any())).thenReturn(List.of());
        when(projectMapper.selectBatchIds(any())).thenReturn(List.of(project(1L, "P1", "业主A")));

        List<Map<String, Object>> topPayments = (List<Map<String, Object>>)
                service.summary().get("topOverduePayments");

        assertThat(topPayments).hasSize(2);
        assertThat((BigDecimal) topPayments.get(0).get("amount")).isEqualByComparingTo("500");
        assertThat((BigDecimal) topPayments.get(1).get("amount")).isEqualByComparingTo("0");
    }
}
