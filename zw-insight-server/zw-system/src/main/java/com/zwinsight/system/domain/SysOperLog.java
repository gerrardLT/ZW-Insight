package com.zwinsight.system.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 操作日志实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_oper_log")
public class SysOperLog extends BaseEntity {

    /**
     * 模块名称
     */
    private String module;

    /**
     * 操作类型（INSERT/UPDATE/DELETE）
     */
    private String operType;

    /**
     * IP地址
     */
    private String ipAddress;

    /**
     * 操作人姓名
     */
    private String operName;

    /**
     * 操作人账号
     */
    private String operAccount;

    /**
     * 操作时间
     */
    private LocalDateTime operTime;

    /**
     * 方法名称
     */
    private String methodName;

    /**
     * 操作描述
     */
    private String description;

    /**
     * 请求参数
     */
    private String params;

    /**
     * 返回结果
     */
    private String result;
}
