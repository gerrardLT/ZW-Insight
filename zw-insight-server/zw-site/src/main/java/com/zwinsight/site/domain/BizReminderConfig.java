package com.zwinsight.site.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.validator.constraints.Range;

/**
 * 整改催办配置实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_reminder_config")
public class BizReminderConfig extends BaseEntity {

    /**
     * 催办间隔天数(1-30)
     */
    @Range(min = 1, max = 30, message = "催办间隔天数必须在1-30之间")
    private Integer intervalDays;

    /**
     * 升级通知阈值天数
     */
    private Integer escalationDays;

    /**
     * 长期超期停止催办天数
     */
    private Integer longOverdueDays;

    /**
     * 是否启用(0-停用/1-启用)
     */
    private Boolean enabled;
}
