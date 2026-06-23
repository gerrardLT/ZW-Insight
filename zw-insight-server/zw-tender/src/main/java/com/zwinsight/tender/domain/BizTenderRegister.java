package com.zwinsight.tender.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 投标登记实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_tender_register")
public class BizTenderRegister extends BaseEntity {

    /** 项目ID */
    private Long projectId;

    /** 业主单位 */
    private String ownerCompany;

    /** 招标方式 */
    private String bidMethod;

    /** 报名方式 */
    private String registerMethod;

    /** 报名日期 */
    private LocalDate registerDate;

    /** 开标日期 */
    private LocalDate openDate;

    /** 投标方式 */
    private String tenderMethod;

    /** 保证金金额 */
    private BigDecimal depositAmount;

    /** 状态（REGISTERED/WON/LOST） */
    private String status;
}
