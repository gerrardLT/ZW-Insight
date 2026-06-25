package com.zwinsight.finance.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 项目最终结算实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_project_settlement")
public class BizProjectSettlement extends BaseEntity {

    /** 项目ID */
    private Long projectId;

    /** 结算单编号 */
    private String settlementCode;

    // ===== 收入汇总 =====

    /** 施工合同总额 */
    private BigDecimal constructionContractAmount;

    /** 累计产值 */
    private BigDecimal cumulativeOutput;

    /** 累计收款 */
    private BigDecimal cumulativeReceived;

    /** 累计开票 */
    private BigDecimal cumulativeInvoiced;

    /** 总收入 */
    private BigDecimal totalIncome;

    // ===== 支出汇总 =====

    /** 分包结算总额 */
    private BigDecimal subcontractSettled;

    /** 劳务结算总额 */
    private BigDecimal laborSettled;

    /** 材料结算总额 */
    private BigDecimal materialSettled;

    /** 机械结算总额 */
    private BigDecimal machineSettled;

    /** 其他支出 */
    private BigDecimal otherExpense;

    /** 累计付款 */
    private BigDecimal cumulativePaid;

    /** 总支出 */
    private BigDecimal totalExpenditure;

    // ===== 利润 =====

    /** 最终利润 */
    private BigDecimal profit;

    /** 利润率(%) */
    private BigDecimal profitRate;

    // ===== 状态 =====

    /** 状态(DRAFT/SUBMITTED/APPROVED/REJECTED) */
    private String status;

    /** 流程实例ID */
    private String workflowInstanceId;
}
