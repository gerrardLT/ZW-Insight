package com.zwinsight.contract.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zwinsight.common.config.SecurityContextHolder;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.contract.domain.BizFinalSettlement;
import com.zwinsight.contract.service.FinalSettlementService;
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
@Import({FinalSettlementController.class, TestSecurityConfig.class})
class FinalSettlementControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private FinalSettlementService finalSettlementService;

    @BeforeEach void setUp() { SecurityContextHolder.setTenantId(1L); SecurityContextHolder.setUserId(1L); }
    @AfterEach void tearDown() { SecurityContextHolder.clear(); }

    @Test @DisplayName("GET /api/v1/contract/settlement - 返回结算分页列表")
    void should_return_page() throws Exception {
        BizFinalSettlement s = new BizFinalSettlement();
        s.setId(1L); s.setSettlementAmount(new BigDecimal("2000000")); s.setStatus("DRAFT");
        PageResult<BizFinalSettlement> page = new PageResult<>(List.of(s), 1, 1, 10, 1);
        when(finalSettlementService.page(anyInt(), anyInt(), any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/contract/settlement").param("page","1").param("size","10"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
    }

    @Test @DisplayName("POST /api/v1/contract/settlement - 新增结算")
    void should_save() throws Exception {
        BizFinalSettlement s = new BizFinalSettlement();
        s.setProjectId(100L); s.setSettlementAmount(new BigDecimal("1500000"));

        mockMvc.perform(post("/api/v1/contract/settlement")
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(s)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        verify(finalSettlementService).save(any(BizFinalSettlement.class));
    }

    @Test @DisplayName("POST /api/v1/contract/settlement/{id}/submit - 提交结算审批")
    void should_submit() throws Exception {
        mockMvc.perform(post("/api/v1/contract/settlement/1/submit"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        verify(finalSettlementService).submit(1L);
    }
}
