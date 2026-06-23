package com.zwinsight.hr.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 车辆实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_vehicle")
public class BizVehicle extends BaseEntity {

    /** 车牌号 */
    private String plateNumber;

    /** 车辆类型 */
    private String vehicleType;

    /** 车辆状态（IDLE/IN_USE） */
    private String vehicleStatus;

    /** 图片URL */
    private String imageUrl;

    /** 状态（1-启用 0-停用） */
    private Integer status;
}
