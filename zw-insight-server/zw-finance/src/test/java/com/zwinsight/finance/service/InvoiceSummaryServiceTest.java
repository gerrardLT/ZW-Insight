package com.zwinsight.finance.service;

import com.zwinsight.finance.domain.dto.InvoiceSummaryDTO;
import com.zwinsight.finance.mapper.InvoiceSummaryMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * InvoiceSummaryService 单元测试
 * <p>发票汇总：开票/收票两侧按项目合并、缺省填 0、项目名互补。</p>
 */
@ExtendWith(MockitoExtension.class)
class InvoiceSummaryServiceTest {

    @Mock
    private InvoiceSummaryMapper invoiceSummaryMapper;

    @InjectMocks
    private InvoiceSummaryService service;

    private InvoiceSummaryDTO invoicedRow(Long projectId, String name, Integer count, String amount, String tax) {
        InvoiceSummaryDTO dto = new InvoiceSummaryDTO();
        dto.setProjectId(projectId);
        dto.setProjectName(name);
        dto.setInvoicedCount(count);
        dto.setInvoicedAmount(amount == null ? null : new BigDecimal(amount));
        dto.setInvoicedTaxAmount(tax == null ? null : new BigDecimal(tax));
        return dto;
    }

    private InvoiceSummaryDTO receivedRow(Long projectId, String name, Integer count, String amount, String tax) {
        InvoiceSummaryDTO dto = new InvoiceSummaryDTO();
        dto.setProjectId(projectId);
        dto.setProjectName(name);
        dto.setReceivedCount(count);
        dto.setReceivedAmount(amount == null ? null : new BigDecimal(amount));
        dto.setReceivedTaxAmount(tax == null ? null : new BigDecimal(tax));
        return dto;
    }

    @Test
    @DisplayName("summary - 同项目两侧合并，null 字段填 0")
    void summary_mergesBothSides() {
        when(invoiceSummaryMapper.summarizeInvoiced(1L, null, null))
                .thenReturn(Collections.singletonList(invoicedRow(1L, "项目A", 3, "1000", null)));
        when(invoiceSummaryMapper.summarizeReceived(1L, null, null))
                .thenReturn(Collections.singletonList(receivedRow(1L, "项目A", null, "800", "72")));

        List<InvoiceSummaryDTO> result = service.summary(1L, null, null);

        assertThat(result).hasSize(1);
        InvoiceSummaryDTO merged = result.get(0);
        assertThat(merged.getProjectName()).isEqualTo("项目A");
        assertThat(merged.getInvoicedCount()).isEqualTo(3);
        assertThat(merged.getInvoicedAmount()).isEqualByComparingTo("1000");
        assertThat(merged.getInvoicedTaxAmount()).isEqualByComparingTo("0"); // null → 0
        assertThat(merged.getReceivedCount()).isZero(); // null → 0
        assertThat(merged.getReceivedAmount()).isEqualByComparingTo("800");
        assertThat(merged.getReceivedTaxAmount()).isEqualByComparingTo("72");
    }

    @Test
    @DisplayName("summary - 仅一侧有数据时另一侧缺省 0，项目名用收票侧补齐")
    void summary_oneSideOnly_defaultsZero() {
        // 开票侧项目 2 无名称，收票侧补名称
        when(invoiceSummaryMapper.summarizeInvoiced(null, null, null))
                .thenReturn(Collections.singletonList(invoicedRow(2L, null, 1, "500", "45")));
        when(invoiceSummaryMapper.summarizeReceived(null, null, null))
                .thenReturn(Collections.singletonList(receivedRow(2L, "项目B", 1, "500", "45")));

        List<InvoiceSummaryDTO> result = service.summary(null, null, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getProjectName()).isEqualTo("项目B"); // 收票侧补齐
    }

    @Test
    @DisplayName("summary - 两侧均空返回空列表")
    void summary_empty_returnsEmpty() {
        when(invoiceSummaryMapper.summarizeInvoiced(null, null, null)).thenReturn(Collections.emptyList());
        when(invoiceSummaryMapper.summarizeReceived(null, null, null)).thenReturn(Collections.emptyList());

        assertThat(service.summary(null, null, null)).isEmpty();
    }

    @Test
    @DisplayName("summary - 不同项目分别成行且保持顺序")
    void summary_distinctProjects_keepOrder() {
        when(invoiceSummaryMapper.summarizeInvoiced(null, null, null))
                .thenReturn(List.of(invoicedRow(1L, "项目A", 1, "100", "9"),
                        invoicedRow(2L, "项目B", 2, "200", "18")));
        when(invoiceSummaryMapper.summarizeReceived(null, null, null))
                .thenReturn(Collections.singletonList(receivedRow(3L, "项目C", 1, "300", "27")));

        List<InvoiceSummaryDTO> result = service.summary(null, null, null);

        assertThat(result).hasSize(3);
        assertThat(result).extracting(InvoiceSummaryDTO::getProjectId).containsExactly(1L, 2L, 3L);
        // 项目C 开票侧缺省 0
        assertThat(result.get(2).getInvoicedCount()).isZero();
        assertThat(result.get(2).getInvoicedAmount()).isEqualByComparingTo("0");
    }
}
