package com.zwinsight.workflow.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 流程定义扩展表（关联租户）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wf_process_def")
public class WfProcessDef extends BaseEntity {

    /**
     * 流程标识
     */
    private String processKey;

    /**
     * 流程名称
     */
    private String processName;

    /**
     * 业务类型ID
     */
    private Long businessTypeId;

    /**
     * 资源文件名
     */
    private String resourceName;

    /**
     * Flowable部署ID
     */
    private String deploymentId;

    /**
     * Flowable流程定义ID
     */
    private String processDefinitionId;

    /**
     * 版本号
     */
    private Integer versionNum;

    /**
     * 状态（1-启用 0-停用）
     */
    private Integer status;
}
