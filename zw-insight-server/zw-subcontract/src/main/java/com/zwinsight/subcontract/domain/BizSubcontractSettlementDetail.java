package com.zwinsight.subcontract.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 分包结算明细
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_subcontract_settlement_detail")
public class BizSubcontractSettlementDetail extends BaseEntity {

    /** 结算单ID */
    private Long settlementId;

    /** 工程项名称 */
    private String itemName;

    /** 计量单位 */
    private String unit;

    /** 本次结算数量 */
    private BigDecimal quantity;

    /** 单价 */
    private BigDecimal unitPrice;

    /** 本次结算金额 (quantity × unitPrice) */
    private BigDecimal amount;

    /** 备注 */
    private String remark;

    /** 排序号 */
    private Integer sortOrder;
}
