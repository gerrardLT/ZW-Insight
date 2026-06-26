package com.zwinsight.machine.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 机械工作日志
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_machine_work_log")
public class BizMachineWorkLog extends BaseEntity {

    /** 机械ID */
    private Long machineId;

    /** 项目ID */
    private Long projectId;

    /** 工作日期 */
    private LocalDate workDate;

    /** 台班数 */
    private BigDecimal shiftCount;

    /** 工作量 */
    private BigDecimal workQuantity;

    /** 油耗 */
    private BigDecimal oilConsumption;

    /** 状态（DRAFT-草稿/SETTLED-已结算） */
    private String status;

    /** 结算状态（UNSETTLED-未结算/SETTLED-已结算） */
    private String settlementStatus;
}
