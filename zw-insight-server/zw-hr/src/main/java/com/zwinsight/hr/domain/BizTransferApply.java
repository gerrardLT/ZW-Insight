package com.zwinsight.hr.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 调动申请实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_transfer_apply")
public class BizTransferApply extends BaseEntity {

    /** 用户ID */
    private Long userId;

    /** 用户姓名 */
    private String userName;

    /** 调动日期 */
    private LocalDate transferDate;

    /** 原部门ID */
    private Long fromOrgId;

    /** 原岗位ID */
    private Long fromPostId;

    /** 新部门ID */
    private Long toOrgId;

    /** 新岗位ID */
    private Long toPostId;

    /** 备注 */
    private String remark;

    /** 状态（DRAFT/APPROVED） */
    private String status;
}
