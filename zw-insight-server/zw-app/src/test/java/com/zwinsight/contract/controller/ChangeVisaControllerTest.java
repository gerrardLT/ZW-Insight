package com.zwinsight.contract.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zwinsight.common.config.SecurityContextHolder;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.contract.domain.BizChangeVisa;
import com.zwinsight.contract.service.ChangeVisaService;
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
@Import({ChangeVisaController.class, TestSecurityConfig.class})
class ChangeVisaControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private ChangeVisaService changeVisaService;

    @BeforeEach void setUp() { SecurityContextHolder.setTenantId(1L); SecurityContextHolder.setUserId(1L); }
    @AfterEach void tearDown() { SecurityContextHolder.clear(); }

    @Test @DisplayName("GET /api/v1/contract/change-visa - 返回分页列表")
    void should_return_page() throws Exception {
        BizChangeVisa visa = new BizChangeVisa();
        visa.setId(1L); visa.setChangeType("DESIGN_CHANGE"); visa.setStatus("DRAFT");
        PageResult<BizChangeVisa> page = new PageResult<>(List.of(visa), 1, 1, 10, 1);
        when(changeVisaService.page(anyInt(), anyInt(), any(), any(), any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/contract/change-visa").param("page","1").param("size","10"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records[0].changeType").value("DESIGN_CHANGE"));
    }

    @Test @DisplayName("POST /api/v1/contract/change-visa - 新增变更签证")
    void should_save() throws Exception {
        BizChangeVisa visa = new BizChangeVisa();
        visa.setProjectId(100L); visa.setChangeReason("设计调整");
        visa.setChangeAmount(new BigDecimal("50000"));

        mockMvc.perform(post("/api/v1/contract/change-visa")
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(visa)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        verify(changeVisaService).save(any(BizChangeVisa.class));
    }

    @Test @DisplayName("POST /api/v1/contract/change-visa/{id}/submit - 提交审批")
    void should_submit() throws Exception {
        mockMvc.perform(post("/api/v1/contract/change-visa/1/submit"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        verify(changeVisaService).submit(1L);
    }
}
