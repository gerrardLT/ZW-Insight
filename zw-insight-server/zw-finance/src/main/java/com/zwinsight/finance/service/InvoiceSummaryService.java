package com.zwinsight.finance.service;

import com.zwinsight.finance.domain.dto.InvoiceSummaryDTO;
import com.zwinsight.finance.mapper.InvoiceSummaryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 发票汇总服务
 * <p>
 * 将已开票、已收票两张表的按项目聚合结果合并为统一视图，
 * 缺省字段填 0，保证前端可直接展示开票额、收票额与税额差异。
 * </p>
 */
@Service
@RequiredArgsConstructor
public class InvoiceSummaryService {

    private final InvoiceSummaryMapper invoiceSummaryMapper;

    /**
     * 按项目维度汇总发票明细
     *
     * @param projectId 项目ID（可选）
     * @param startDate 起始日期 yyyy-MM-dd（可选）
     * @param endDate   截止日期 yyyy-MM-dd（可选）
     * @return 按项目合并后的汇总列表
     */
    public List<InvoiceSummaryDTO> summary(Long projectId, String startDate, String endDate) {
        List<InvoiceSummaryDTO> invoicedList = invoiceSummaryMapper.summarizeInvoiced(projectId, startDate, endDate);
        List<InvoiceSummaryDTO> receivedList = invoiceSummaryMapper.summarizeReceived(projectId, startDate, endDate);

        Map<Long, InvoiceSummaryDTO> merged = new LinkedHashMap<>();

        for (InvoiceSummaryDTO invoiced : invoicedList) {
            InvoiceSummaryDTO dto = merged.computeIfAbsent(invoiced.getProjectId(),
                    k -> newRow(invoiced.getProjectId(), invoiced.getProjectName()));
            dto.setInvoicedCount(nvl(invoiced.getInvoicedCount()));
            dto.setInvoicedAmount(nvl(invoiced.getInvoicedAmount()));
            dto.setInvoicedTaxAmount(nvl(invoiced.getInvoicedTaxAmount()));
        }

        for (InvoiceSummaryDTO received : receivedList) {
            InvoiceSummaryDTO dto = merged.computeIfAbsent(received.getProjectId(),
                    k -> newRow(received.getProjectId(), received.getProjectName()));
            // 项目名补齐（开票侧缺失时用收票侧）
            if (dto.getProjectName() == null) {
                dto.setProjectName(received.getProjectName());
            }
            dto.setReceivedCount(nvl(received.getReceivedCount()));
            dto.setReceivedAmount(nvl(received.getReceivedAmount()));
            dto.setReceivedTaxAmount(nvl(received.getReceivedTaxAmount()));
        }

        return new ArrayList<>(merged.values());
    }

    private InvoiceSummaryDTO newRow(Long projectId, String projectName) {
        InvoiceSummaryDTO dto = new InvoiceSummaryDTO();
        dto.setProjectId(projectId);
        dto.setProjectName(projectName);
        dto.setInvoicedCount(0);
        dto.setInvoicedAmount(BigDecimal.ZERO);
        dto.setInvoicedTaxAmount(BigDecimal.ZERO);
        dto.setReceivedCount(0);
        dto.setReceivedAmount(BigDecimal.ZERO);
        dto.setReceivedTaxAmount(BigDecimal.ZERO);
        return dto;
    }

    private Integer nvl(Integer value) {
        return value == null ? 0 : value;
    }

    private BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
