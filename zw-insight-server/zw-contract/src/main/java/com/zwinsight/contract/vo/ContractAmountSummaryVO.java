package com.zwinsight.contract.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 合同金额汇总 VO
 */
@Data
public class ContractAmountSummaryVO {

    /** 项目ID */
    private Long projectId;

    /** 合同数量（仅统计登记合同） */
    private Integer contractCount;

    /** 合同金额合计 */
    private BigDecimal totalContractAmount;

    /** 不含税金额合计 */
    private BigDecimal totalAmountWithoutTax;

    /** 税额合计 */
    private BigDecimal totalTaxAmount;

    /** 累计变更金额合计 */
    private BigDecimal totalChangeAmount;

    /** 累计产值合计 */
    private BigDecimal totalOutput;

    /** 累计开票金额合计 */
    private BigDecimal totalInvoiceAmount;

    /** 累计收款金额合计 */
    private BigDecimal totalReceivedAmount;

    /** 回款比例（累计收款 / 合同金额，合同金额为 0 时为 null） */
    private BigDecimal receivedRate;

    /** 按状态分布明细 */
    private List<StatusItem> statusBreakdown;

    /**
     * 状态分布条目
     */
    @Data
    public static class StatusItem {

        /** 合同状态 */
        private String status;

        /** 合同数量 */
        private Integer count;

        /** 合同金额合计 */
        private BigDecimal amount;
    }
}
