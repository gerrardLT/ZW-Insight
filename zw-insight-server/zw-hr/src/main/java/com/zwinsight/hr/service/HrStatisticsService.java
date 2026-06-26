package com.zwinsight.hr.service;

import com.zwinsight.common.config.SecurityContextHolder;
import com.zwinsight.hr.domain.vo.HrStatisticsVO;
import com.zwinsight.hr.mapper.HrStatisticsMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 人事统计服务
 * <p>
 * 实现按部门/岗位/工龄段/月度趋势的聚合查询，
 * 数据基于 sys_user 表和 biz_entry_apply/biz_resign_apply 表实时计算。
 */
@Service
@RequiredArgsConstructor
public class HrStatisticsService {

    private final HrStatisticsMapper hrStatisticsMapper;

    /**
     * 获取人事统计总览数据
     *
     * @return 包含所有维度统计数据的 VO
     */
    public HrStatisticsVO getOverview() {
        Long tenantId = SecurityContextHolder.getTenantId();

        HrStatisticsVO vo = new HrStatisticsVO();

        // 汇总数据
        vo.setTotalActive(hrStatisticsMapper.countActiveUsers(tenantId));
        vo.setMonthlyEntry(hrStatisticsMapper.countMonthlyEntry(tenantId));
        vo.setMonthlyResign(hrStatisticsMapper.countMonthlyResign(tenantId));

        // 按部门统计
        vo.setByDept(hrStatisticsMapper.statByDept(tenantId));

        // 按岗位统计
        vo.setByPost(hrStatisticsMapper.statByPost(tenantId));

        // 按工龄段统计
        vo.setBySeniority(hrStatisticsMapper.statBySeniority(tenantId));

        // 近12个月入离职趋势（合并入职和离职数据）
        vo.setMonthlyTrend(mergeMonthlyTrend(
                hrStatisticsMapper.statEntryTrend(tenantId),
                hrStatisticsMapper.statResignTrend(tenantId)
        ));

        return vo;
    }

    /**
     * 合并入职趋势和离职趋势数据到统一的月度列表
     */
    private List<HrStatisticsVO.TrendStatItem> mergeMonthlyTrend(
            List<HrStatisticsVO.TrendStatItem> entryTrend,
            List<HrStatisticsVO.TrendStatItem> resignTrend) {

        Map<String, HrStatisticsVO.TrendStatItem> monthMap = new HashMap<>();

        // 填充入职数据
        if (entryTrend != null) {
            for (HrStatisticsVO.TrendStatItem item : entryTrend) {
                HrStatisticsVO.TrendStatItem merged = new HrStatisticsVO.TrendStatItem();
                merged.setMonth(item.getMonth());
                merged.setEntryCount(item.getEntryCount());
                merged.setResignCount(0L);
                monthMap.put(item.getMonth(), merged);
            }
        }

        // 填充离职数据
        if (resignTrend != null) {
            for (HrStatisticsVO.TrendStatItem item : resignTrend) {
                HrStatisticsVO.TrendStatItem merged = monthMap.get(item.getMonth());
                if (merged == null) {
                    merged = new HrStatisticsVO.TrendStatItem();
                    merged.setMonth(item.getMonth());
                    merged.setEntryCount(0L);
                    monthMap.put(item.getMonth(), merged);
                }
                merged.setResignCount(item.getResignCount());
            }
        }

        // 按月份排序
        List<HrStatisticsVO.TrendStatItem> result = new ArrayList<>(monthMap.values());
        result.sort((a, b) -> a.getMonth().compareTo(b.getMonth()));
        return result;
    }
}
