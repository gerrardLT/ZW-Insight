package com.zwinsight.basedata.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zwinsight.common.config.SecurityContextHolder;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.basedata.domain.BdInspectionScheme;
import com.zwinsight.basedata.service.InspectionSchemeService;
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
@Import({InspectionSchemeController.class, TestSecurityConfig.class})
class InspectionSchemeControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private InspectionSchemeService schemeService;

    @BeforeEach void setUp() { SecurityContextHolder.setTenantId(1L); SecurityContextHolder.setUserId(1L); }
    @AfterEach void tearDown() { SecurityContextHolder.clear(); }

    @Test @DisplayName("GET /api/v1/basedata/inspection-scheme - 返回分页列表")
    void should_return_page() throws Exception {
        BdInspectionScheme s = new BdInspectionScheme();
        s.setId(1L); s.setSchemeName("质量检查方案A"); s.setSchemeType("QUALITY"); s.setStatus(1);
        PageResult<BdInspectionScheme> page = new PageResult<>(List.of(s), 1, 1, 10, 1);
        when(schemeService.page(anyInt(), anyInt(), any(), any(), any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/basedata/inspection-scheme").param("page","1").param("size","10"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records[0].schemeName").value("质量检查方案A"));
    }

    @Test @DisplayName("GET /api/v1/basedata/inspection-scheme/{id} - 返回详情")
    void should_return_by_id() throws Exception {
        BdInspectionScheme s = new BdInspectionScheme();
        s.setId(1L); s.setSchemeName("安全检查方案");
        when(schemeService.getById(1L)).thenReturn(s);

        mockMvc.perform(get("/api/v1/basedata/inspection-scheme/1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
    }

    @Test @DisplayName("POST /api/v1/basedata/inspection-scheme - 新增")
    void should_save() throws Exception {
        BdInspectionScheme s = new BdInspectionScheme();
        s.setSchemeName("新方案"); s.setSchemeType("SAFETY");

        mockMvc.perform(post("/api/v1/basedata/inspection-scheme")
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(s)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        verify(schemeService).save(any(BdInspectionScheme.class));
    }

    @Test @DisplayName("DELETE /api/v1/basedata/inspection-scheme/{id} - 删除")
    void should_delete() throws Exception {
        mockMvc.perform(delete("/api/v1/basedata/inspection-scheme/1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        verify(schemeService).delete(1L);
    }
}
