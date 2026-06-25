package com.zwinsight.workflow.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 催办配置实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wf_urge_config")
public class WfUrgeConfig extends BaseEntity {

    /**
     * 催办超时时间（小时），超过此时间未处理则触发催办
     */
    private Integer timeoutHours;

    /**
     * 催办间隔（小时），两次催办之间的最小间隔
     */
    private Integer intervalHours;

    /**
     * 最大催办次数
     */
    private Integer maxUrgeCount;

    /**
     * 是否启用自动催办（0-禁用 1-启用）
     */
    private Integer autoUrgeEnabled;
}
