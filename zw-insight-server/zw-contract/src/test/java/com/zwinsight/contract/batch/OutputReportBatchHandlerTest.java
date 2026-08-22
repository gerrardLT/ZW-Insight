package com.zwinsight.contract.batch;

import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.contract.domain.BizOutputReport;
import com.zwinsight.contract.mapper.BizOutputReportMapper;
import com.zwinsight.file.batch.dto.OutputReportExcelDTO;
import com.zwinsight.file.batch.enums.ModuleCode;
import com.zwinsight.project.domain.BizProject;
import com.zwinsight.project.mapper.BizProjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * OutputReportBatchHandler 单元测试（产值上报导出处理器）
 */
@ExtendWith(MockitoExtension.class)
class OutputReportBatchHandlerTest {

    @Mock
    private BizOutputReportMapper outputReportMapper;

    @Mock
    private BizProjectMapper projectMapper;

    @InjectMocks
    private OutputReportBatchHandler handler;

    @Test
    @DisplayName("supports - 仅支持 OUTPUT_REPORT 模块")
    void supports_onlyOutputReport() {
        assertThat(handler.supports(ModuleCode.OUTPUT_REPORT)).isTrue();
        ModuleCode other = Arrays.stream(ModuleCode.values())
                .filter(c -> c != ModuleCode.OUTPUT_REPORT)
                .findFirst()
                .orElseThrow();
        assertThat(handler.supports(other)).isFalse();
    }

    @Test
    @DisplayName("createImportListener - 产值上报不支持导入，抛业务异常")
    void createImportListener_throws() {
        assertThatThrownBy(() -> handler.createImportListener(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不支持批量导入");
    }

    @Test
    @DisplayName("queryExportData - 实体转 DTO（回填项目名+状态中文标签）")
    void queryExportData_mapsToDto() {
        BizOutputReport entity = new BizOutputReport();
        entity.setProjectId(10L);
        entity.setReportPeriod("2026-04");
        entity.setCurrentOutput(new BigDecimal("100.50"));
        entity.setCumulativeOutput(new BigDecimal("320.00"));
        entity.setConfirmDate(LocalDate.of(2026, 4, 30));
        entity.setStatus("APPROVED");
        when(outputReportMapper.selectList(any())).thenReturn(Collections.singletonList(entity));

        BizProject project = new BizProject();
        project.setId(10L);
        project.setProjectName("测试项目");
        when(projectMapper.selectList(null)).thenReturn(Collections.singletonList(project));

        List<?> result = handler.queryExportData(Collections.emptyMap());

        assertThat(result).hasSize(1);
        OutputReportExcelDTO dto = (OutputReportExcelDTO) result.get(0);
        assertThat(dto.getProjectName()).isEqualTo("测试项目");
        assertThat(dto.getReportPeriod()).isEqualTo("2026-04");
        assertThat(dto.getCurrentOutput()).isEqualTo("100.50");
        assertThat(dto.getCumulativeOutput()).isEqualTo("320.00");
        assertThat(dto.getConfirmDate()).isEqualTo("2026-04-30");
        assertThat(dto.getStatus()).isEqualTo("已审批");
    }

    @Test
    @DisplayName("queryExportData - 项目缺失时项目名为空串，状态 DRAFT 映射为草稿")
    void queryExportData_missingProjectAndDraftStatus() {
        BizOutputReport entity = new BizOutputReport();
        entity.setProjectId(99L);
        entity.setStatus("DRAFT");
        when(outputReportMapper.selectList(any())).thenReturn(Collections.singletonList(entity));
        when(projectMapper.selectList(null)).thenReturn(Collections.emptyList());

        List<?> result = handler.queryExportData(Collections.emptyMap());

        OutputReportExcelDTO dto = (OutputReportExcelDTO) result.get(0);
        assertThat(dto.getProjectName()).isEmpty();
        assertThat(dto.getStatus()).isEqualTo("草稿");
        assertThat(dto.getCurrentOutput()).isEmpty();
        assertThat(dto.getConfirmDate()).isEmpty();
    }
}
