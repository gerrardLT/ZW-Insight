package com.zwinsight.finance.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 报销明细实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_reimbursement_detail")
public class BizReimbursementDetail extends BaseEntity {

    /** 报销单ID */
    private Long reimbursementId;

    /** 费用类型 */
    private String expenseType;

    /** 金额 */
    private BigDecimal amount;

    /** 备注 */
    private String remark;
}
