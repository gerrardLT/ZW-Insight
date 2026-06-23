package com.zwinsight.tender.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 投标费用实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_tender_fee")
public class BizTenderFee extends BaseEntity {

    /** 投标登记ID */
    private Long registerId;

    /** 项目ID */
    private Long projectId;

    /** 费用类型 */
    private String feeType;

    /** 费用金额 */
    private BigDecimal feeAmount;

    /** 付款日期 */
    private LocalDate paymentDate;

    /** 回单文件 */
    private String receiptFile;

    /** 状态（DRAFT/PAID） */
    private String status;
}
