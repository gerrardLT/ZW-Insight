package com.zwinsight.finance.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 税率字典实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_tax_rate")
public class BizTaxRate extends BaseEntity {

    /** 税率名称 */
    private String name;

    /** 税率数值（如 13.00 表示 13%） */
    private BigDecimal rateValue;

    /** 状态：ENABLED-启用 / DISABLED-停用 */
    private String status;

    /** 租户编码 */
    private String tenantCode;
}
