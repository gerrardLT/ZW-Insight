package com.zwinsight.machine.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 机械工作量结算单
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_machine_work_settlement")
public class BizMachineWorkSettlement extends BaseEntity {

    /** 项目ID */
    private Long projectId;

    /** 结算单编号 */
    private String settlementCode;

    /** 结算周期开始日期 */
    private LocalDate periodStart;

    /** 结算周期结束日期 */
    private LocalDate periodEnd;

    /** 结算总金额 */
    private BigDecimal totalAmount;

    /**
     * 状态：0-草稿, 1-审批中, 2-已审批, 3-已驳回
     */
    private Integer status;

    /** 流程实例ID */
    private String workflowInstanceId;
}
