package com.zwinsight.finance.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 融资借贷台账实体
 * <p>还款方式：
 * EQUAL_INSTALLMENT 等额本息：月供 = P×r×(1+r)^n/((1+r)^n-1)；
 * EQUAL_PRINCIPAL 等额本金：每期本金 = P/n，利息 = 剩余本金×r；
 * BULLET 到期还本付息：到期一次性还本，利息 = P×r×n/12。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_financing")
public class BizFinancing extends BaseEntity {

    /** 融资类型：银行贷款 */
    public static final String TYPE_BANK_LOAN = "BANK_LOAN";
    /** 融资类型：其他借款 */
    public static final String TYPE_OTHER = "OTHER";

    /** 还款方式：等额本息 */
    public static final String METHOD_EQUAL_INSTALLMENT = "EQUAL_INSTALLMENT";
    /** 还款方式：等额本金 */
    public static final String METHOD_EQUAL_PRINCIPAL = "EQUAL_PRINCIPAL";
    /** 还款方式：到期还本付息 */
    public static final String METHOD_BULLET = "BULLET";

    /** 状态：在借 */
    public static final String STATUS_ACTIVE = "ACTIVE";
    /** 状态：已结清 */
    public static final String STATUS_SETTLED = "SETTLED";
    /** 状态：逾期 */
    public static final String STATUS_OVERDUE = "OVERDUE";

    /** 融资类型（BANK_LOAN/OTHER） */
    private String financingType;

    /** 借款合同编号 */
    private String contractNo;

    /** 出借方名称（银行/机构） */
    private String lenderName;

    /** 借款本金 */
    private BigDecimal principal;

    /** 年利率（小数，如0.045） */
    private BigDecimal annualRate;

    /** 放款日期 */
    private LocalDate startDate;

    /** 到期日期 */
    private LocalDate endDate;

    /** 期限（月） */
    private Integer termMonths;

    /** 还款方式（EQUAL_INSTALLMENT/EQUAL_PRINCIPAL/BULLET） */
    private String repaymentMethod;

    /** 计划总利息（生成计划时汇总） */
    private BigDecimal totalInterest;

    /** 累计已还本息 */
    private BigDecimal totalRepaid;

    /** 状态（ACTIVE/SETTLED/OVERDUE） */
    private String status;

    /** 备注 */
    private String remark;
}
