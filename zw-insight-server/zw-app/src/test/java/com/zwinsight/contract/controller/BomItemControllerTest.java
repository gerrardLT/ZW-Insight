package com.zwinsight.contract.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zwinsight.common.config.SecurityContextHolder;
import com.zwinsight.contract.domain.BizBomItem;
import com.zwinsight.contract.service.BomItemService;
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
@Import({BomItemController.class, TestSecurityConfig.class})
class BomItemControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private BomItemService bomItemService;

    @BeforeEach void setUp() { SecurityContextHolder.setTenantId(1L); SecurityContextHolder.setUserId(1L); }
    @AfterEach void tearDown() { SecurityContextHolder.clear(); }

    @Test @DisplayName("GET /api/v1/contract/bom/{contractId} - 返回BOM列表")
    void should_return_list() throws Exception {
        BizBomItem item = new BizBomItem();
        item.setId(1L);
        when(bomItemService.list(100L)).thenReturn(List.of(item));

        mockMvc.perform(get("/api/v1/contract/bom/100"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
    }

    @Test @DisplayName("POST /api/v1/contract/bom - 新增BOM")
    void should_save() throws Exception {
        BizBomItem item = new BizBomItem();
        mockMvc.perform(post("/api/v1/contract/bom")
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(item)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        verify(bomItemService).save(any(BizBomItem.class));
    }

    @Test @DisplayName("DELETE /api/v1/contract/bom/{id} - 删除BOM")
    void should_delete() throws Exception {
        mockMvc.perform(delete("/api/v1/contract/bom/1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        verify(bomItemService).delete(1L);
    }
}
