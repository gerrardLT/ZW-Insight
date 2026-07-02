package com.zwinsight.dashboard.controller;

import com.zwinsight.common.config.SecurityContextHolder;
import com.zwinsight.dashboard.service.DashboardService;
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

import java.util.Map;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest
@Import({DashboardController.class, TestSecurityConfig.class})
class DashboardControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private DashboardService dashboardService;

    @BeforeEach void setUp() { SecurityContextHolder.setTenantId(1L); SecurityContextHolder.setUserId(1L); }
    @AfterEach void tearDown() { SecurityContextHolder.clear(); }

    @Test @DisplayName("GET /api/v1/dashboard/company-overview - 公司概览")
    void should_return_company_overview() throws Exception {
        when(dashboardService.getCompanyOverview()).thenReturn(Map.of("projectCount", 10, "contractAmount", 5000));
        mockMvc.perform(get("/api/v1/dashboard/company-overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.projectCount").value(10));
    }

    @Test @DisplayName("GET /api/v1/dashboard/budget-execution - 预算执行")
    void should_return_budget_execution() throws Exception {
        when(dashboardService.getBudgetExecution(eq(100L), any(), any()))
                .thenReturn(Map.of("totalBudget", 1000, "usedBudget", 600));
        mockMvc.perform(get("/api/v1/dashboard/budget-execution")
                        .param("projectId", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.totalBudget").value(1000));
    }

    @Test @DisplayName("GET /api/v1/dashboard/receivable-monitor - 应收款监控")
    void should_return_receivable_monitor() throws Exception {
        when(dashboardService.getReceivableMonitor()).thenReturn(Map.of("totalReceivable", 2000));
        mockMvc.perform(get("/api/v1/dashboard/receivable-monitor"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.totalReceivable").value(2000));
    }

    @Test @DisplayName("GET /api/v1/dashboard/tender-analysis - 投标分析")
    void should_return_tender_analysis() throws Exception {
        when(dashboardService.getTenderAnalysis()).thenReturn(Map.of("winRate", 0.65));
        mockMvc.perform(get("/api/v1/dashboard/tender-analysis"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test @DisplayName("GET /api/v1/dashboard/schedule-gantt/{projectId} - 进度甘特图")
    void should_return_schedule_gantt() throws Exception {
        when(dashboardService.getScheduleGantt(100L)).thenReturn(Map.of("tasks", 5));
        mockMvc.perform(get("/api/v1/dashboard/schedule-gantt/100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.tasks").value(5));
    }
}
