package com.zwinsight.basedata.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zwinsight.common.config.SecurityContextHolder;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.basedata.domain.BizSupplierEvaluation;
import com.zwinsight.basedata.service.SupplierEvaluationService;
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
@Import({SupplierEvaluationController.class, TestSecurityConfig.class})
class SupplierEvaluationControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private SupplierEvaluationService evaluationService;

    @BeforeEach void setUp() { SecurityContextHolder.setTenantId(1L); SecurityContextHolder.setUserId(1L); }
    @AfterEach void tearDown() { SecurityContextHolder.clear(); }

    @Test @DisplayName("GET /api/v1/basedata/supplier-evaluation - 分页查询评价")
    void should_return_page() throws Exception {
        BizSupplierEvaluation e = new BizSupplierEvaluation();
        e.setId(1L); e.setSupplierName("供应商A"); e.setTotalScore(new BigDecimal("4.5"));
        PageResult<BizSupplierEvaluation> page = new PageResult<>(List.of(e), 1, 1, 10, 1);
        when(evaluationService.page(anyInt(), anyInt(), any(), any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/basedata/supplier-evaluation").param("page","1").param("size","10"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
    }

    @Test @DisplayName("POST /api/v1/basedata/supplier-evaluation - 新增评价")
    void should_save() throws Exception {
        BizSupplierEvaluation e = new BizSupplierEvaluation();
        e.setSupplierId(100L); e.setQualityScore(5); e.setTimelinessScore(4);

        mockMvc.perform(post("/api/v1/basedata/supplier-evaluation")
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(e)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        verify(evaluationService).save(any(BizSupplierEvaluation.class));
    }

    @Test @DisplayName("GET /api/v1/basedata/supplier-evaluation/avg-score/{supplierId} - 平均评分")
    void should_return_avg_score() throws Exception {
        when(evaluationService.getAvgScore(100L)).thenReturn(new BigDecimal("4.2"));

        mockMvc.perform(get("/api/v1/basedata/supplier-evaluation/avg-score/100"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
    }

    @Test @DisplayName("DELETE /api/v1/basedata/supplier-evaluation/{id} - 删除评价")
    void should_delete() throws Exception {
        mockMvc.perform(delete("/api/v1/basedata/supplier-evaluation/1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        verify(evaluationService).delete(1L);
    }
}
