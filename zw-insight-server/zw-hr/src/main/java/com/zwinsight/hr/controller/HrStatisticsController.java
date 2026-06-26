package com.zwinsight.hr.controller;

import com.zwinsight.common.result.R;
import com.zwinsight.hr.domain.vo.HrStatisticsVO;
import com.zwinsight.hr.service.HrStatisticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 人事统计接口
 * <p>
 * 提供按部门/岗位/工龄段/月度趋势维度的人员统计数据，
 * 以及在职总人数、本月入职/离职人数的汇总。
 */
@RestController
@RequestMapping("/api/v1/hr/statistics")
@RequiredArgsConstructor
public class HrStatisticsController {

    private final HrStatisticsService hrStatisticsService;

    /**
     * 获取人事统计总览数据
     * <p>
     * 返回数据包含：
     * - 在职总人数、本月入职人数、本月离职人数
     * - 按部门统计人数
     * - 按岗位统计人数
     * - 按工龄段统计人数（0-1年/1-3年/3-5年/5年以上）
     * - 近12个月入离职趋势
     */
    @GetMapping("/overview")
    public R<HrStatisticsVO> overview() {
        return R.ok(hrStatisticsService.getOverview());
    }
}
