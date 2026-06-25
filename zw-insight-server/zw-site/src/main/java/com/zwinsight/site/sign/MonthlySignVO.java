package com.zwinsight.site.sign;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * 月度签到日历 VO
 */
@Data
public class MonthlySignVO {

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 项目ID
     */
    private Long projectId;

    /**
     * 月份（yyyy-MM）
     */
    private String month;

    /**
     * 签到天数
     */
    private int signDays;

    /**
     * 在范围内签到天数
     */
    private int inRangeDays;

    /**
     * 每日签到记录
     */
    private List<DailySign> dailyRecords;

    @Data
    public static class DailySign {
        /**
         * 日期
         */
        private LocalDate date;

        /**
         * 是否签到
         */
        private boolean signed;

        /**
         * 是否在范围内
         */
        private boolean inRange;
    }
}
