package com.zwinsight.budget.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * CBS 成本流水（金额变动台账）。
 * <p>
 * <b>双重职责</b>：
 * </p>
 * <ol>
 *   <li><b>幂等锁</b> —— unique key on (tenant_id, source_type, source_id, account_id, amount_type)
 *       保证同一业务事实只生效一次，Outbox「至少一次」投递不会重复记账；</li>
 *   <li><b>可追溯</b> —— current/commitment/actual 每一次变动都留痕，支持「这个数字怎么来的」下钻与审计回放。</li>
 * </ol>
 *
 * @implNote 金额一律记增量（delta_amount）+ 变动后余额（balance_after），余额字段让对账无需从头累加即可校验。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_cost_account_txn")
public class BizCostAccountTxn extends BaseEntity {

    /** 项目 ID */
    private Long projectId;

    /** 成本账户 ID */
    private Long accountId;

    /** 金额维度（BASELINE/CURRENT/COMMITMENT/ACTUAL/FORECAST） */
    private String amountType;

    /** 变动增量（正=增加，负=减少） */
    private BigDecimal deltaAmount;

    /** 变动后余额（对账校验用） */
    private BigDecimal balanceAfter;

    /** 来源类型（CHANGE_EVENT/CONTRACT/PURCHASE/SETTLEMENT/PAYMENT/MANUAL） */
    private String sourceType;

    /** 来源业务 ID（如变更事件编号、合同编号、发票号等）——与 source_type 组成幂等键 */
    private String sourceId;

    /** 来源单据编号（展示用，冗余字段便于列表查询） */
    private String sourceNumber;

    /** 业务发生时间（区别于记录写入时间） */
    private LocalDateTime occurredAt;

    /** 备注 */
    private String remark;
}
