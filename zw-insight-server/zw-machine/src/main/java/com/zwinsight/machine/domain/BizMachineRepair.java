package com.zwinsight.machine.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 机械维修记录
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_machine_repair")
public class BizMachineRepair extends BaseEntity {

    /** 机械ID */
    private Long machineId;

    /** 项目ID */
    private Long projectId;

    /** 故障描述 */
    private String faultDescription;

    /** 报修日期 */
    private LocalDate reportDate;

    /** 维修人 */
    private String repairPerson;

    /** 维修日期 */
    private LocalDate repairDate;

    /** 维修费用 */
    private BigDecimal repairCost;

    /** 维修状态（REPORTED-已报修/DISPATCHED-已派工/REPAIRING-维修中/COMPLETED-已完成） */
    private String repairStatus;
}
