package com.zwinsight.site.sign;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 签到记录实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_sign_record")
public class BizSignRecord extends BaseEntity {

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 项目ID
     */
    private Long projectId;

    /**
     * 签到时间
     */
    private LocalDateTime signTime;

    /**
     * 纬度（精度到小数点后7位）
     */
    private BigDecimal latitude;

    /**
     * 经度（精度到小数点后7位）
     */
    private BigDecimal longitude;

    /**
     * 签到地址
     */
    private String address;

    /**
     * 是否在范围内（0-否 1-是）
     */
    private Integer isInRange;
}
