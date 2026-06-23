package com.zwinsight.machine.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 机械台账
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_machine_ledger")
public class BizMachineLedger extends BaseEntity {

    /** 机械名称 */
    private String machineName;

    /** 机械编号 */
    private String machineCode;

    /** 机械类型 */
    private String machineType;

    /** 规格型号 */
    private String model;

    /** 购置日期 */
    private LocalDate purchaseDate;

    /** 状态（REGISTERED-已登记/IN_FIELD-在场/OUT_FIELD-退场） */
    private String status;
}
