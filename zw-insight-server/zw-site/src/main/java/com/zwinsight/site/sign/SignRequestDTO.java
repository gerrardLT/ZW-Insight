package com.zwinsight.site.sign;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 签到请求 DTO
 */
@Data
public class SignRequestDTO {

    /**
     * 项目ID
     */
    private Long projectId;

    /**
     * 纬度
     */
    private BigDecimal latitude;

    /**
     * 经度
     */
    private BigDecimal longitude;

    /**
     * 签到地址
     */
    private String address;
}
