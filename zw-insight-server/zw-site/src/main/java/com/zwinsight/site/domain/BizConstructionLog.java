package com.zwinsight.site.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 施工日志实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_construction_log")
public class BizConstructionLog extends BaseEntity {

    /** 项目ID */
    private Long projectId;

    /** 日志日期 */
    private LocalDate logDate;

    /** 天气 */
    private String weather;

    /** 温度 */
    private String temperature;

    /** 风力 */
    private String wind;

    /** 施工人数 */
    private Integer workerCount;

    /** 生产记录 */
    private String productionRecord;

    /** 技术记录 */
    private String technicalRecord;

    /** 项目名称（展示用，不持久化） */
    @TableField(exist = false)
    private String projectName;
}
