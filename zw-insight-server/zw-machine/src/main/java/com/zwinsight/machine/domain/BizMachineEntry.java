package com.zwinsight.machine.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 机械进退场记录
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_machine_entry")
public class BizMachineEntry extends BaseEntity {

    /** 机械ID */
    private Long machineId;

    /** 项目ID */
    private Long projectId;

    /** 进退场日期 */
    private LocalDate entryDate;

    /** 进退场类型（IN-进场/OUT-退场） */
    private String entryType;
}
