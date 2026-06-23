package com.zwinsight.basedata.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 供应商黑名单实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_supplier_blacklist")
public class BizSupplierBlacklist extends BaseEntity {

    /** 供应商ID */
    private Long supplierId;

    /** 供应商名称 */
    private String supplierName;

    /** 拉黑原因 */
    private String reason;

    /** 拉黑日期 */
    private LocalDate blacklistDate;

    /** 状态（1-黑名单/0-已移出） */
    private Integer status;
}
