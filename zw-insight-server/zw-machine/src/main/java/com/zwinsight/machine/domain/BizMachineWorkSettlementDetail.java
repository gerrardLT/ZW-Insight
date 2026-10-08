package com.zwinsight.machine.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.machine.mapper.LongListTypeHandler;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 机械工作量结算明细
 */
@Data
@TableName(value = "biz_machine_work_settlement_detail", autoResultMap = true)
public class BizMachineWorkSettlementDetail implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键ID */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 结算单ID */
    private Long settlementId;

    /** 机械台账ID */
    private Long ledgerId;

    /** 机械合同ID（回写合同累计结算的依据） */
    private Long contractId;

    /** 关联工作日志ID列表（JSON） */
    @TableField(typeHandler = LongListTypeHandler.class)
    private List<Long> workLogIds;

    /** 台班数 */
    private BigDecimal shiftCount;

    /** 工作量 */
    private BigDecimal workVolume;

    /** 单价 */
    private BigDecimal unitPrice;

    /** 计价数量（台班数/月数/工作量，小计 = 单价 × 计价数量） */
    private BigDecimal billingQuantity;

    /** 小计金额 */
    private BigDecimal subtotal;

    /** 计价方式：SHIFT-台班计价, MONTHLY-月租/包月, VOLUME-工作量计价 */
    private String pricingType;

    /** 租户ID */
    private Long tenantId;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
