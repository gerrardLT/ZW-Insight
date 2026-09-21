package com.zwinsight.finance.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 银行账户实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_bank_account")
public class BizBankAccount extends BaseEntity {

    /** 账户名称 */
    private String accountName;

    /** 开户行 */
    private String bankName;

    /** 银行账号 */
    private String bankAccount;

    /** 账户类型（BASIC-基本户/GENERAL-一般户/SPECIAL-专用户） */
    private String accountType;

    /** 项目ID（可选，关联项目） */
    private Long projectId;

    /** 所属账户分组ID（biz_bank_account_group.id；55_V2026_53 新增） */
    private Long groupId;

    /** 状态（1-启用/0-停用） */
    private Integer status;
}
