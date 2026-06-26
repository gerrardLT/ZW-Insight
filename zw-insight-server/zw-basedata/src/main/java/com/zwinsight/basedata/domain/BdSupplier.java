package com.zwinsight.basedata.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.desensitize.Desensitize;
import com.zwinsight.common.desensitize.DesensitizeType;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 供应商实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("bd_supplier")
public class BdSupplier extends BaseEntity {

    /**
     * 供应商名称
     */
    private String supplierName;

    /**
     * 供应商编码
     */
    private String supplierCode;

    /**
     * 供应商类型（MATERIAL-材料 LABOR-劳务 MACHINE-机械 SUBCONTRACT-分包）
     */
    private String supplierType;

    /**
     * 联系人
     */
    private String contactName;

    /**
     * 联系电话
     */
    @Desensitize(type = DesensitizeType.PHONE)
    private String contactPhone;

    /**
     * 地址
     */
    @Desensitize(type = DesensitizeType.ADDRESS)
    private String address;

    /**
     * 开户行
     */
    private String bankName;

    /**
     * 银行账号
     */
    private String bankAccount;

    /**
     * 税号
     */
    private String taxNumber;

    /**
     * 状态（1-启用 0-停用）
     */
    private Integer status;
}
