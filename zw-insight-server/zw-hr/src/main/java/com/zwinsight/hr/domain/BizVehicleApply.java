package com.zwinsight.hr.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 车辆申请实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_vehicle_apply")
public class BizVehicleApply extends BaseEntity {

    /** 车辆ID */
    private Long vehicleId;

    /** 车牌号 */
    private String plateNumber;

    /** 使用时间 */
    private LocalDateTime useTime;

    /** 用途 */
    private String purpose;

    /** 预计归还时间 */
    private LocalDateTime expectedReturnTime;

    /** 状态（DRAFT/APPROVED） */
    private String status;
}
