package com.zwinsight.finance.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 农民工工资专用账户实体
 * <p>政策依据《保障农民工工资支付条例》（国务院令第724号）第26/29/31/33条：
 * 专户专项用于支付农民工工资；人工费拨付周期不得超过1个月；
 * 专户资金不得因其他原因被查封、冻结或划拨（compliance_flag 记录合规状态）。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_wage_special_account")
public class BizWageSpecialAccount extends BaseEntity {

    /** 状态：正常 */
    public static final String STATUS_ACTIVE = "ACTIVE";
    /** 状态：冻结（不得因其他原因冻结，仅监管冻结） */
    public static final String STATUS_FROZEN = "FROZEN";
    /** 状态：销户 */
    public static final String STATUS_CANCELLED = "CANCELLED";

    /** 合规状态：合规 */
    public static final String COMPLIANCE_COMPLIANT = "COMPLIANT";
    /** 合规状态：拨付不足（月度拨付低于人工费预算月均值） */
    public static final String COMPLIANCE_INSUFFICIENT = "INSUFFICIENT";
    /** 合规状态：拨付逾期（超过1个月无人工费到账，条例第24条） */
    public static final String COMPLIANCE_OVERDUE = "OVERDUE";

    /** 项目ID */
    private Long projectId;

    /** 专户账号 */
    private String accountNo;

    /** 专户户名 */
    private String accountName;

    /** 开户银行 */
    private String bankName;

    /** 开户支行 */
    private String bankBranch;

    /** 状态（ACTIVE/FROZEN/CANCELLED） */
    private String status;

    /** 人工费总预算（合同口径） */
    private BigDecimal wageBudget;

    /** 专户累计到账 */
    private BigDecimal totalReceived;

    /** 专户累计代发工资 */
    private BigDecimal totalPaid;

    /** 当前余额 */
    private BigDecimal currentBalance;

    /** 在册农民工人数 */
    private Integer workerCount;

    /** 最近一次人工费到账日期 */
    private LocalDate lastDepositDate;

    /** 合规状态（COMPLIANT/INSUFFICIENT/OVERDUE） */
    private String complianceFlag;

    /** 备注 */
    private String remark;
}
