package com.zwinsight.machine.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 机械结算单视图对象
 */
@Data
public class MachineSettlementVO {

    private Long id;

    /** 项目ID */
    private Long projectId;

    /** 结算单编号 */
    private String settlementCode;

    /** 结算周期开始 */
    private LocalDate periodStart;

    /** 结算周期结束 */
    private LocalDate periodEnd;

    /** 结算总金额 */
    private BigDecimal totalAmount;

    /** 状态：0-草稿, 1-审批中, 2-已审批, 3-已驳回 */
    private Integer status;

    /** 流程实例ID */
    private String workflowInstanceId;

    /** 创建人 */
    private Long createdBy;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 结算明细列表 */
    private List<MachineSettlementDetailVO> details;

    /**
     * 结算明细 VO
     */
    @Data
    public static class MachineSettlementDetailVO {

        private Long id;

        /** 机械台账ID */
        private Long ledgerId;

        /** 机械名称 */
        private String machineName;

        /** 机械编号 */
        private String machineCode;

        /** 关联工作日志ID列表 */
        private List<Long> workLogIds;

        /** 台班数 */
        private BigDecimal shiftCount;

        /** 工作量 */
        private BigDecimal workVolume;

        /** 单价 */
        private BigDecimal unitPrice;

        /** 小计金额 */
        private BigDecimal subtotal;

        /** 计价方式 */
        private String pricingType;
    }
}
