package com.zwinsight.basedata.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zwinsight.common.config.SecurityContextHolder;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.basedata.domain.BdSupplier;
import com.zwinsight.basedata.service.SupplierService;
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
@Import({SupplierController.class, TestSecurityConfig.class})
class SupplierControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private SupplierService supplierService;

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
    @DisplayName("GET /api/v1/basedata/supplier - 返回供应商分页列吨")
    void should_return_page() throws Exception {
        BdSupplier supplier = new BdSupplier();
        supplier.setId(1L);
        supplier.setSupplierName("测试供应吨");
        supplier.setSupplierType("MATERIAL");
        supplier.setStatus(1);

        PageResult<BdSupplier> pageResult = new PageResult<>(List.of(supplier), 1, 1, 10, 1);
        when(supplierService.page(anyInt(), anyInt(), any(), any(), any())).thenReturn(pageResult);

        mockMvc.perform(get("/api/v1/basedata/supplier")
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records[0].supplierName").value("测试供应吨"));
    }

    @Test
    @DisplayName("GET /api/v1/basedata/supplier/{id} - 返回供应商详吨")
    void should_return_by_id() throws Exception {
        BdSupplier supplier = new BdSupplier();
        supplier.setId(1L);
        supplier.setSupplierName("测试供应吨");
        supplier.setContactName("张三");

        when(supplierService.getById(1L)).thenReturn(supplier);

        mockMvc.perform(get("/api/v1/basedata/supplier/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.supplierName").value("测试供应吨"));
    }

    @Test
    @DisplayName("POST /api/v1/basedata/supplier - 新增供应吨")
    void should_save_supplier() throws Exception {
        BdSupplier supplier = new BdSupplier();
        supplier.setSupplierName("新供应商");
        supplier.setSupplierType("LABOR");

        mockMvc.perform(post("/api/v1/basedata/supplier")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(supplier)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(supplierService).save(any(BdSupplier.class));
    }

    @Test
    @DisplayName("PUT /api/v1/basedata/supplier/{id} - 更新供应吨")
    void should_update_supplier() throws Exception {
        BdSupplier supplier = new BdSupplier();
        supplier.setSupplierName("更新供应吨");

        mockMvc.perform(put("/api/v1/basedata/supplier/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(supplier)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(supplierService).update(any(BdSupplier.class));
    }

    @Test
    @DisplayName("DELETE /api/v1/basedata/supplier/{id} - 删除供应吨")
    void should_delete_supplier() throws Exception {
        mockMvc.perform(delete("/api/v1/basedata/supplier/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(supplierService).delete(1L);
    }

    @Test
    @DisplayName("DELETE /api/v1/basedata/supplier/batch - 批量删除供应吨")
    void should_batch_delete() throws Exception {
        mockMvc.perform(delete("/api/v1/basedata/supplier/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[1,2,3]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(supplierService).batchDelete(anyList());
    }
}
