package com.zwinsight.dashboard.controller;

import com.zwinsight.common.result.R;
import com.zwinsight.dashboard.dto.ProjectCostControlDTO;
import com.zwinsight.dashboard.service.ProjectCostControlService;
import com.zwinsight.dashboard.service.ProjectDashboardService;
import com.zwinsight.project.domain.BizProject;
import com.zwinsight.project.mapper.BizProjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ProjectDashboardController#getCostControl(Long)} 单元测试。
 *
 * <p>只覆盖 cost-control-backbone 新增的成本看板端点。该端点的职责边界是：
 * <b>先做项目存在性校验（不存在返回 404），再委派给 {@link ProjectCostControlService}</b>。
 * 服务层自身对「项目不存在」是容忍的（只留空 projectName），404 语义完全由本控制器承担，
 * 因此这两条分支必须分别钉住。
 *
 * <p><b>范围说明（不夸大）：</b>本控制器的其余端点
 * （{@code /budget}、{@code /progress}、{@code /contract}、{@code /output}、{@code /overview}）
 * 仍然没有单测，本文件不改变这一事实。
 */
@ExtendWith(MockitoExtension.class)
class ProjectDashboardControllerCostControlTest {

    private static final Long PROJECT_ID = 1L;

    @Mock private ProjectDashboardService projectDashboardService;
    @Mock private ProjectCostControlService projectCostControlService;
    @Mock private BizProjectMapper projectMapper;

    @InjectMocks
    private ProjectDashboardController controller;

    @Test
    @DisplayName("项目不存在：返回 404 且不调用成本看板服务（短路）")
    void getCostControl_projectNotFound_returns404AndShortCircuits() {
        when(projectMapper.selectById(PROJECT_ID)).thenReturn(null);

        R<ProjectCostControlDTO> r = controller.getCostControl(PROJECT_ID);

        assertThat(r.getCode()).isEqualTo(404);
        assertThat(r.getMessage()).isEqualTo("项目[1]不存在");
        assertThat(r.getData()).isNull();
        // 校验失败必须短路，不得继续触发聚合查询
        verify(projectCostControlService, never()).getCostControl(PROJECT_ID);
    }

    @Test
    @DisplayName("项目存在：返回 200 并透传服务层 DTO")
    void getCostControl_projectFound_returns200WithDto() {
        BizProject project = new BizProject();
        project.setId(PROJECT_ID);
        project.setProjectName("滨江花园一期");
        when(projectMapper.selectById(PROJECT_ID)).thenReturn(project);

        ProjectCostControlDTO dto = new ProjectCostControlDTO();
        dto.setProjectId(PROJECT_ID);
        dto.setProjectName("滨江花园一期");
        dto.setAccountCount(2);
        ProjectCostControlDTO.CostTotals totals = new ProjectCostControlDTO.CostTotals();
        totals.setCurrentTotal(new BigDecimal("120"));
        totals.setUsageRate(new BigDecimal("25.00"));
        dto.setTotals(totals);
        when(projectCostControlService.getCostControl(PROJECT_ID)).thenReturn(dto);

        R<ProjectCostControlDTO> r = controller.getCostControl(PROJECT_ID);

        assertThat(r.getCode()).isEqualTo(200);
        assertThat(r.getMessage()).isEqualTo("操作成功");
        assertThat(r.getData()).isSameAs(dto);
        assertThat(r.getData().getAccountCount()).isEqualTo(2);
        assertThat(r.getData().getTotals().getUsageRate()).isEqualByComparingTo("25.00");
        assertThat(r.getTimestamp()).isNotNull();
        verify(projectCostControlService).getCostControl(PROJECT_ID);
    }

    @Test
    @DisplayName("项目存在但无成本账户：仍返回 200，空数据不等于 404")
    void getCostControl_projectFoundButNoAccounts_returns200WithEmptyDto() {
        BizProject project = new BizProject();
        project.setId(PROJECT_ID);
        when(projectMapper.selectById(PROJECT_ID)).thenReturn(project);

        ProjectCostControlDTO dto = new ProjectCostControlDTO();
        dto.setProjectId(PROJECT_ID);
        dto.setAccountCount(0);
        when(projectCostControlService.getCostControl(PROJECT_ID)).thenReturn(dto);

        R<ProjectCostControlDTO> r = controller.getCostControl(PROJECT_ID);

        assertThat(r.getCode()).isEqualTo(200);
        assertThat(r.getData().getAccountCount()).isZero();
    }
}
