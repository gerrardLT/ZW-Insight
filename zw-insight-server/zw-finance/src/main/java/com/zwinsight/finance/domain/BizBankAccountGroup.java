package com.zwinsight.finance.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 银行账户分组实体（多级树，司库集中管控维度）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_bank_account_group")
public class BizBankAccountGroup extends BaseEntity {

    /** 分组名称（如 集团公司/XX项目部） */
    private String groupName;

    /** 分组编码 */
    private String groupCode;

    /** 父级ID（0为顶级） */
    private Long parentId;

    /** 层级（1-4） */
    private Integer level;

    /** 排序号 */
    private Integer sortOrder;

    /** 状态（ENABLED/DISABLED） */
    private String status;

    /** 备注 */
    private String remark;

    /** 子分组列表（非表字段，树形组装用） */
    @TableField(exist = false)
    private List<BizBankAccountGroup> children;
}
