package com.zwinsight.finance.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 其他支付实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_other_payment")
public class BizOtherPayment extends BaseEntity {

    /** 项目ID */
    private Long projectId;

    /** 付款人 */
    private String payerName;

    /** 付款日期 */
    private LocalDate paymentDate;

    /** 付款金额 */
    private BigDecimal paymentAmount;

    /** 备注 */
    private String remark;

    /** 状态（DRAFT/APPROVED） */
    private String status;
}
