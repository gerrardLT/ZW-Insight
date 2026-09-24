package com.zwinsight.finance.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

/**
 * 应收台账实体（V2026_57）
 * <p>
 * 结算审批通过生成应收记录（source_type=SETTLEMENT），回款登记生效时 FIFO 核销。
 * 口径不变量：biz_project.receivable_amount = 该项目 OPEN 记录余额
 * （receivable_amount - written_off_amount）合计，由 ReceivableService 同事务双写维护。
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_receivable")
public class BizReceivable extends BaseEntity {

    /** 来源类型：项目结算 */
    public static final String SOURCE_SETTLEMENT = "SETTLEMENT";
    /** 来源类型：质保金到期转应收 */
    public static final String SOURCE_RETENTION = "RETENTION";

    /** 状态：未结清 */
    public static final String STATUS_OPEN = "OPEN";
    /** 状态：已结清 */
    public static final String STATUS_CLOSED = "CLOSED";

    // ==================== 甲方审核状态值域（§10 第 6 级，V2026_69）====================
    /** 甲方审核：已提交 */
    public static final String REVIEW_SUBMITTED = "SUBMITTED";
    /** 甲方审核：审核中 */
    public static final String REVIEW_UNDER_REVIEW = "UNDER_REVIEW";
    /** 甲方审核：已确认 */
    public static final String REVIEW_CONFIRMED = "CONFIRMED";
    /** 甲方审核：有异议（需对账/谈判） */
    public static final String REVIEW_DISPUTED = "DISPUTED";

    /** 甲方审核状态合法值；非法值拒绝写入（不静默当作未登记） */
    public static final Set<String> REVIEW_STATUSES = Set.of(
            REVIEW_SUBMITTED, REVIEW_UNDER_REVIEW, REVIEW_CONFIRMED, REVIEW_DISPUTED);

    /** 项目ID */
    private Long projectId;

    /** 项目名称（冗余展示字段，不持久化） */
    @TableField(exist = false)
    private String projectName;

    /** 关联施工合同ID（可空） */
    private Long contractId;

    /** 来源类型（SETTLEMENT-项目结算/RETENTION-质保金到期） */
    private String sourceType;

    /** 来源单据ID（结算单ID等） */
    private Long sourceId;

    /** 应收金额 */
    private BigDecimal receivableAmount;

    /** 约定收款到期日 */
    private LocalDate dueDate;

    /** 已核销金额（回款登记生效时 FIFO 冲减） */
    private BigDecimal writtenOffAmount;

    /** 状态（OPEN-未结清/CLOSED-已结清） */
    private String status;

    /** 备注 */
    private String remark;

    // ==================== §10 下钻 8 级链补充字段（V2026_69）====================
    // 以下六列均<b>可空且无默认值</b>：系统内无来源单据（结算单无工程节点字段、
    // 产值报告与结算单无外键关联），只能人工登记。NULL 即「未登记」，
    // <b>不得用「待审核」「未知」等默认值填充</b>（那会把「没登记」伪装成「已登记为某状态」）。

    /** 对应工程节点（§10 第 3 级；人工维护） */
    private String milestoneNode;

    /** 实际申请日期（§10 第 5 级；向甲方提交结算/付款申请的日期） */
    private LocalDate applyDate;

    /** 甲方审核状态（§10 第 6 级；见 REVIEW_* 常量，NULL=未登记） */
    private String ownerReviewStatus;

    /** 甲方审核日期（与 applyDate 配对可算甲方停留天数） */
    private LocalDate ownerReviewDate;

    /** 催收负责人 ID（§10 第 7 级；人工指定） */
    private Long ownerId;

    /** 催收负责人姓名（冗余存储便于列表展示，与 BizRiskRegister 同做法） */
    private String ownerName;

    /** 下一步动作（§10 第 8 级；人工维护） */
    private String nextAction;
}
