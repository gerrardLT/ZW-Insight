package com.zwinsight.contract.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zwinsight.common.config.SecurityContextHolder;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.contract.domain.BizOutputReport;
import com.zwinsight.contract.service.OutputReportService;
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
@Import({OutputReportController.class, TestSecurityConfig.class})
class OutputReportControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private OutputReportService outputReportService;

    @BeforeEach void setUp() { SecurityContextHolder.setTenantId(1L); SecurityContextHolder.setUserId(1L); }
    @AfterEach void tearDown() { SecurityContextHolder.clear(); }

    @Test @DisplayName("GET /api/v1/contract/output - 返回产值报告分页列吨")
    void should_return_page() throws Exception {
        BizOutputReport r = new BizOutputReport();
        r.setId(1L); r.setReportPeriod("2024-06"); r.setCurrentOutput(new BigDecimal("500000"));
        PageResult<BizOutputReport> page = new PageResult<>(List.of(r), 1, 1, 10, 1);
        when(outputReportService.page(anyInt(), anyInt(), any(), any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/contract/output").param("page","1").param("size","10"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
    }

    @Test @DisplayName("POST /api/v1/contract/output - 新增产值报吨")
    void should_save() throws Exception {
        BizOutputReport r = new BizOutputReport();
        r.setProjectId(100L); r.setCurrentOutput(new BigDecimal("300000"));

        mockMvc.perform(post("/api/v1/contract/output")
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(r)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        verify(outputReportService).save(any(BizOutputReport.class));
    }

    @Test @DisplayName("POST /api/v1/contract/output/{id}/submit - 提交审批")
    void should_submit() throws Exception {
        mockMvc.perform(post("/api/v1/contract/output/1/submit"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        verify(outputReportService).submit(1L);
    }
}
