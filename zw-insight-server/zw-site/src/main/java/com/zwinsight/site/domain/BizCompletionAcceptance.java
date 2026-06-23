package com.zwinsight.site.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 竣工验收实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_completion_acceptance")
public class BizCompletionAcceptance extends BaseEntity {

    /** 项目ID */
    private Long projectId;

    /** 验收日期 */
    private LocalDate acceptanceDate;

    /** 验收报告 */
    private String acceptanceReport;

    /** 状态（DRAFT/APPROVED） */
    private String status;

    /** 流程实例ID */
    private String workflowInstanceId;
}
