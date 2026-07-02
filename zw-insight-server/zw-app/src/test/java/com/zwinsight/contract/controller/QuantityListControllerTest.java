package com.zwinsight.contract.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zwinsight.common.config.SecurityContextHolder;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.contract.domain.BizQuantityList;
import com.zwinsight.contract.service.QuantityListService;
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
@Import({QuantityListController.class, TestSecurityConfig.class})
class QuantityListControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private QuantityListService quantityListService;

    @BeforeEach void setUp() { SecurityContextHolder.setTenantId(1L); SecurityContextHolder.setUserId(1L); }
    @AfterEach void tearDown() { SecurityContextHolder.clear(); }

    @Test @DisplayName("GET /api/v1/contract/quantity - 返回工程量清单分页列吨")
    void should_return_page() throws Exception {
        BizQuantityList q = new BizQuantityList();
        q.setId(1L); q.setItemName("钢筋"); q.setQuantity(new BigDecimal("100"));
        PageResult<BizQuantityList> page = new PageResult<>(List.of(q), 1, 1, 10, 1);
        when(quantityListService.page(anyInt(), anyInt(), any(), any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/contract/quantity").param("page","1").param("size","10"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
    }

    @Test @DisplayName("POST /api/v1/contract/quantity - 新增")
    void should_save() throws Exception {
        BizQuantityList q = new BizQuantityList();
        q.setProjectId(100L); q.setItemName("水泥"); q.setQuantity(new BigDecimal("200"));

        mockMvc.perform(post("/api/v1/contract/quantity")
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(q)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        verify(quantityListService).save(any(BizQuantityList.class));
    }

    @Test @DisplayName("PUT /api/v1/contract/quantity/{id} - 更新")
    void should_update() throws Exception {
        BizQuantityList q = new BizQuantityList();
        q.setItemName("更新材料");

        mockMvc.perform(put("/api/v1/contract/quantity/1")
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(q)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        verify(quantityListService).update(any(BizQuantityList.class));
    }

    @Test @DisplayName("DELETE /api/v1/contract/quantity/{id} - 删除")
    void should_delete() throws Exception {
        mockMvc.perform(delete("/api/v1/contract/quantity/1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        verify(quantityListService).delete(1L);
    }
}
