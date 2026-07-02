package com.zwinsight.budget.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zwinsight.common.config.SecurityContextHolder;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.budget.domain.SysBudgetControlConfig;
import com.zwinsight.budget.dto.BudgetControlConfigDTO;
import com.zwinsight.budget.service.BudgetControlConfigService;
import com.zwinsight.test.TestSecurityConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest
@Import({BudgetControlConfigController.class, TestSecurityConfig.class})
class BudgetControlConfigControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private BudgetControlConfigService budgetControlConfigService;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.setTenantId(1L);
        SecurityContextHolder.setUserId(1L);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clear();
    }

    @Test
    @DisplayName("GET /api/v1/budget-control-configs - 返回分页列表")
    void should_return_page() throws Exception {
        SysBudgetControlConfig config = new SysBudgetControlConfig();
        config.setId(1L);
        config.setControlMode("BLOCK");
        config.setWarningThreshold(80);

        PageResult<SysBudgetControlConfig> pageResult = new PageResult<>(List.of(config), 1, 1, 10, 1);
        when(budgetControlConfigService.page(anyInt(), anyInt(), any())).thenReturn(pageResult);

        mockMvc.perform(get("/api/v1/budget-control-configs")
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records[0].controlMode").value("BLOCK"));
    }

    @Test
    @DisplayName("GET /api/v1/budget-control-configs/{id} - 返回详情")
    void should_return_by_id() throws Exception {
        SysBudgetControlConfig config = new SysBudgetControlConfig();
        config.setId(1L);
        config.setControlMode("WARN_ONLY");

        when(budgetControlConfigService.getById(1L)).thenReturn(config);

        mockMvc.perform(get("/api/v1/budget-control-configs/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.controlMode").value("WARN_ONLY"));
    }

    @Test
    @DisplayName("POST /api/v1/budget-control-configs - 新增配置")
    void should_save_config() throws Exception {
        BudgetControlConfigDTO dto = new BudgetControlConfigDTO();
        dto.setControlMode("BLOCK");
        dto.setWarningThreshold(80);

        mockMvc.perform(post("/api/v1/budget-control-configs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(budgetControlConfigService).save(any(BudgetControlConfigDTO.class));
    }

    @Test
    @DisplayName("GET /api/v1/budget-control-configs/project/{projectId} - 获取项目生效配置")
    void should_return_effective_config() throws Exception {
        SysBudgetControlConfig config = new SysBudgetControlConfig();
        config.setId(1L);
        config.setProjectId(100L);

        when(budgetControlConfigService.getEffectiveConfig(100L)).thenReturn(config);

        mockMvc.perform(get("/api/v1/budget-control-configs/project/100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.projectId").value(100));
    }
}
