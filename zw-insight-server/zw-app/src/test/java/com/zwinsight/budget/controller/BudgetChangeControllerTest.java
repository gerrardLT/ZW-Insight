package com.zwinsight.budget.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zwinsight.common.config.SecurityContextHolder;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.budget.domain.BizBudgetChange;
import com.zwinsight.budget.domain.BizBudgetChangeDetail;
import com.zwinsight.budget.dto.BudgetChangeDTO;
import com.zwinsight.budget.dto.BudgetChangeDetailDTO;
import com.zwinsight.budget.service.BudgetChangeService;
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
@Import({BudgetChangeController.class, TestSecurityConfig.class})
class BudgetChangeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private BudgetChangeService budgetChangeService;

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
    @DisplayName("GET /api/v1/budget/change - 返回变更分页列表")
    void should_return_page() throws Exception {
        BizBudgetChange change = new BizBudgetChange();
        change.setId(1L);
        change.setChangeCode("BC-001");
        change.setStatus("DRAFT");

        PageResult<BizBudgetChange> pageResult = new PageResult<>(List.of(change), 1, 1, 10, 1);
        when(budgetChangeService.page(anyInt(), anyInt(), any(), any())).thenReturn(pageResult);

        mockMvc.perform(get("/api/v1/budget/change")
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records[0].changeCode").value("BC-001"));
    }

    @Test
    @DisplayName("GET /api/v1/budget/change/{id} - 返回变更详情")
    void should_return_by_id() throws Exception {
        BizBudgetChange change = new BizBudgetChange();
        change.setId(1L);
        change.setChangeReason("工程量增吨");

        when(budgetChangeService.getById(1L)).thenReturn(change);

        mockMvc.perform(get("/api/v1/budget/change/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.changeReason").value("工程量增吨"));
    }

    @Test
    @DisplayName("GET /api/v1/budget/change/{id}/details - 返回变更明细")
    void should_return_details() throws Exception {
        BizBudgetChangeDetail detail = new BizBudgetChangeDetail();
        detail.setId(1L);
        detail.setItemName("钢筋");
        detail.setAdjustAmount(new BigDecimal("50000"));

        when(budgetChangeService.getDetailsByChangeId(1L)).thenReturn(List.of(detail));

        mockMvc.perform(get("/api/v1/budget/change/1/details"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].itemName").value("钢筋"));
    }

    @Test
    @DisplayName("POST /api/v1/budget/change - 新增变更吨")
    void should_save_change() throws Exception {
        BudgetChangeDTO dto = new BudgetChangeDTO();
        dto.setProjectId(100L);
        dto.setBudgetId(1L);
        dto.setChangeReason("设计变更");

        BudgetChangeDetailDTO detailDTO = new BudgetChangeDetailDTO();
        detailDTO.setBudgetDetailId(1L);
        detailDTO.setOriginalAmount(new BigDecimal("100000"));
        detailDTO.setAdjustAmount(new BigDecimal("20000"));
        dto.setDetails(List.of(detailDTO));

        mockMvc.perform(post("/api/v1/budget/change")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(budgetChangeService).save(any(BudgetChangeDTO.class));
    }

    @Test
    @DisplayName("POST /api/v1/budget/change/{id}/submit - 提交变更审批")
    void should_submit_change() throws Exception {
        mockMvc.perform(post("/api/v1/budget/change/1/submit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(budgetChangeService).submit(1L);
    }

    @Test
    @DisplayName("GET /api/v1/budget/change/trace - 查询变更轨迹")
    void should_return_change_trace() throws Exception {
        BizBudgetChange change = new BizBudgetChange();
        change.setId(1L);
        change.setStatus("APPROVED");

        when(budgetChangeService.getChangeTraceByProject(100L)).thenReturn(List.of(change));

        mockMvc.perform(get("/api/v1/budget/change/trace")
                        .param("projectId", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].status").value("APPROVED"));
    }
}
