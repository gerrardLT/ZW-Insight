package com.zwinsight.basedata.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zwinsight.common.config.SecurityContextHolder;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.basedata.domain.BdOwner;
import com.zwinsight.basedata.service.OwnerService;
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
@Import({OwnerController.class, TestSecurityConfig.class})
class OwnerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private OwnerService ownerService;

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
    @DisplayName("GET /api/v1/basedata/owner - 返回甲方分页列表")
    void should_return_page() throws Exception {
        BdOwner owner = new BdOwner();
        owner.setId(1L);
        owner.setOwnerName("城投集团");
        owner.setStatus(1);

        PageResult<BdOwner> pageResult = new PageResult<>(List.of(owner), 1, 1, 10, 1);
        when(ownerService.page(anyInt(), anyInt(), any(), any())).thenReturn(pageResult);

        mockMvc.perform(get("/api/v1/basedata/owner")
                        .param("page", "1").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records[0].ownerName").value("城投集团"));
    }

    @Test
    @DisplayName("GET /api/v1/basedata/owner/{id} - 返回甲方详情")
    void should_return_by_id() throws Exception {
        BdOwner owner = new BdOwner();
        owner.setId(1L);
        owner.setOwnerName("城投集团");
        when(ownerService.getById(1L)).thenReturn(owner);

        mockMvc.perform(get("/api/v1/basedata/owner/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.ownerName").value("城投集团"));
    }

    @Test
    @DisplayName("POST /api/v1/basedata/owner - 新增甲方")
    void should_save_owner() throws Exception {
        BdOwner owner = new BdOwner();
        owner.setOwnerName("新甲吨");

        mockMvc.perform(post("/api/v1/basedata/owner")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(ownerService).save(any(BdOwner.class));
    }

    @Test
    @DisplayName("DELETE /api/v1/basedata/owner/{id} - 删除甲方")
    void should_delete_owner() throws Exception {
        mockMvc.perform(delete("/api/v1/basedata/owner/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        verify(ownerService).delete(1L);
    }
}
