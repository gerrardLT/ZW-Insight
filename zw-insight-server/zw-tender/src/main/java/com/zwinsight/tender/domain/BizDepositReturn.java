package com.zwinsight.tender.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 保证金退还实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_deposit_return")
public class BizDepositReturn extends BaseEntity {

    /** 保证金申请ID */
    private Long depositApplyId;

    /** 退还金额 */
    private BigDecimal returnAmount;

    /** 退还日期 */
    private LocalDate returnDate;
}
