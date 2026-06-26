package com.zwinsight.hr.mapper;

import com.zwinsight.hr.domain.vo.HrStatisticsVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 人事统计 Mapper
 */
@Mapper
public interface HrStatisticsMapper {

    /**
     * 查询在职总人数
     */
    Long countActiveUsers(@Param("tenantId") Long tenantId);

    /**
     * 查询本月入职人数
     */
    Long countMonthlyEntry(@Param("tenantId") Long tenantId);

    /**
     * 查询本月离职人数
     */
    Long countMonthlyResign(@Param("tenantId") Long tenantId);

    /**
     * 按部门统计人数
     */
    List<HrStatisticsVO.DeptStatItem> statByDept(@Param("tenantId") Long tenantId);

    /**
     * 按岗位统计人数
     */
    List<HrStatisticsVO.PostStatItem> statByPost(@Param("tenantId") Long tenantId);

    /**
     * 按工龄段统计人数
     */
    List<HrStatisticsVO.SeniorityStatItem> statBySeniority(@Param("tenantId") Long tenantId);

    /**
     * 查询近12个月入职趋势
     */
    List<HrStatisticsVO.TrendStatItem> statEntryTrend(@Param("tenantId") Long tenantId);

    /**
     * 查询近12个月离职趋势
     */
    List<HrStatisticsVO.TrendStatItem> statResignTrend(@Param("tenantId") Long tenantId);
}
