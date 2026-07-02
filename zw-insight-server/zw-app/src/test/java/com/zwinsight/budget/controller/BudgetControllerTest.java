package com.zwinsight.budget.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zwinsight.common.config.SecurityContextHolder;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.budget.domain.BizBudget;
import com.zwinsight.budget.service.BudgetService;
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

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest
@Import({BudgetController.class, TestSecurityConfig.class})
class BudgetControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private BudgetService budgetService;

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
    @DisplayName("GET /api/v1/budget - 返回分页列表")
    void should_return_page() throws Exception {
        BizBudget budget = new BizBudget();
        budget.setId(1L);
        budget.setProjectId(100L);
        budget.setTotalAmount(new BigDecimal("5000000"));
        budget.setStatus("DRAFT");

        PageResult<BizBudget> pageResult = new PageResult<>(List.of(budget), 1, 1, 10, 1);
        when(budgetService.page(anyInt(), anyInt(), any())).thenReturn(pageResult);

        mockMvc.perform(get("/api/v1/budget")
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records").isArray())
                .andExpect(jsonPath("$.data.records[0].status").value("DRAFT"));
    }

    @Test
    @DisplayName("GET /api/v1/budget/{id} - 返回预算详情")
    void should_return_budget_by_id() throws Exception {
        BizBudget budget = new BizBudget();
        budget.setId(1L);
        budget.setBudgetType("ORIGINAL");
        budget.setTotalAmount(new BigDecimal("3000000"));

        when(budgetService.getById(1L)).thenReturn(budget);

        mockMvc.perform(get("/api/v1/budget/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.budgetType").value("ORIGINAL"));
    }

    @Test
    @DisplayName("POST /api/v1/budget - 新增预算")
    void should_save_budget() throws Exception {
        BizBudget budget = new BizBudget();
        budget.setProjectId(100L);
        budget.setTotalAmount(new BigDecimal("1000000"));

        mockMvc.perform(post("/api/v1/budget")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(budget)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(budgetService).save(any(BizBudget.class));
    }

    @Test
    @DisplayName("DELETE /api/v1/budget/{id} - 删除预算")
    void should_delete_budget() throws Exception {
        mockMvc.perform(delete("/api/v1/budget/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(budgetService).delete(1L);
    }

    @Test
    @DisplayName("GET /api/v1/budget/project/{projectId} - 按项目查预算")
    void should_return_budget_by_project() throws Exception {
        BizBudget budget = new BizBudget();
        budget.setId(1L);
        budget.setProjectId(100L);

        when(budgetService.getByProject(100L)).thenReturn(budget);

        mockMvc.perform(get("/api/v1/budget/project/100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.projectId").value(100));
    }
}
