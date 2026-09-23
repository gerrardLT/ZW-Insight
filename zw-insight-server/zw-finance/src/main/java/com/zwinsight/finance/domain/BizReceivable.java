package com.zwinsight.finance.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

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
}
