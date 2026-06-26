package com.zwinsight.finance.domain.dto;

import lombok.Data;

/**
 * 创建封账请求
 */
@Data
public class FinanceLockCreateRequest {

    /** 封账期间，格式 YYYY-MM */
    private String period;

    /** 封账类型：MONTHLY-月度 / QUARTERLY-季度 */
    private String lockType;
}
