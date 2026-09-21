package com.zwinsight.finance.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 资金收支分类科目实体（单表树，3级）
 * <p>四维分类体系的维度1（资金性质）+ 维度2（业务来源）落地面，
 * 付款申请/回款登记通过 code 引用本表（docs/资金流转深度调研报告.md 第五节）。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_fund_category")
public class BizFundCategory extends BaseEntity {

    /** 科目编码（如 EXP-DIRECT-MATERIAL，全局唯一） */
    private String code;

    /** 科目名称 */
    private String name;

    /** 方向（INCOME-收入 / EXPENSE-支出） */
    private String direction;

    /** 父级ID（0为顶级） */
    private Long parentId;

    /** 层级（1-3） */
    private Integer level;

    /** 排序号 */
    private Integer sortOrder;

    /** 状态（ENABLED-启用 / DISABLED-停用） */
    private String status;

    /** 系统内置（1-是，不可删除） */
    private Integer isSystem;

    /** 子科目列表（非表字段，树形组装用） */
    @TableField(exist = false)
    private List<BizFundCategory> children;
}
