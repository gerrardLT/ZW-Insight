package com.zwinsight.archive.controller;

import com.zwinsight.archive.service.ArchiveService;
import com.zwinsight.common.config.SecurityContextHolder;
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

import java.util.Map;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest
@Import({ArchiveController.class, TestSecurityConfig.class})
class ArchiveControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private ArchiveService archiveService;

    @BeforeEach void setUp() { SecurityContextHolder.setTenantId(1L); SecurityContextHolder.setUserId(1L); }
    @AfterEach void tearDown() { SecurityContextHolder.clear(); }

    @Test @DisplayName("GET /api/v1/archive/project/{projectId} - 项目档案")
    void should_return_project_archive() throws Exception {
        when(archiveService.getProjectArchive(100L)).thenReturn(Map.of("projectName", "测试项目"));
        mockMvc.perform(get("/api/v1/archive/project/100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.projectName").value("测试项目"));
    }

    @Test @DisplayName("GET /api/v1/archive/budget/{projectId} - 预算档案")
    void should_return_budget_archive() throws Exception {
        when(archiveService.getBudgetArchive(100L)).thenReturn(Map.of("totalAmount", 5000));
        mockMvc.perform(get("/api/v1/archive/budget/100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test @DisplayName("GET /api/v1/archive/contract/{contractId} - 合同档案")
    void should_return_contract_archive() throws Exception {
        when(archiveService.getContractArchive(1L)).thenReturn(Map.of("contractCode", "HT-001"));
        mockMvc.perform(get("/api/v1/archive/contract/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test @DisplayName("GET /api/v1/archive/supplier/{supplierId} - 供应商档案")
    void should_return_supplier_archive() throws Exception {
        when(archiveService.getSupplierArchive(1L)).thenReturn(Map.of("supplierName", "供应商A"));
        mockMvc.perform(get("/api/v1/archive/supplier/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test @DisplayName("GET /api/v1/archive/tender/{registerId} - 投标档案")
    void should_return_tender_archive() throws Exception {
        when(archiveService.getTenderArchive(1L)).thenReturn(Map.of("projectName", "投标项目"));
        mockMvc.perform(get("/api/v1/archive/tender/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }
}
