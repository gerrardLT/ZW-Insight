package com.zwinsight.machine.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 机械使用记录
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_machine_usage_record")
public class BizMachineUsageRecord extends BaseEntity {

    /** 项目ID */
    private Long projectId;

    /** 合同ID */
    private Long contractId;

    /** 使用量 */
    private BigDecimal usageQuantity;

    /** 油耗金额 */
    private BigDecimal oilAmount;

    /** 记录日期 */
    private LocalDate recordDate;
}
