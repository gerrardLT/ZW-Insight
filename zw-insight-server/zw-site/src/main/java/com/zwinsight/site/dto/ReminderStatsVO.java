package com.zwinsight.site.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 催办统计 VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReminderStatsVO {

    /**
     * 超期整改总数
     */
    private Long totalOverdueCount;

    /**
     * 已催办次数
     */
    private Long totalReminderCount;

    /**
     * 已升级通知次数
     */
    private Long escalatedCount;
}
