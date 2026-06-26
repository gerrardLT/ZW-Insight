package com.zwinsight.basedata.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.desensitize.Desensitize;
import com.zwinsight.common.desensitize.DesensitizeType;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 甲方单位实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("bd_owner")
public class BdOwner extends BaseEntity {

    /**
     * 甲方名称
     */
    private String ownerName;

    /**
     * 甲方编码
     */
    private String ownerCode;

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
     * 发票抬头
     */
    private String invoiceTitle;

    /**
     * 纳税人识别号
     */
    private String taxpayerNo;

    /**
     * 开户行
     */
    private String bankName;

    /**
     * 银行账号
     */
    private String bankAccount;

    /**
     * 状态（1-启用 0-停用）
     */
    private Integer status;
}
