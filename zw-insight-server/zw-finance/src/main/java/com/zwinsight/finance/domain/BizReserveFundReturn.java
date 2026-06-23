package com.zwinsight.finance.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 备用金归还实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_reserve_fund_return")
public class BizReserveFundReturn extends BaseEntity {

    /** 备用金申请ID */
    private Long reserveApplyId;

    /** 归还金额 */
    private BigDecimal returnAmount;

    /** 归还日期 */
    private LocalDate returnDate;
}
