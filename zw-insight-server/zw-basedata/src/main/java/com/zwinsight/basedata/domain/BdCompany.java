package com.zwinsight.basedata.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 自持公司实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("bd_company")
public class BdCompany extends BaseEntity {

    /**
     * 公司名称
     */
    private String companyName;

    /**
     * 公司编码
     */
    private String companyCode;

    /**
     * 法人
     */
    private String legalPerson;

    /**
     * 注册资本
     */
    private String registeredCapital;

    /**
     * 地址
     */
    private String address;

    /**
     * 联系人
     */
    private String contactName;

    /**
     * 联系电话
     */
    private String contactPhone;

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
