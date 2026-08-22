package com.zwinsight.project.batch;

import com.zwinsight.file.batch.dto.ProjectExcelDTO;
import com.zwinsight.file.batch.enums.ModuleCode;
import com.zwinsight.file.batch.listener.AbstractImportListener;
import com.zwinsight.project.domain.BizProject;
import com.zwinsight.project.mapper.BizProjectMapper;
import com.zwinsight.project.service.ProjectService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ProjectBatchHandler 单元测试（项目报备批量导入导出处理器）
 */
@ExtendWith(MockitoExtension.class)
class ProjectBatchHandlerTest {

    @Mock
    private BizProjectMapper projectMapper;

    @Mock
    private ProjectService projectService;

    @InjectMocks
    private ProjectBatchHandler handler;

    private ProjectExcelDTO validRow() {
        ProjectExcelDTO row = new ProjectExcelDTO();
        row.setProjectName("  滨江花园二期  ");
        row.setProjectNature("房建");
        row.setProjectType("住宅");
        row.setOwnerCompanyName("业主单位");
        row.setProjectAddress("杭州市滨江区");
        row.setContactName("张三");
        row.setContactPhone("13800000001");
        row.setBudgetAmount("1000000.00");
        return row;
    }

    @Test
    @DisplayName("supports - 仅支持 PROJECT 模块")
    void supports_onlyProject() {
        assertThat(handler.supports(ModuleCode.PROJECT)).isTrue();
        assertThat(handler.supports(ModuleCode.CONTRACT)).isFalse();
    }

    @Test
    @DisplayName("getImportDtoClass - 返回 ProjectExcelDTO")
    void importDtoClass() {
        assertThat(handler.getImportDtoClass()).isEqualTo(ProjectExcelDTO.class);
    }

    @Test
    @DisplayName("createImportListener - 项目名已存在时行级拒绝，不触发保存")
    void importListener_duplicatedName_rejected() {
        when(projectMapper.selectCount(any())).thenReturn(1L);
        @SuppressWarnings("unchecked")
        AbstractImportListener<ProjectExcelDTO> listener =
                (AbstractImportListener<ProjectExcelDTO>) handler.createImportListener(100L);

        listener.invoke(validRow(), null);
        listener.doAfterAllAnalysed(null);

        assertThat(listener.getImportResult().getFailedRows()).isEqualTo(1);
        assertThat(listener.getImportResult().getErrors().get(0).getErrorMessage()).contains("已存在");
        verify(projectService, never()).save(any(BizProject.class));
    }

    @Test
    @DisplayName("createImportListener - 正常行字段映射后逐条保存（名称去空格+金额转换）")
    void importListener_validRow_savedWithMappedFields() {
        when(projectMapper.selectCount(any())).thenReturn(0L);
        @SuppressWarnings("unchecked")
        AbstractImportListener<ProjectExcelDTO> listener =
                (AbstractImportListener<ProjectExcelDTO>) handler.createImportListener(100L);

        listener.invoke(validRow(), null);
        listener.doAfterAllAnalysed(null);

        assertThat(listener.getImportResult().isAllSuccess()).isTrue();
        ArgumentCaptor<BizProject> captor = ArgumentCaptor.forClass(BizProject.class);
        verify(projectService).save(captor.capture());
        BizProject entity = captor.getValue();
        assertThat(entity.getProjectName()).isEqualTo("滨江花园二期");
        assertThat(entity.getProjectNature()).isEqualTo("房建");
        assertThat(entity.getOwnerCompanyName()).isEqualTo("业主单位");
        assertThat(entity.getBudgetAmount()).isEqualByComparingTo(new BigDecimal("1000000.00"));
    }

    @Test
    @DisplayName("createImportListener - 预算金额留空时实体金额为 null")
    void importListener_blankBudgetAmount_nullAmount() {
        when(projectMapper.selectCount(any())).thenReturn(0L);
        @SuppressWarnings("unchecked")
        AbstractImportListener<ProjectExcelDTO> listener =
                (AbstractImportListener<ProjectExcelDTO>) handler.createImportListener(100L);
        ProjectExcelDTO row = validRow();
        row.setBudgetAmount(null);

        listener.invoke(row, null);
        listener.doAfterAllAnalysed(null);

        ArgumentCaptor<BizProject> captor = ArgumentCaptor.forClass(BizProject.class);
        verify(projectService).save(captor.capture());
        assertThat(captor.getValue().getBudgetAmount()).isNull();
    }

    @Test
    @DisplayName("queryExportData - 实体转 DTO（金额转字符串，null 金额转空串）")
    void queryExportData_mapsToDto() {
        BizProject entity = new BizProject();
        entity.setProjectName("滨江花园一期");
        entity.setProjectNature("房建");
        entity.setProjectType("住宅");
        entity.setOwnerCompanyName("业主单位");
        entity.setProjectAddress("杭州市滨江区");
        entity.setContactName("张三");
        entity.setContactPhone("13800000001");
        entity.setBudgetAmount(new BigDecimal("888888.00"));

        BizProject noAmount = new BizProject();
        noAmount.setProjectName("城南市政道路改造");
        when(projectMapper.selectList(null)).thenReturn(List.of(entity, noAmount));

        List<?> result = handler.queryExportData(Collections.emptyMap());

        assertThat(result).hasSize(2);
        ProjectExcelDTO dto = (ProjectExcelDTO) result.get(0);
        assertThat(dto.getProjectName()).isEqualTo("滨江花园一期");
        assertThat(dto.getContactPhone()).isEqualTo("13800000001");
        assertThat(dto.getBudgetAmount()).isEqualTo("888888.00");
        assertThat(((ProjectExcelDTO) result.get(1)).getBudgetAmount()).isEmpty();
    }
}
