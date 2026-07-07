package com.zwinsight.file.batch.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 定时导出配置
 * <p>
 * 配置周期性自动导出任务，导出完成后通知指定接收人。
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_export_schedule")
public class BizExportSchedule extends BaseEntity {

    /** 定时导出名称 */
    private String scheduleName;

    /** 模块编码（对应 BatchImportExportService 的 moduleCode） */
    private String moduleCode;

    /** Cron 表达式（6位 Spring 格式） */
    private String cronExpression;

    /** 导出参数（JSON 字符串） */
    private String exportParams;

    /** 接收人邮箱（逗号分隔多个） */
    private String recipients;

    /** 是否启用（1启用/0停用） */
    private Integer enabled;

    /** 上次执行时间 */
    private LocalDateTime lastExecuteTime;

    /** 下次执行时间（由系统自动计算） */
    private LocalDateTime nextExecuteTime;
}
