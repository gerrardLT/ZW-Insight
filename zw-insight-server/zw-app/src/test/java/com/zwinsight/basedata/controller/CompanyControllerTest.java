package com.zwinsight.basedata.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zwinsight.common.config.SecurityContextHolder;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.basedata.domain.BdCompany;
import com.zwinsight.basedata.service.CompanyService;
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
@Import({CompanyController.class, TestSecurityConfig.class})
class CompanyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CompanyService companyService;

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
    @DisplayName("GET /api/v1/basedata/company - 返回公司分页列表")
    void should_return_page() throws Exception {
        BdCompany company = new BdCompany();
        company.setId(1L);
        company.setCompanyName("中维建设");
        company.setStatus(1);

        PageResult<BdCompany> pageResult = new PageResult<>(List.of(company), 1, 1, 10, 1);
        when(companyService.page(anyInt(), anyInt(), any(), any())).thenReturn(pageResult);

        mockMvc.perform(get("/api/v1/basedata/company")
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records[0].companyName").value("中维建设"));
    }

    @Test
    @DisplayName("GET /api/v1/basedata/company/{id} - 返回公司详情")
    void should_return_by_id() throws Exception {
        BdCompany company = new BdCompany();
        company.setId(1L);
        company.setCompanyName("中维建设");
        company.setLegalPerson("张三");

        when(companyService.getById(1L)).thenReturn(company);

        mockMvc.perform(get("/api/v1/basedata/company/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.companyName").value("中维建设"));
    }

    @Test
    @DisplayName("POST /api/v1/basedata/company - 新增公司")
    void should_save_company() throws Exception {
        BdCompany company = new BdCompany();
        company.setCompanyName("新公司");

        mockMvc.perform(post("/api/v1/basedata/company")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(company)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(companyService).save(any(BdCompany.class));
    }

    @Test
    @DisplayName("DELETE /api/v1/basedata/company/{id} - 删除公司")
    void should_delete_company() throws Exception {
        mockMvc.perform(delete("/api/v1/basedata/company/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(companyService).delete(1L);
    }
}
