package com.zwinsight.finance.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 保证金台账实体（四类全生命周期）
 * <p>政策依据（docs/资金流转深度调研报告.md 4.2节）：
 * 投标≤估算价2%（招标投标法实施条例第26/57条）、履约≤合同额10%（第58条）、
 * 质量≤结算总额3%（建质〔2017〕138号）、工资1%-3%（人社部发〔2021〕65号）。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_security_bond")
public class BizSecurityBond extends BaseEntity {

    /** 保证金类型：投标 */
    public static final String TYPE_TENDER = "TENDER";
    /** 保证金类型：履约 */
    public static final String TYPE_PERFORMANCE = "PERFORMANCE";
    /** 保证金类型：质量 */
    public static final String TYPE_QUALITY = "QUALITY";
    /** 保证金类型：农民工工资 */
    public static final String TYPE_WAGE = "WAGE";

    /** 状态：已缴存 */
    public static final String STATUS_DEPOSITED = "DEPOSITED";
    /** 状态：退还申请中 */
    public static final String STATUS_REFUND_APPLY = "REFUND_APPLY";
    /** 状态：已退还 */
    public static final String STATUS_REFUNDED = "REFUNDED";
    /** 状态：已动用（工资类动用后10个工作日内须补足） */
    public static final String STATUS_USED = "USED";

    /** 项目ID */
    private Long projectId;

    /** 关联合同ID（履约/质量类） */
    private Long contractId;

    /** 关联投标报名ID（投标类） */
    private Long tenderId;

    /** 保证金类型（TENDER/PERFORMANCE/QUALITY/WAGE） */
    private String bondType;

    /** 保证金金额 */
    private BigDecimal amount;

    /** 关联合同金额（比例校验基数） */
    private BigDecimal contractAmount;

    /** 缴存日期 */
    private LocalDate depositDate;

    /** 到期日期 */
    private LocalDate dueDate;

    /** 状态（DEPOSITED/REFUND_APPLY/REFUNDED/USED） */
    private String refundStatus;

    /** 退还申请日期 */
    private LocalDate refundApplyDate;

    /** 实际退还日期 */
    private LocalDate refundActualDate;

    /** 实际退还金额 */
    private BigDecimal refundAmount;

    /** 形式（CASH-现金/BANK_GUARANTEE-银行保函/INSURANCE-保证保险） */
    private String bondForm;

    /** 保函/保险文件路径 */
    private String guaranteeFile;

    /** 免存/降比理由（如连续3年无拖欠） */
    private String exemptReason;

    /** 备注 */
    private String remark;
}
