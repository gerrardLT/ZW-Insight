package com.zwinsight.finance.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 应收核销明细实体（V2026_57）
 * <p>
 * 回款登记与应收台账的多对多勾稽记录：每次 FIFO 核销逐笔落明细，
 * 回款改额/删除时按明细精确反冲（可审计，不做近似回滚）。
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_receivable_write_off")
public class BizReceivableWriteOff extends BaseEntity {

    /** 应收台账ID（biz_receivable.id） */
    private Long receivableId;

    /** 回款登记ID（biz_payment_received.id） */
    private Long paymentReceivedId;

    /** 本次核销金额 */
    private BigDecimal amount;
}
