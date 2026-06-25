package com.zwinsight.workflow.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 审批委托配置实体
 * <p>
 * 用于设定某用户在指定时间段内，将审批任务自动转给代理人处理。
 * 委托期间内新分配给该用户的任务将自动转给代理人。
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wf_delegate_config")
public class WfDelegateConfig extends BaseEntity {

    /**
     * 委托人用户ID
     */
    private Long delegatorId;

    /**
     * 代理人用户ID
     */
    private Long delegateId;

    /**
     * 委托开始时间
     */
    private LocalDateTime startTime;

    /**
     * 委托结束时间
     */
    private LocalDateTime endTime;

    /**
     * 状态：ACTIVE-生效中 EXPIRED-已过期 CANCELLED-已取消
     */
    private String status;

    /**
     * 委托原因/备注
     */
    private String reason;
}
