package com.zwinsight.site.sign;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 签到范围配置 DTO
 */
@Data
public class SignConfigDTO {

    /**
     * 项目中心纬度
     */
    private BigDecimal latitude;

    /**
     * 项目中心经度
     */
    private BigDecimal longitude;

    /**
     * 允许签到半径（米）
     */
    private Integer radius;
}
