package com.zwinsight.contract.batch;

import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.contract.domain.BizConstructionContract;
import com.zwinsight.contract.mapper.BizConstructionContractMapper;
import com.zwinsight.contract.service.ConstructionContractService;
import com.zwinsight.file.batch.dto.ConstructionContractExcelDTO;
import com.zwinsight.file.batch.enums.ModuleCode;
import com.zwinsight.file.batch.listener.AbstractImportListener;
import com.zwinsight.project.domain.BizProject;
import com.zwinsight.project.mapper.BizProjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ConstructionContractBatchHandler 单元测试（施工合同批量导入导出处理器）
 */
@ExtendWith(MockitoExtension.class)
class ConstructionContractBatchHandlerTest {

    @Mock
    private BizConstructionContractMapper contractMapper;

    @Mock
    private ConstructionContractService contractService;

    @Mock
    private BizProjectMapper projectMapper;

    @InjectMocks
    private ConstructionContractBatchHandler handler;

    private BizProject project() {
        BizProject project = new BizProject();
        project.setId(10L);
        project.setProjectName("滨江花园一期");
        return project;
    }

    private ConstructionContractExcelDTO validRow() {
        ConstructionContractExcelDTO row = new ConstructionContractExcelDTO();
        row.setProjectName("滨江花园一期");
        row.setPartyAName("甲方单位");
        row.setContractAmount("5000000");
        row.setTaxRate("0.09");
        row.setSigningDate("2026-01-10");
        row.setStartDate("2026-02-01");
        row.setEndDate("2026-12-31");
        return row;
    }

    @Test
    @DisplayName("supports - 仅支持 CONTRACT 模块")
    void supports_onlyContract() {
        assertThat(handler.supports(ModuleCode.CONTRACT)).isTrue();
        assertThat(handler.supports(ModuleCode.PROJECT)).isFalse();
    }

    @Test
    @DisplayName("getImportDtoClass - 返回 ConstructionContractExcelDTO")
    void importDtoClass() {
        assertThat(handler.getImportDtoClass()).isEqualTo(ConstructionContractExcelDTO.class);
    }

    @Test
    @DisplayName("createImportListener - 项目不存在时行级拒绝，不触发保存")
    void importListener_projectNotFound_rejected() {
        when(projectMapper.selectOne(any())).thenReturn(null);
        @SuppressWarnings("unchecked")
        AbstractImportListener<ConstructionContractExcelDTO> listener =
                (AbstractImportListener<ConstructionContractExcelDTO>) handler.createImportListener(100L);

        listener.invoke(validRow(), null);
        listener.doAfterAllAnalysed(null);

        assertThat(listener.getImportResult().getFailedRows()).isEqualTo(1);
        assertThat(listener.getImportResult().getErrors().get(0).getErrorMessage()).contains("不存在");
        verify(contractService, never()).save(any(BizConstructionContract.class));
    }

    @Test
    @DisplayName("createImportListener - 正常行字段映射后保存（项目挂靠+合同类型 REGISTER+日期解析）")
    void importListener_validRow_savedWithMappedFields() {
        when(projectMapper.selectOne(any())).thenReturn(project());
        @SuppressWarnings("unchecked")
        AbstractImportListener<ConstructionContractExcelDTO> listener =
                (AbstractImportListener<ConstructionContractExcelDTO>) handler.createImportListener(100L);

        listener.invoke(validRow(), null);
        listener.doAfterAllAnalysed(null);

        assertThat(listener.getImportResult().isAllSuccess()).isTrue();
        ArgumentCaptor<BizConstructionContract> captor = ArgumentCaptor.forClass(BizConstructionContract.class);
        verify(contractService).save(captor.capture());
        BizConstructionContract entity = captor.getValue();
        assertThat(entity.getProjectId()).isEqualTo(10L);
        assertThat(entity.getProjectName()).isEqualTo("滨江花园一期");
        assertThat(entity.getContractType()).isEqualTo("REGISTER");
        assertThat(entity.getPartyAName()).isEqualTo("甲方单位");
        assertThat(entity.getContractAmount()).isEqualByComparingTo(new BigDecimal("5000000"));
        assertThat(entity.getTaxRate()).isEqualByComparingTo(new BigDecimal("0.09"));
        assertThat(entity.getSigningDate()).isEqualTo(LocalDate.of(2026, 1, 10));
        assertThat(entity.getStartDate()).isEqualTo(LocalDate.of(2026, 2, 1));
        assertThat(entity.getEndDate()).isEqualTo(LocalDate.of(2026, 12, 31));
    }

