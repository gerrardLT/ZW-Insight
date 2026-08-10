package com.zwinsight.hr.service;

import com.zwinsight.common.config.SecurityContextHolder;
import com.zwinsight.hr.domain.vo.HrStatisticsVO;
import com.zwinsight.hr.mapper.HrStatisticsMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * HrStatisticsService（人事统计）单元测试
 *
 * 覆盖场景:
 * - 总览数据聚合（在职/本月入离职/部门/岗位/工龄段）
 * - 入离职趋势合并：仅入职、仅离职、同月合并、按月份升序排序
 * - 趋势数据为 null 的兜底
 */
@ExtendWith(MockitoExtension.class)
class HrStatisticsServiceTest {

    private static final Long TENANT_ID = 9999L;

    @Mock
    private HrStatisticsMapper hrStatisticsMapper;

    @InjectMocks
    private HrStatisticsService hrStatisticsService;

    @BeforeEach
    void setUpTenant() {
        SecurityContextHolder.setTenantId(TENANT_ID);
    }

    @AfterEach
    void clearTenant() {
        SecurityContextHolder.clear();
    }

    private HrStatisticsVO.TrendStatItem trend(String month, Long entry, Long resign) {
        HrStatisticsVO.TrendStatItem item = new HrStatisticsVO.TrendStatItem();
        item.setMonth(month);
        item.setEntryCount(entry);
        item.setResignCount(resign);
        return item;
    }

    @Test
    @DisplayName("总览：各维度数据按租户聚合返回")
    void getOverview_aggregatesAllDimensions() {
        when(hrStatisticsMapper.countActiveUsers(eq(TENANT_ID))).thenReturn(100L);
        when(hrStatisticsMapper.countMonthlyEntry(eq(TENANT_ID))).thenReturn(5L);
        when(hrStatisticsMapper.countMonthlyResign(eq(TENANT_ID))).thenReturn(2L);
        HrStatisticsVO.DeptStatItem dept = new HrStatisticsVO.DeptStatItem();
        dept.setDeptId(1L);
        dept.setDeptName("工程部");
        dept.setCount(30L);
        when(hrStatisticsMapper.statByDept(eq(TENANT_ID))).thenReturn(List.of(dept));
        when(hrStatisticsMapper.statByPost(eq(TENANT_ID))).thenReturn(new ArrayList<>());
        when(hrStatisticsMapper.statBySeniority(eq(TENANT_ID))).thenReturn(new ArrayList<>());
        when(hrStatisticsMapper.statEntryTrend(eq(TENANT_ID)))
                .thenReturn(List.of(trend("2026-07", 3L, null)));
        when(hrStatisticsMapper.statResignTrend(eq(TENANT_ID)))
                .thenReturn(List.of(trend("2026-07", null, 1L)));

        HrStatisticsVO vo = hrStatisticsService.getOverview();

        assertThat(vo.getTotalActive()).isEqualTo(100L);
        assertThat(vo.getMonthlyEntry()).isEqualTo(5L);
        assertThat(vo.getMonthlyResign()).isEqualTo(2L);
        assertThat(vo.getByDept()).hasSize(1);
        assertThat(vo.getByDept().get(0).getDeptName()).isEqualTo("工程部");
        assertThat(vo.getMonthlyTrend()).hasSize(1);
    }

    @Test
    @DisplayName("趋势合并：同月入职与离职合并为一条记录")
    void getOverview_mergesSameMonthTrend() {
        stubBaseCounts();
        when(hrStatisticsMapper.statEntryTrend(eq(TENANT_ID)))
                .thenReturn(List.of(trend("2026-07", 3L, null)));
        when(hrStatisticsMapper.statResignTrend(eq(TENANT_ID)))
                .thenReturn(List.of(trend("2026-07", null, 1L)));

        HrStatisticsVO vo = hrStatisticsService.getOverview();

        assertThat(vo.getMonthlyTrend()).hasSize(1);
        HrStatisticsVO.TrendStatItem merged = vo.getMonthlyTrend().get(0);
        assertThat(merged.getEntryCount()).isEqualTo(3L);
        assertThat(merged.getResignCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("趋势合并：仅离职月份补 0 入职且按月份升序")
    void getOverview_resignOnlyMonthAndSorted() {
        stubBaseCounts();
        when(hrStatisticsMapper.statEntryTrend(eq(TENANT_ID)))
                .thenReturn(List.of(trend("2026-08", 2L, null)));
        when(hrStatisticsMapper.statResignTrend(eq(TENANT_ID)))
                .thenReturn(List.of(trend("2026-06", null, 4L)));

        HrStatisticsVO vo = hrStatisticsService.getOverview();

        assertThat(vo.getMonthlyTrend()).hasSize(2);
        assertThat(vo.getMonthlyTrend().get(0).getMonth()).isEqualTo("2026-06");
        assertThat(vo.getMonthlyTrend().get(0).getEntryCount()).isZero();
        assertThat(vo.getMonthlyTrend().get(0).getResignCount()).isEqualTo(4L);
        assertThat(vo.getMonthlyTrend().get(1).getMonth()).isEqualTo("2026-08");
        assertThat(vo.getMonthlyTrend().get(1).getResignCount()).isZero();
    }

    @Test
    @DisplayName("趋势合并：入离职数据均为 null 时返回空列表")
    void getOverview_nullTrends_returnsEmpty() {
        stubBaseCounts();
        when(hrStatisticsMapper.statEntryTrend(eq(TENANT_ID))).thenReturn(null);
        when(hrStatisticsMapper.statResignTrend(eq(TENANT_ID))).thenReturn(null);

        HrStatisticsVO vo = hrStatisticsService.getOverview();

        assertThat(vo.getMonthlyTrend()).isEmpty();
    }

    /**
     * 汇总与分组统计的公共 stub（趋势测试不关注这些维度）
     */
    private void stubBaseCounts() {
        when(hrStatisticsMapper.countActiveUsers(eq(TENANT_ID))).thenReturn(0L);
        when(hrStatisticsMapper.countMonthlyEntry(eq(TENANT_ID))).thenReturn(0L);
        when(hrStatisticsMapper.countMonthlyResign(eq(TENANT_ID))).thenReturn(0L);
        when(hrStatisticsMapper.statByDept(eq(TENANT_ID))).thenReturn(new ArrayList<>());
        when(hrStatisticsMapper.statByPost(eq(TENANT_ID))).thenReturn(new ArrayList<>());
        when(hrStatisticsMapper.statBySeniority(eq(TENANT_ID))).thenReturn(new ArrayList<>());
    }
}
