package com.zwinsight.basedata.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zwinsight.common.config.SecurityContextHolder;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.basedata.domain.BdMaterial;
import com.zwinsight.basedata.domain.BdMaterialCategory;
import com.zwinsight.basedata.service.MaterialCategoryService;
import com.zwinsight.basedata.service.MaterialService;
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
@Import({MaterialController.class, TestSecurityConfig.class})
class MaterialControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private MaterialService materialService;

    @MockBean
    private MaterialCategoryService categoryService;

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
    @DisplayName("GET /api/v1/basedata/material - 返回材料分页列表")
    void should_return_page() throws Exception {
        BdMaterial material = new BdMaterial();
        material.setId(1L);
        material.setMaterialName("HRB400钢筋");
        material.setUnit("吨");

        PageResult<BdMaterial> pageResult = new PageResult<>(List.of(material), 1, 1, 10, 1);
        when(materialService.page(anyInt(), anyInt(), any(), any())).thenReturn(pageResult);

        mockMvc.perform(get("/api/v1/basedata/material")
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records[0].materialName").value("HRB400钢筋"));
    }

    @Test
    @DisplayName("GET /api/v1/basedata/material/{id} - 返回材料详情")
    void should_return_by_id() throws Exception {
        BdMaterial material = new BdMaterial();
        material.setId(1L);
        material.setMaterialName("HRB400钢筋");

        when(materialService.getById(1L)).thenReturn(material);

        mockMvc.perform(get("/api/v1/basedata/material/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.materialName").value("HRB400钢筋"));
    }

    @Test
    @DisplayName("POST /api/v1/basedata/material - 新增材料")
    void should_save_material() throws Exception {
        BdMaterial material = new BdMaterial();
        material.setMaterialName("P.O42.5水泥");
        material.setUnit("吨");

        mockMvc.perform(post("/api/v1/basedata/material")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(material)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(materialService).save(any(BdMaterial.class));
    }

    @Test
    @DisplayName("DELETE /api/v1/basedata/material/{id} - 删除材料")
    void should_delete_material() throws Exception {
        mockMvc.perform(delete("/api/v1/basedata/material/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(materialService).delete(1L);
    }

    @Test
    @DisplayName("GET /api/v1/basedata/material/categories - 返回材料分类吨")
    void should_return_categories() throws Exception {
        BdMaterialCategory category = new BdMaterialCategory();
        category.setId(1L);
        category.setCategoryName("钢材");

        when(categoryService.listTree()).thenReturn(List.of(category));

        mockMvc.perform(get("/api/v1/basedata/material/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].categoryName").value("钢材"));
    }
}
