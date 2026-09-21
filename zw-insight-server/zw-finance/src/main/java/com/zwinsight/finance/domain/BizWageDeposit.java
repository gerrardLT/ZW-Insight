package com.zwinsight.finance.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 工资专户拨付记录实体（建设单位人工费拨付流水）
 * <p>条例第29条：建设单位应当及时足额将人工费用拨付至专户。
 * 本表记录每一笔到账，用于"到位率"与"拨付周期≤1个月"合规核算。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_wage_deposit")
public class BizWageDeposit extends BaseEntity {

    /** 工资专户ID */
    private Long accountId;

    /** 项目ID */
    private Long projectId;

    /** 到账日期 */
    private LocalDate depositDate;

    /** 到账金额 */
    private BigDecimal amount;

    /** 拨付方（建设单位）名称 */
    private String payerName;

    /** 银行凭证号 */
    private String voucherNo;

    /** 备注 */
    private String remark;
}
