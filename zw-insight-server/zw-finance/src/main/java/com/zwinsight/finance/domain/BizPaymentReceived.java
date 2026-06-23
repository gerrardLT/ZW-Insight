package com.zwinsight.finance.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 收款登记实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_payment_received")
public class BizPaymentReceived extends BaseEntity {

    /** 项目ID */
    private Long projectId;

    /** 合同ID */
    private Long contractId;

    /** 收款日期 */
    private LocalDate receiveDate;

    /** 收款金额 */
    private BigDecimal receiveAmount;

    /** 收款人 */
    private String receiver;

    /** 收款方式 */
    private String receiveType;

    /** 收款银行账号 */
    private String receiveBankAccount;

    /** 状态（DRAFT/APPROVED） */
    private String status;
}
