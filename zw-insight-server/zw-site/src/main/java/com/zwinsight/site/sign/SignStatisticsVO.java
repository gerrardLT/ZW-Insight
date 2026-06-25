package com.zwinsight.site.sign;

import lombok.Data;

import java.util.List;

/**
 * 项目全员签到统计 VO
 */
@Data
public class SignStatisticsVO {

    /**
     * 项目ID
     */
    private Long projectId;

    /**
     * 月份（yyyy-MM）
     */
    private String month;

    /**
     * 项目总人数
     */
    private int totalUsers;

    /**
     * 人员签到统计列表
     */
    private List<UserSignStat> userStats;

    @Data
    public static class UserSignStat {
        /**
         * 用户ID
         */
        private Long userId;

        /**
         * 用户名
         */
        private String userName;

        /**
         * 签到天数
         */
        private int signDays;

        /**
         * 范围内签到天数
         */
        private int inRangeDays;

        /**
         * 范围外签到天数
         */
        private int outRangeDays;
    }
}
