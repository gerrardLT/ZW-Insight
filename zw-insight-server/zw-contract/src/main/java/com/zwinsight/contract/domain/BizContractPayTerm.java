package com.zwinsight.contract.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 合同收付款条款（V2026_109）。
 * <p>
 * 结构化表达合同价款支付约定（财建〔2004〕369 号第七条要求合同约定预付款、
 * 进度款、竣工结算款、质保金的支付条件/方式/期限）：每合同可有多条款，
 * 供过程结算扣款建议、质保金自动计提及资金预测引用，不复制金额。
 * 通用挂接：{@code contract_category} + {@code contract_id}（施工合同与五类支出合同）。
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_contract_pay_term")
public class BizContractPayTerm extends BaseEntity {

    /** 预付款 */
    public static final String TYPE_PREPAY = "PREPAY";
    /** 进度款 */
    public static final String TYPE_PROGRESS = "PROGRESS";
    /** 竣工结算款 */
    public static final String TYPE_FINAL = "FINAL";
    /** 质保金 */
    public static final String TYPE_RETENTION = "RETENTION";

    /** 施工合同类别（挂接 biz_construction_contract） */
    public static final String CATEGORY_CONSTRUCTION = "CONSTRUCTION";

    /** 项目ID（级联清理登记用） */
    private Long projectId;

    /** 合同类别（CONSTRUCTION/PURCHASE/LABOR/MACHINE/SUBCONTRACT/OTHER） */
    private String contractCategory;

    /** 合同ID */
    private Long contractId;

    /** 条款类型（PREPAY/PROGRESS/FINAL/RETENTION） */
    private String termType;

    /** 触发条件（如：进场且报验合格后支付） */
    private String triggerCondition;

    /** 计划支付/收款日期 */
    private LocalDate planDate;

    /** 占合同总额比例（%，0-100） */
    private BigDecimal ratio;

    /** 固定金额（与比例二选一，同时填以金额为准） */
    private BigDecimal amount;

    /** 预付款扣回规则（PER_STAGE 按期均扣 / AFTER_80 付至80%后一次扣回 / NONE） */
    private String deductRule;

    /** 质保期月数（RETENTION 条款用；缺省由人工在质保金台账补录，不猜默认） */
    private Integer retentionMonths;

    /** 宽限期天数 */
    private Integer graceDays;

    /** 状态（ACTIVE/DISABLED） */
    private String status;
}
