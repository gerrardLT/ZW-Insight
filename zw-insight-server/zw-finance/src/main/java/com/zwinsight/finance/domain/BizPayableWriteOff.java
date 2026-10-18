package com.zwinsight.finance.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 应付核销明细实体（V2026_115，付款↔应付多对多勾稽）。
 * <p>付款申请生效时按 FIFO 逐笔落明细；反冲时按明细精确回退并逻辑删除保留审计痕迹。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_payable_write_off")
public class BizPayableWriteOff extends BaseEntity {

    /** 应付台账ID（biz_payable.id） */
    private Long payableId;

    /** 付款申请ID（biz_payment_apply.id） */
    private Long paymentApplyId;

    /** 本次核销金额 */
    private BigDecimal amount;
}
