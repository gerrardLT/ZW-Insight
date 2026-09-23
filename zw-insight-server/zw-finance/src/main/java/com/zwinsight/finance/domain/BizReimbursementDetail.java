package com.zwinsight.finance.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 报销明细实体
 * <p>
 * V2026_60 激活：本表自 00_schema.sql 建表后长期为孤儿表（无任何 Service 写入/读取），
 * 导致报销单只有总额、无费用类别维度。现挂接 biz_fund_category 科目树，
 * 明细合计必须等于主表 totalAmount（ReimbursementDetailService 强校验，不静默）。
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_reimbursement_detail")
public class BizReimbursementDetail extends BaseEntity {

    /** 报销单来源：项目报销 */
    public static final String SOURCE_PROJECT = "PROJECT";
    /** 报销单来源：个人报销 */
    public static final String SOURCE_PERSONAL = "PERSONAL";
    /** 招待费科目编码（命中时必须填写 {@link BizEntertainmentDetail} 专项明细） */
    public static final String CATEGORY_ENTERTAINMENT = "EXP-INDIRECT-ENTERTAIN";

    /** 报销单ID */
    private Long reimbursementId;

    /** 报销单来源（PROJECT-项目报销/PERSONAL-个人报销；V2026_60） */
    private String sourceType;

    /** 费用类型 */
    private String expenseType;

    /** 费用科目编码（biz_fund_category.code；V2026_60 激活明细科目维度） */
    private String categoryCode;

    /** 金额 */
    private BigDecimal amount;

    /** 备注 */
    private String remark;

    /** 招待费专项明细（请求/展示透传，不持久化；落库于 biz_entertainment_detail） */
    @TableField(exist = false)
    private BizEntertainmentDetail entertainment;
}
