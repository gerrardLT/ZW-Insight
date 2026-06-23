package com.zwinsight.machine.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 机械加油记录
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_machine_oil_record")
public class BizMachineOilRecord extends BaseEntity {

    /** 机械ID */
    private Long machineId;

    /** 项目ID */
    private Long projectId;

    /** 加油日期 */
    private LocalDate oilDate;

    /** 加油量 */
    private BigDecimal oilQuantity;

    /** 加油金额 */
    private BigDecimal oilAmount;
}
