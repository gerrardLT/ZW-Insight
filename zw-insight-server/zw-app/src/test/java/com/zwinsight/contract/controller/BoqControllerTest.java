package com.zwinsight.contract.controller;

import com.zwinsight.common.config.SecurityContextHolder;
import com.zwinsight.contract.domain.BizBoqItem;
import com.zwinsight.contract.service.BoqService;
import com.zwinsight.test.TestSecurityConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest
@Import({BoqController.class, TestSecurityConfig.class})
class BoqControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private BoqService boqService;

    @BeforeEach void setUp() { SecurityContextHolder.setTenantId(1L); SecurityContextHolder.setUserId(1L); }
    @AfterEach void tearDown() { SecurityContextHolder.clear(); }

    @Test @DisplayName("GET /api/v1/contracts/{contractId}/boq - 返回BOQ吨")
    void should_return_boq_tree() throws Exception {
        BizBoqItem item = new BizBoqItem();
        item.setId(1L); item.setItemName("土建工程");
        when(boqService.getBoqTree(100L)).thenReturn(List.of(item));

        mockMvc.perform(get("/api/v1/contracts/100/boq"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].itemName").value("土建工程"));
    }

    @Test @DisplayName("GET /api/v1/contracts/{contractId}/boq/flat - 返回BOQ平铺列表")
    void should_return_boq_flat() throws Exception {
        BizBoqItem item = new BizBoqItem();
        item.setId(1L); item.setItemName("钢筋工程");
        when(boqService.getBoqFlat(100L)).thenReturn(List.of(item));

        mockMvc.perform(get("/api/v1/contracts/100/boq/flat"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
    }

    @Test @DisplayName("DELETE /api/v1/contracts/{contractId}/boq - 清除清单")
    void should_delete_boq() throws Exception {
        mockMvc.perform(delete("/api/v1/contracts/100/boq"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        verify(boqService).deleteBoq(100L);
    }
}
