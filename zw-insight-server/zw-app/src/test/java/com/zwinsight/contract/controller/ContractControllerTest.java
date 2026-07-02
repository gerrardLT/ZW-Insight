package com.zwinsight.contract.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zwinsight.common.config.SecurityContextHolder;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.contract.domain.BizConstructionContract;
import com.zwinsight.contract.domain.BizContractDetail;
import com.zwinsight.contract.service.ConstructionContractService;
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
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest
@Import({ContractController.class, TestSecurityConfig.class})
class ContractControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ConstructionContractService contractService;

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
    @DisplayName("GET /api/v1/contract - 返回分页列表")
    void should_return_page() throws Exception {
        BizConstructionContract contract = new BizConstructionContract();
        contract.setId(1L);
        contract.setContractCode("HT-001");
        contract.setContractAmount(new BigDecimal("1000000"));
        contract.setStatus("DRAFT");

        PageResult<BizConstructionContract> pageResult = new PageResult<>(
                List.of(contract), 1, 1, 10, 1);
        when(contractService.page(anyInt(), anyInt(), any(), any())).thenReturn(pageResult);

        mockMvc.perform(get("/api/v1/contract")
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records").isArray())
                .andExpect(jsonPath("$.data.records[0].contractCode").value("HT-001"));
    }

    @Test
    @DisplayName("GET /api/v1/contract/{id} - 返回合同详情")
    void should_return_contract_by_id() throws Exception {
        BizConstructionContract contract = new BizConstructionContract();
        contract.setId(1L);
        contract.setContractCode("HT-001");
        contract.setProjectId(100L);
        contract.setStatus("EFFECTIVE");

        when(contractService.getById(1L)).thenReturn(contract);

        mockMvc.perform(get("/api/v1/contract/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.contractCode").value("HT-001"));
    }

    @Test
    @DisplayName("POST /api/v1/contract - 新增合同")
    void should_save_contract() throws Exception {
        BizConstructionContract contract = new BizConstructionContract();
        contract.setContractCode("HT-002");
        contract.setContractAmount(new BigDecimal("500000"));

        mockMvc.perform(post("/api/v1/contract")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(contract)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(contractService).save(any(BizConstructionContract.class));
    }

    @Test
    @DisplayName("PUT /api/v1/contract/{id} - 更新合同")
    void should_update_contract() throws Exception {
        BizConstructionContract contract = new BizConstructionContract();
        contract.setContractCode("HT-002-UPDATED");

        mockMvc.perform(put("/api/v1/contract/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(contract)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(contractService).update(any(BizConstructionContract.class));
    }

    @Test
    @DisplayName("DELETE /api/v1/contract/{id} - 删除合同")
    void should_delete_contract() throws Exception {
        mockMvc.perform(delete("/api/v1/contract/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(contractService).delete(1L);
    }

    @Test
    @DisplayName("GET /api/v1/contract/{id}/details - 返回合同明细列表")
    void should_return_contract_details() throws Exception {
        BizContractDetail detail = new BizContractDetail();
        detail.setId(1L);
        detail.setItemName("钢筋");
        detail.setQuantity(new BigDecimal("100"));

        when(contractService.getDetails(1L)).thenReturn(List.of(detail));

        mockMvc.perform(get("/api/v1/contract/1/details"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].itemName").value("钢筋"));
    }

    @Test
    @DisplayName("POST /api/v1/contract/{id}/submit - 提交合同审批")
    void should_submit_contract() throws Exception {
        mockMvc.perform(post("/api/v1/contract/1/submit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(contractService).submit(1L);
    }
}
