package com.zwinsight.budget.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zwinsight.common.config.SecurityContextHolder;
import com.zwinsight.budget.domain.BizCostSubcategory;
import com.zwinsight.budget.service.CostSubcategoryService;
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
@Import({CostSubcategoryController.class, TestSecurityConfig.class})
class CostSubcategoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CostSubcategoryService costSubcategoryService;

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
    @DisplayName("GET /api/v1/budget/subcategory/{costCategory} - 返回费用子类列表")
    void should_return_list() throws Exception {
        BizCostSubcategory sub = new BizCostSubcategory();
        sub.setId(1L);
        sub.setSubcategoryName("钢筋");
        sub.setSortOrder(1);

        when(costSubcategoryService.list("MATERIAL")).thenReturn(List.of(sub));

        mockMvc.perform(get("/api/v1/budget/subcategory/MATERIAL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].subcategoryName").value("钢筋"));
    }

    @Test
    @DisplayName("POST /api/v1/budget/subcategory - 新增费用子类")
    void should_save_subcategory() throws Exception {
        BizCostSubcategory sub = new BizCostSubcategory();
        sub.setCostCategory("MATERIAL");
        sub.setSubcategoryName("水泥");

        mockMvc.perform(post("/api/v1/budget/subcategory")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sub)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(costSubcategoryService).save(any(BizCostSubcategory.class));
    }

    @Test
    @DisplayName("DELETE /api/v1/budget/subcategory/{id} - 删除费用子类")
    void should_delete_subcategory() throws Exception {
        mockMvc.perform(delete("/api/v1/budget/subcategory/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(costSubcategoryService).delete(1L);
    }
}
