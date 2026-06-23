package com.zwinsight.hr.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 车辆维保实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_vehicle_maintenance")
public class BizVehicleMaintenance extends BaseEntity {

    /** 车辆ID */
    private Long vehicleId;

    /** 车牌号 */
    private String plateNumber;

    /** 维保类型（REPAIR/INSURANCE） */
    private String maintType;

    /** 维保日期 */
    private LocalDate maintDate;

    /** 维保费用 */
    private BigDecimal maintCost;

    /** 维保内容 */
    private String content;
}
