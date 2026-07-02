package com.zwinsight.contract.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zwinsight.common.config.SecurityContextHolder;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.contract.domain.BizOtherContract;
import com.zwinsight.contract.service.OtherContractService;
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
@Import({OtherContractController.class, TestSecurityConfig.class})
class OtherContractControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private OtherContractService otherContractService;

    @BeforeEach void setUp() { SecurityContextHolder.setTenantId(1L); SecurityContextHolder.setUserId(1L); }
    @AfterEach void tearDown() { SecurityContextHolder.clear(); }

    @Test @DisplayName("GET /api/v1/contract/other - 返回分页列表")
    void should_return_page() throws Exception {
        BizOtherContract c = new BizOtherContract();
        c.setId(1L); c.setContractName("监理合同"); c.setContractCategory("OTHER_EXPENSE");
        PageResult<BizOtherContract> page = new PageResult<>(List.of(c), 1, 1, 10, 1);
        when(otherContractService.page(anyInt(), anyInt(), any(), any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/contract/other").param("page","1").param("size","10"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records[0].contractName").value("监理合同"));
    }

    @Test @DisplayName("GET /api/v1/contract/other/{id} - 返回详情")
    void should_return_by_id() throws Exception {
        BizOtherContract c = new BizOtherContract();
        c.setId(1L); c.setContractName("监理合同");
        when(otherContractService.getById(1L)).thenReturn(c);

        mockMvc.perform(get("/api/v1/contract/other/1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
    }

    @Test @DisplayName("POST /api/v1/contract/other - 新增")
    void should_save() throws Exception {
        BizOtherContract c = new BizOtherContract();
        c.setContractName("咨询合同"); c.setContractAmount(new BigDecimal("100000"));

        mockMvc.perform(post("/api/v1/contract/other")
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(c)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        verify(otherContractService).save(any(BizOtherContract.class));
    }

    @Test @DisplayName("DELETE /api/v1/contract/other/{id} - 删除")
    void should_delete() throws Exception {
        mockMvc.perform(delete("/api/v1/contract/other/1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        verify(otherContractService).delete(1L);
    }
}