    @Test
    @DisplayName("createImportListener - 税率/日期留空时对应字段为 null")
    void importListener_optionalFields_nullWhenBlank() {
        when(projectMapper.selectOne(any())).thenReturn(project());
        @SuppressWarnings("unchecked")
        AbstractImportListener<ConstructionContractExcelDTO> listener =
                (AbstractImportListener<ConstructionContractExcelDTO>) handler.createImportListener(100L);
        ConstructionContractExcelDTO row = validRow();
        row.setTaxRate(null);
        row.setSigningDate(null);
        row.setStartDate(null);
        row.setEndDate(null);

        listener.invoke(row, null);
        listener.doAfterAllAnalysed(null);

        ArgumentCaptor<BizConstructionContract> captor = ArgumentCaptor.forClass(BizConstructionContract.class);
        verify(contractService).save(captor.capture());
        BizConstructionContract entity = captor.getValue();
        assertThat(entity.getTaxRate()).isNull();
        assertThat(entity.getSigningDate()).isNull();
        assertThat(entity.getStartDate()).isNull();
        assertThat(entity.getEndDate()).isNull();
    }

    @Test
    @DisplayName("createImportListener - 校验通过但保存时项目已删除，抛业务异常（批量保存失败记入结果）")
    void importListener_projectDeletedBeforeSave_batchFailed() {
        // 首次查询（validate）命中，二次查询（save）未命中
        when(projectMapper.selectOne(any())).thenReturn(project()).thenReturn(null);
        @SuppressWarnings("unchecked")
        AbstractImportListener<ConstructionContractExcelDTO> listener =
                (AbstractImportListener<ConstructionContractExcelDTO>) handler.createImportListener(100L);

        listener.invoke(validRow(), null);
        listener.doAfterAllAnalysed(null);

        assertThat(listener.getImportResult().getSuccessRows()).isZero();
        assertThat(listener.getImportResult().getErrors().get(0).getErrorMessage()).contains("批量保存失败");
        verify(contractService, never()).save(any(BizConstructionContract.class));
    }

    @Test
    @DisplayName("queryExportData - 按 projectId 过滤并转 DTO（日期/金额转字符串，null 转空串）")
    void queryExportData_mapsToDto() {
        BizConstructionContract entity = new BizConstructionContract();
        entity.setProjectName("滨江花园一期");
        entity.setPartyAName("甲方单位");
        entity.setSigningDate(LocalDate.of(2026, 1, 10));
        entity.setStartDate(LocalDate.of(2026, 2, 1));
        entity.setEndDate(LocalDate.of(2026, 12, 31));
        entity.setContractAmount(new BigDecimal("5000000"));
        entity.setTaxRate(new BigDecimal("0.09"));

        BizConstructionContract bare = new BizConstructionContract();
        bare.setProjectName("城南市政道路改造");
        when(contractMapper.selectList(any())).thenReturn(List.of(entity, bare));

        List<?> result = handler.queryExportData(Map.of("projectId", "10"));

        assertThat(result).hasSize(2);
        ConstructionContractExcelDTO dto = (ConstructionContractExcelDTO) result.get(0);
        assertThat(dto.getProjectName()).isEqualTo("滨江花园一期");
        assertThat(dto.getSigningDate()).isEqualTo("2026-01-10");
        assertThat(dto.getStartDate()).isEqualTo("2026-02-01");
        assertThat(dto.getEndDate()).isEqualTo("2026-12-31");
        assertThat(dto.getContractAmount()).isEqualTo("5000000");
        assertThat(dto.getTaxRate()).isEqualTo("0.09");
        assertThat(((ConstructionContractExcelDTO) result.get(1)).getContractAmount()).isEmpty();
    }

    @Test
    @DisplayName("queryExportData - params 为 null 时全量查询")
    void queryExportData_nullParams() {
        when(contractMapper.selectList(any())).thenReturn(Collections.emptyList());
        assertThat(handler.queryExportData(null)).isEmpty();
    }
}
