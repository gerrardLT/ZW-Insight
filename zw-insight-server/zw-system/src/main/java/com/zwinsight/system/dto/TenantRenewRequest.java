package com.zwinsight.system.dto;

import lombok.Data;

/**
 * 租户续期请求
 */
@Data
public class TenantRenewRequest {

    /** 租户ID */
    private Long tenantId;

    /** 续期天数 */
    private Integer durationDays;
}
