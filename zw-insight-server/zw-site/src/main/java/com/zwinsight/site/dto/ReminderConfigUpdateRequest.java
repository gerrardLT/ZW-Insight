package com.zwinsight.site.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.hibernate.validator.constraints.Range;

/**
 * 催办配置更新请求 DTO
 */
@Data
public class ReminderConfigUpdateRequest {

    /**
     * 催办间隔天数(1-30)
     */
    @NotNull(message = "催办间隔天数不能为空")
    @Range(min = 1, max = 30, message = "催办间隔天数必须在1-30之间")
    private Integer intervalDays;

    /**
     * 升级通知阈值天数
     */
    @NotNull(message = "升级通知阈值天数不能为空")
    @Range(min = 1, max = 365, message = "升级通知阈值天数必须在1-365之间")
    private Integer escalationDays;

    /**
     * 长期超期停止催办天数
     */
    @NotNull(message = "长期超期停止催办天数不能为空")
    @Range(min = 1, max = 365, message = "长期超期停止催办天数必须在1-365之间")
    private Integer longOverdueDays;

    /**
     * 是否启用自动催办
     */
    @NotNull(message = "启用状态不能为空")
    private Boolean enabled;
}
