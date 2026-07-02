package com.zwinsight.basedata.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zwinsight.common.config.SecurityContextHolder;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.basedata.domain.BizSupplierBlacklist;
import com.zwinsight.basedata.service.SupplierBlacklistService;
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

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest
@Import({SupplierBlacklistController.class, TestSecurityConfig.class})
class SupplierBlacklistControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private SupplierBlacklistService blacklistService;

    @BeforeEach void setUp() { SecurityContextHolder.setTenantId(1L); SecurityContextHolder.setUserId(1L); }
    @AfterEach void tearDown() { SecurityContextHolder.clear(); }

    @Test @DisplayName("GET /api/v1/basedata/supplier-blacklist - 分页查询")
    void should_return_page() throws Exception {
        BizSupplierBlacklist item = new BizSupplierBlacklist();
        item.setId(1L); item.setSupplierName("问题供应吨");
        PageResult<BizSupplierBlacklist> page = new PageResult<>(List.of(item), 1, 1, 10, 1);
        when(blacklistService.page(anyInt(), anyInt())).thenReturn(page);

        mockMvc.perform(get("/api/v1/basedata/supplier-blacklist").param("page","1").param("size","10"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
    }

    @Test @DisplayName("POST /api/v1/basedata/supplier-blacklist - 加入黑名吨")
    void should_add() throws Exception {
        BizSupplierBlacklist bl = new BizSupplierBlacklist();
        bl.setSupplierId(100L); bl.setSupplierName("问题供应吨"); bl.setReason("多次违约");

        mockMvc.perform(post("/api/v1/basedata/supplier-blacklist")
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(bl)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        verify(blacklistService).add(eq(100L), eq("问题供应吨"), eq("多次违约"));
    }

    @Test @DisplayName("DELETE /api/v1/basedata/supplier-blacklist/{id} - 移出黑名吨")
    void should_remove() throws Exception {
        mockMvc.perform(delete("/api/v1/basedata/supplier-blacklist/1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        verify(blacklistService).remove(1L);
    }

    @Test @DisplayName("GET /api/v1/basedata/supplier-blacklist/check/{supplierId} - 检查黑名单")
    void should_check() throws Exception {
        when(blacklistService.isBlacklisted(100L)).thenReturn(true);
        mockMvc.perform(get("/api/v1/basedata/supplier-blacklist/check/100"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").value(true));
    }
}
