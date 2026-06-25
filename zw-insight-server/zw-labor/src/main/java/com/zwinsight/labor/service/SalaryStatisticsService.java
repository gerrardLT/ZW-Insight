package com.zwinsight.labor.service;

import com.zwinsight.common.result.PageResult;
import com.zwinsight.labor.vo.SalaryCompareVO;
import com.zwinsight.labor.vo.SalaryDetailVO;
import com.zwinsight.labor.vo.SalaryMonthlyReport;
import com.zwinsight.labor.vo.SalaryStatsSummary;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 薪资统计服务接口
 */
public interface SalaryStatisticsService {

    /**
     * 按班组汇总薪资统计
     *
     * @param projectId 项目ID
     * @param month     统计月份（格式：YYYY-MM）
     * @return 薪资统计汇总
     */
    SalaryStatsSummary getStatsByTeam(Long projectId, String month);

    /**
     * 班组内工人薪资明细
     *
     * @param projectId 项目ID
     * @param month     统计月份（格式：YYYY-MM）
     * @param teamId    班组ID
     * @param page      页码
     * @param size      每页大小
     * @return 分页结果
     */
    PageResult<SalaryDetailVO> getTeamDetail(Long projectId, String month, Long teamId, int page, int size);

    /**
     * 生成月度报表数据
     *
     * @param projectId 项目ID
     * @param month     统计月份（格式：YYYY-MM）
     * @return 月度报表
     */
    SalaryMonthlyReport generateMonthlyReport(Long projectId, String month);

    /**
     * 导出薪资报表（EasyExcel 多 Sheet 导出）
     *
     * @param projectId 项目ID
     * @param month     统计月份（格式：YYYY-MM）
     * @param response  HTTP 响应
     */
    void exportReport(Long projectId, String month, HttpServletResponse response);

    /**
     * 获取同比环比数据
     *
     * @param projectId 项目ID
     * @param month     统计月份（格式：YYYY-MM）
     * @return 同比环比数据
     */
    SalaryCompareVO getCompareData(Long projectId, String month);
}
