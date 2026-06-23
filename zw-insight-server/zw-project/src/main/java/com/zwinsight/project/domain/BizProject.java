package com.zwinsight.project.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 项目实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_project")
public class BizProject extends BaseEntity {

    /**
     * 项目编号
     */
    private String projectCode;

    /**
     * 项目名称
     */
    private String projectName;

    /**
     * 项目性质
     */
    private String projectNature;

    /**
     * 项目类型
     */
    private String projectType;

    /**
     * 业主单位ID
     */
    private Long ownerCompanyId;

    /**
     * 业主单位名称
     */
    private String ownerCompanyName;

    /**
     * 签约公司ID
     */
    private Long signingCompanyId;

    /**
     * 签约公司名称
     */
    private String signingCompanyName;

    /**
     * 项目概述
     */
    private String projectOverview;

    /**
     * 项目地址
     */
    private String projectAddress;

    /**
     * 联系人
     */
    private String contactName;

    /**
     * 联系电话
     */
    private String contactPhone;

    /**
     * 是否需要招标（1-是 0-否）
     */
    private Integer needTender;

    /**
     * 项目状态（DRAFT/FILED/TENDERING/WON/CONSTRUCTION/COMPLETED/CLOSED）
     */
    private String status;

    /**
     * 预算金额
     */
    private BigDecimal budgetAmount;

    /**
     * 合同金额
     */
    private BigDecimal contractAmount;

    /**
     * 累计产值
     */
    private BigDecimal cumulativeOutput;

    /**
     * 结算金额
     */
    private BigDecimal settlementAmount;

    /**
     * 总收入
     */
    private BigDecimal totalIncome;

    /**
     * 总支出
     */
    private BigDecimal totalExpense;

    /**
     * 其他总支付
     */
    private BigDecimal totalOtherPayment;
}
