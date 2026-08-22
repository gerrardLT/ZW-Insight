package com.zwinsight.finance.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 回款率分析 VO
 */
@Data
public class CollectionRateVO {

    /** 项目ID */
    private Long projectId;

    /** 已开票金额合计（已审批开票申请） */
    private BigDecimal totalInvoiced;

    /** 已回款金额合计（已审批收款登记） */
    private BigDecimal totalReceived;

    /** 回款率（已回款 / 已开票，已开票为 0 时为 null） */
    private BigDecimal collectionRate;

    /** 未回款金额（已开票 - 已回款） */
    private BigDecimal uncollectedAmount;
}
