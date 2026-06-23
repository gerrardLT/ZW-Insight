package com.zwinsight.site.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 整改记录实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_rectification")
public class BizRectification extends BaseEntity {

    /** 检查记录ID */
    private Long inspectionId;

    /** 项目ID */
    private Long projectId;

    /** 整改内容 */
    private String rectificationContent;

    /** 状态（SUBMITTED/APPROVED/REJECTED） */
    private String status;

    /** 流程实例ID */
    private String workflowInstanceId;
}
