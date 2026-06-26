package com.zwinsight.dashboard.controller;

import com.zwinsight.common.result.R;
import com.zwinsight.dashboard.dto.BudgetExecutionDTO;
import com.zwinsight.dashboard.dto.ContractReceiptDTO;
import com.zwinsight.dashboard.dto.OutputTrendDTO;
import com.zwinsight.dashboard.dto.ProgressDTO;
import com.zwinsight.dashboard.dto.ProjectDashboardDTO;
import com.zwinsight.dashboard.service.ProjectDashboardService;
import com.zwinsight.project.domain.BizProject;
import com.zwinsight.project.mapper.BizProjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 项目维度数据看板接口
 * <p>
 * 提供单项目的预算执行、进度完成率、合同回款、产值上报等数据看板。
 * 项目不存在时返回 404；无权限访问由现有数据权限拦截器统一处理（返回 403）。
 * 某维度无数据时由 {@link ProjectDashboardService} 返回零值和空列表，不影响其他维度。
 * </p>
 */
@RestController
@RequestMapping("/api/v1/dashboard/project")
@RequiredArgsConstructor
public class ProjectDashboardController {

    private final ProjectDashboardService projectDashboardService;
    private final BizProjectMapper projectMapper;

    /**
     * 项目预算执行数据
     *
     * @param projectId 项目ID
     * @return 预算执行DTO（预算总额、已使用金额、使用率、各科目明细）
     */
    @GetMapping("/{projectId}/budget")
    public R<BudgetExecutionDTO> getBudget(@PathVariable Long projectId) {
        R<BudgetExecutionDTO> notFound = checkProjectExists(projectId);
        if (notFound != null) {
            return notFound;
        }
        return R.ok(projectDashboardService.getBudgetExecution(projectId));
    }

    /**
     * 项目进度完成率数据
     *
     * @param projectId 项目ID
     * @return 进度DTO（总任务数、已完成数、完成百分比）
     */
    @GetMapping("/{projectId}/progress")
    public R<ProgressDTO> getProgress(@PathVariable Long projectId) {
        R<ProgressDTO> notFound = checkProjectExists(projectId);
        if (notFound != null) {
            return notFound;
        }
        return R.ok(projectDashboardService.getProgress(projectId));
    }

    /**
     * 项目合同与回款数据
     *
     * @param projectId 项目ID
     * @return 合同回款DTO（合同总额、累计开票、累计回款、回款率）
     */
    @GetMapping("/{projectId}/contract")
    public R<ContractReceiptDTO> getContract(@PathVariable Long projectId) {
        R<ContractReceiptDTO> notFound = checkProjectExists(projectId);
        if (notFound != null) {
            return notFound;
        }
        return R.ok(projectDashboardService.getContractReceipt(projectId));
    }

    /**
     * 项目产值上报汇总数据
     *
     * @param projectId 项目ID
     * @return 产值趋势DTO（累计产值、本月产值、近12月趋势）
     */
    @GetMapping("/{projectId}/output")
    public R<OutputTrendDTO> getOutput(@PathVariable Long projectId) {
        R<OutputTrendDTO> notFound = checkProjectExists(projectId);
        if (notFound != null) {
            return notFound;
        }
        return R.ok(projectDashboardService.getOutputTrend(projectId));
    }

    /**
     * 项目看板聚合数据（一次调用返回四个维度）
     *
     * @param projectId 项目ID
     * @return 项目看板聚合DTO（预算、进度、合同、产值）
     */
    @GetMapping("/{projectId}/overview")
    public R<ProjectDashboardDTO> getOverview(@PathVariable Long projectId) {
        R<ProjectDashboardDTO> notFound = checkProjectExists(projectId);
        if (notFound != null) {
            return notFound;
        }
        return R.ok(projectDashboardService.getProjectOverview(projectId));
    }

    /**
     * 校验项目是否存在。
     * <p>
     * 项目不存在时返回 404 错误响应；存在时返回 {@code null} 表示校验通过。
     * </p>
     *
     * @param projectId 项目ID
     * @param <T>       响应数据类型
     * @return 项目不存在时的 404 错误响应；存在时为 {@code null}
     */
    private <T> R<T> checkProjectExists(Long projectId) {
        BizProject project = projectMapper.selectById(projectId);
        if (project == null) {
            return R.fail(404, "项目[" + projectId + "]不存在");
        }
        return null;
    }
}
