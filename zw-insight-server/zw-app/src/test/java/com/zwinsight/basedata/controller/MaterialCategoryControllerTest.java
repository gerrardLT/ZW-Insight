package com.zwinsight.basedata.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zwinsight.common.config.SecurityContextHolder;
import com.zwinsight.basedata.domain.BdMaterialCategory;
import com.zwinsight.basedata.service.MaterialCategoryService;
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
@Import({MaterialCategoryController.class, TestSecurityConfig.class})
class MaterialCategoryControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private MaterialCategoryService categoryService;

    @BeforeEach void setUp() { SecurityContextHolder.setTenantId(1L); SecurityContextHolder.setUserId(1L); }
    @AfterEach void tearDown() { SecurityContextHolder.clear(); }

    @Test @DisplayName("GET /api/v1/basedata/material-category - 返回分类列表")
    void should_return_tree() throws Exception {
        BdMaterialCategory cat = new BdMaterialCategory();
        cat.setId(1L); cat.setCategoryName("钢材"); cat.setSortOrder(1);
        when(categoryService.listTree()).thenReturn(List.of(cat));

        mockMvc.perform(get("/api/v1/basedata/material-category"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].categoryName").value("钢材"));
    }

    @Test @DisplayName("POST /api/v1/basedata/material-category - 新增分类")
    void should_save() throws Exception {
        BdMaterialCategory cat = new BdMaterialCategory();
        cat.setCategoryName("水泥");

        mockMvc.perform(post("/api/v1/basedata/material-category")
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(cat)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        verify(categoryService).save(any(BdMaterialCategory.class));
    }

    @Test @DisplayName("DELETE /api/v1/basedata/material-category/{id} - 删除分类")
    void should_delete() throws Exception {
        mockMvc.perform(delete("/api/v1/basedata/material-category/1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        verify(categoryService).delete(1L);
    }
}
