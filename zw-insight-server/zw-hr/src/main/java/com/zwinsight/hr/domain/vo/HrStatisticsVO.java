package com.zwinsight.hr.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 人事统计总览 VO
 */
@Data
public class HrStatisticsVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 在职总人数 */
    private Long totalActive;

    /** 本月入职人数 */
    private Long monthlyEntry;

    /** 本月离职人数 */
    private Long monthlyResign;

    /** 按部门统计 */
    private List<DeptStatItem> byDept;

    /** 按岗位统计 */
    private List<PostStatItem> byPost;

    /** 按工龄段统计 */
    private List<SeniorityStatItem> bySeniority;

    /** 近12个月入离职趋势 */
    private List<TrendStatItem> monthlyTrend;

    /**
     * 部门统计项
     */
    @Data
    public static class DeptStatItem implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        private Long deptId;
        private String deptName;
        private Long count;
    }

    /**
     * 岗位统计项
     */
    @Data
    public static class PostStatItem implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        private Long postId;
        private String postName;
        private Long count;
    }

    /**
     * 工龄段统计项
     */
    @Data
    public static class SeniorityStatItem implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        /** 工龄段（0-1年, 1-3年, 3-5年, 5年以上） */
        private String range;
        private Long count;
    }

    /**
     * 月度趋势统计项
     */
    @Data
    public static class TrendStatItem implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        /** 月份（格式：yyyy-MM） */
        private String month;

        /** 入职人数 */
        private Long entryCount;

        /** 离职人数 */
        private Long resignCount;
    }
}
