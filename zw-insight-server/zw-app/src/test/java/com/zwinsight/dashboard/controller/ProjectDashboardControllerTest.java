package com.zwinsight.dashboard.controller;

import com.zwinsight.common.config.SecurityContextHolder;
import com.zwinsight.dashboard.dto.BudgetExecutionDTO;
import com.zwinsight.dashboard.dto.ProgressDTO;
import com.zwinsight.dashboard.dto.ProjectDashboardDTO;
import com.zwinsight.dashboard.service.ProjectDashboardService;
import com.zwinsight.project.domain.BizProject;
import com.zwinsight.project.mapper.BizProjectMapper;
import com.zwinsight.test.TestSecurityConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest
@Import({ProjectDashboardController.class, TestSecurityConfig.class})
class ProjectDashboardControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private ProjectDashboardService projectDashboardService;
    @MockBean private BizProjectMapper projectMapper;

    @BeforeEach void setUp() { SecurityContextHolder.setTenantId(1L); SecurityContextHolder.setUserId(1L); }
    @AfterEach void tearDown() { SecurityContextHolder.clear(); }

    private void mockProjectExists(Long projectId) {
        BizProject project = new BizProject();
        project.setId(projectId);
        when(projectMapper.selectById(projectId)).thenReturn(project);
    }

    @Test @DisplayName("GET /api/v1/dashboard/project/{projectId}/budget - 项目预算执行")
    void should_return_budget() throws Exception {
        mockProjectExists(100L);
        BudgetExecutionDTO dto = new BudgetExecutionDTO();
        when(projectDashboardService.getBudgetExecution(100L)).thenReturn(dto);

        mockMvc.perform(get("/api/v1/dashboard/project/100/budget"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test @DisplayName("GET /api/v1/dashboard/project/{projectId}/progress - 项目进度")
    void should_return_progress() throws Exception {
        mockProjectExists(100L);
        ProgressDTO dto = new ProgressDTO();
        when(projectDashboardService.getProgress(100L)).thenReturn(dto);

        mockMvc.perform(get("/api/v1/dashboard/project/100/progress"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test @DisplayName("GET /api/v1/dashboard/project/{projectId}/overview - 项目看板聚合")
    void should_return_overview() throws Exception {
        mockProjectExists(100L);
        ProjectDashboardDTO dto = new ProjectDashboardDTO();
        when(projectDashboardService.getProjectOverview(100L)).thenReturn(dto);

        mockMvc.perform(get("/api/v1/dashboard/project/100/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test @DisplayName("GET /api/v1/dashboard/project/999/budget - 项目不存在返回404")
    void should_return_404_when_project_not_found() throws Exception {
        when(projectMapper.selectById(999L)).thenReturn(null);

        mockMvc.perform(get("/api/v1/dashboard/project/999/budget"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404));
    }
}
