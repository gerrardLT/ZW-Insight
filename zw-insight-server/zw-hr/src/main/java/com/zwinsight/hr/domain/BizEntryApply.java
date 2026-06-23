package com.zwinsight.hr.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 入职申请实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_entry_apply")
public class BizEntryApply extends BaseEntity {

    /** 用户名 */
    private String username;

    /** 真实姓名 */
    private String realName;

    /** 性别 */
    private String gender;

    /** 出生日期 */
    private LocalDate birthDate;

    /** 身份证号 */
    private String idCard;

    /** 手机号 */
    private String phone;

    /** 入职日期 */
    private LocalDate entryDate;

    /** 部门ID */
    private Long orgId;

    /** 岗位ID */
    private Long postId;

    /** 状态（DRAFT/APPROVED） */
    private String status;

    /** 流程实例ID */
    private String workflowInstanceId;
}
