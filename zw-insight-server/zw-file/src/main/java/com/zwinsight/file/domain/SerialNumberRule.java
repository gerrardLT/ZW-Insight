package com.zwinsight.file.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 编号规则实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("serial_number_rule")
public class SerialNumberRule extends BaseEntity {

    /**
     * 业务类型
     */
    private String businessType;

    /**
     * 规则前缀
     */
    private String rulePrefix;

    /**
     * 日期格式（如 yyyyMMdd、yyyyMM）
     */
    private String dateFormat;

    /**
     * 序号长度
     */
    private Integer seqLength;

    /**
     * 重置周期（MONTH/YEAR）
     */
    private String resetPeriod;

    /**
     * 描述
     */
    private String description;
}
