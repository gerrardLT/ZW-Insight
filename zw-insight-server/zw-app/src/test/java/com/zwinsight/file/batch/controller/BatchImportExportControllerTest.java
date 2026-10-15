package com.zwinsight.file.batch.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zwinsight.common.config.SecurityContextHolder;
import com.zwinsight.file.batch.domain.ExportRequest;
import com.zwinsight.file.batch.domain.ExportStatus;
import com.zwinsight.file.batch.domain.ImportResult;
import com.zwinsight.file.batch.service.BatchImportExportService;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest
@Import({BatchImportExportController.class, TestSecurityConfig.class})
class BatchImportExportControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private BatchImportExportService batchImportExportService;

    @BeforeEach void setUp() { SecurityContextHolder.setTenantId(1L); SecurityContextHolder.setUserId(1L); }
    @AfterEach void tearDown() { SecurityContextHolder.clear(); }

    @Test @DisplayName("POST /api/v1/batch/import - 批量导入")
    void should_import_data() throws Exception {
        ImportResult result = new ImportResult();
        result.setTotalRows(10); result.setSuccessRows(10); result.setFailedRows(0);
        // Controller 走四参 default 方法（extraParams 透传），须按四参匹配打桩
        when(batchImportExportService.importData(eq("MACHINE_LEDGER"), any(), any(), anyMap())).thenReturn(result);

        MockMultipartFile file = new MockMultipartFile("file", "test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "dummy".getBytes());

        mockMvc.perform(multipart("/api/v1/batch/import")
                        .file(file)
                        .param("moduleCode", "MACHINE_LEDGER")
                        .param("projectId", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        verify(batchImportExportService).importData(eq("MACHINE_LEDGER"), any(), eq(100L), eq(java.util.Map.of()));
    }

    @Test
    void should_confirm_with_preview_receipt() throws Exception {
        ImportResult result = new ImportResult();
        when(batchImportExportService.importData(eq("MACHINE_LEDGER"), any(), any(), anyMap())).thenReturn(result);
        mockMvc.perform(multipart("/api/v1/batch/import/confirm")
                .file(new MockMultipartFile("file", "test.xlsx", null, new byte[]{1}))
                .param("moduleCode", "MACHINE_LEDGER").param("projectId", "100")
                .param("previewToken", "receipt"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        verify(batchImportExportService).importData(eq("MACHINE_LEDGER"), any(), eq(100L),
                eq(java.util.Map.of("previewToken", "receipt")));
    }

    @Test
    void confirmation_without_receipt_is_rejected() {
        BatchImportExportController controller = new BatchImportExportController(batchImportExportService);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> controller.confirmImport("MACHINE_LEDGER",
                new MockMultipartFile("file", new byte[]{1}), 100L, java.util.Map.of()))
                .isInstanceOf(com.zwinsight.common.exception.BusinessException.class)
                .hasMessageContaining("请先预览");
        verifyNoInteractions(batchImportExportService);
    }

    @Test
    void confirmation_invalid_receipt_is_not_reported_as_success() {
        when(batchImportExportService.importData(anyString(), any(), any(), anyMap()))
                .thenThrow(new com.zwinsight.common.exception.BusinessException(409, "预览已过期"));
        BatchImportExportController controller = new BatchImportExportController(batchImportExportService);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> controller.confirmImport("MACHINE_LEDGER",
                new MockMultipartFile("file", new byte[]{1}), 100L, java.util.Map.of("previewToken", "expired")))
                .hasMessageContaining("预览已过期");
    }

    @Test @DisplayName("POST /api/v1/batch/export - 发起异步导出")
    void should_async_export() throws Exception {
        when(batchImportExportService.asyncExport(eq("MACHINE_LEDGER"), any())).thenReturn(42L);

        ExportRequest request = new ExportRequest();
        request.setModuleCode("MACHINE_LEDGER");

        mockMvc.perform(post("/api/v1/batch/export")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(42));
    }

    @Test @DisplayName("GET /api/v1/batch/export/{taskId}/status - 查询导出状吨")
    void should_return_export_status() throws Exception {
        ExportStatus status = ExportStatus.pending();
        when(batchImportExportService.getExportStatus(42L)).thenReturn(status);

        mockMvc.perform(get("/api/v1/batch/export/42/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }
}
