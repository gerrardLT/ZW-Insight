package com.zwinsight.budget.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zwinsight.common.config.SecurityContextHolder;
import com.zwinsight.budget.domain.BizBudgetConfig;
import com.zwinsight.budget.service.BudgetConfigService;
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

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest
@Import({BudgetConfigController.class, TestSecurityConfig.class})
class BudgetConfigControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private BudgetConfigService budgetConfigService;

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
    @DisplayName("GET /api/v1/budget/config/{projectId} - 返回预算管控配置")
    void should_return_config() throws Exception {
        BizBudgetConfig config = new BizBudgetConfig();
        config.setId(1L);
        config.setProjectId(100L);
        config.setControlMode("FORBID");

        when(budgetConfigService.getConfig(100L)).thenReturn(config);

        mockMvc.perform(get("/api/v1/budget/config/100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.controlMode").value("FORBID"));
    }

    @Test
    @DisplayName("POST /api/v1/budget/config - 保存配置")
    void should_save_config() throws Exception {
        BizBudgetConfig config = new BizBudgetConfig();
        config.setProjectId(100L);
        config.setControlMode("WARN");

        mockMvc.perform(post("/api/v1/budget/config")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(config)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(budgetConfigService).save(any(BizBudgetConfig.class));
    }

    @Test
    @DisplayName("PUT /api/v1/budget/config/{id} - 更新配置")
    void should_update_config() throws Exception {
        BizBudgetConfig config = new BizBudgetConfig();
        config.setControlMode("FORBID");

        mockMvc.perform(put("/api/v1/budget/config/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(config)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(budgetConfigService).update(any(BizBudgetConfig.class));
    }

    @Test
    @DisplayName("DELETE /api/v1/budget/config/{id} - 删除配置")
    void should_delete_config() throws Exception {
        mockMvc.perform(delete("/api/v1/budget/config/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(budgetConfigService).delete(1L);
    }
}
