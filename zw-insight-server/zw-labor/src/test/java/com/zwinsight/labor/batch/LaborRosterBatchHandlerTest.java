package com.zwinsight.labor.batch;

import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.file.batch.domain.ImportResult;
import com.zwinsight.file.batch.dto.LaborRosterExcelDTO;
import com.zwinsight.file.batch.enums.ModuleCode;
import com.zwinsight.file.batch.listener.AbstractImportListener;
import com.zwinsight.file.batch.listener.LaborRosterImportListener;
import com.zwinsight.labor.domain.BizLaborRoster;
import com.zwinsight.labor.mapper.BizLaborRosterMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * LaborRosterBatchHandler 单元测试（批量导入导出处理器）
 */
@ExtendWith(MockitoExtension.class)
class LaborRosterBatchHandlerTest {

    @TempDir
    Path tempDir;

    @Mock
    private BizLaborRosterMapper laborRosterMapper;

    @InjectMocks
    private LaborRosterBatchHandler handler;

    @Test
    @DisplayName("supports - 仅支持 LABOR_ROSTER 模块")
    void supports_onlyLaborRoster() {
        assertThat(handler.supports(ModuleCode.LABOR_ROSTER)).isTrue();
        // 取任意其他枚举值验证不支持
        ModuleCode other = Arrays.stream(ModuleCode.values())
                .filter(c -> c != ModuleCode.LABOR_ROSTER)
                .findFirst()
                .orElseThrow();
        assertThat(handler.supports(other)).isFalse();
    }

    @Test
    @DisplayName("getImportDtoClass - 返回花名册 Excel DTO 类型")
    void getImportDtoClass_returnsDto() {
        assertThat(handler.getImportDtoClass()).isEqualTo(LaborRosterExcelDTO.class);
    }

    @Test
    @DisplayName("createImportListener - 返回花名册导入监听器")
    void createImportListener_returnsLaborListener() {
        AbstractImportListener<?> listener = handler.createImportListener(1L);

        assertThat(listener).isInstanceOf(LaborRosterImportListener.class);
    }

    /** 用 EasyExcel 真实写 xlsx 后走监听器解析链路（不 mock 解析器） */
    @SuppressWarnings("unchecked")
    private ImportResult runImport(Long projectId, Map<String, Object> extraParams,
                                   List<LaborRosterExcelDTO> rows) throws Exception {
        Path file = tempDir.resolve("roster-" + System.nanoTime() + ".xlsx");
        EasyExcel.write(file.toFile(), LaborRosterExcelDTO.class).sheet().doWrite(rows);
        byte[] bytes = Files.readAllBytes(file);
        Files.delete(file);

        AbstractImportListener<LaborRosterExcelDTO> listener =
                (AbstractImportListener<LaborRosterExcelDTO>) handler.createImportListener(projectId, extraParams);
        EasyExcel.read(new ByteArrayInputStream(bytes), LaborRosterExcelDTO.class, listener)
                .sheet().doRead();
        return listener.getImportResult();
    }

    private LaborRosterExcelDTO validRow() {
        LaborRosterExcelDTO row = new LaborRosterExcelDTO();
        row.setWorkerName(" 工人甲 ");
        row.setIdCard("110101199001011234");
        row.setPhone("13800138000");
        row.setWorkerType("木工");
        return row;
    }

    @Test
    @DisplayName("真实 xlsx 导入 - 唯一性校验通过，teamId 解析回填并批量保存")
    void importHappyPath_withTeamId() throws Exception {
        when(laborRosterMapper.selectCount(any())).thenReturn(0L);

        ImportResult result = runImport(1L, Map.of("teamId", "10"), List.of(validRow()));

        assertThat(result.isAllSuccess()).isTrue();
        assertThat(result.getSuccessRows()).isEqualTo(1);

        ArgumentCaptor<BizLaborRoster> captor = ArgumentCaptor.forClass(BizLaborRoster.class);
        verify(laborRosterMapper).insert(captor.capture());
        BizLaborRoster saved = captor.getValue();
        assertThat(saved.getWorkerName()).isEqualTo("工人甲"); // 首尾空格被 trim
        assertThat(saved.getIdCard()).isEqualTo("110101199001011234");
        assertThat(saved.getProjectId()).isEqualTo(1L);
        assertThat(saved.getTeamId()).isEqualTo(10L);
        assertThat(saved.getStatus()).isEqualTo(1); // 导入默认在岗
    }

    @Test
    @DisplayName("身份证已存在 - 行级拒绝且不保存")
    void importDuplicateIdCard_rejected() throws Exception {
        when(laborRosterMapper.selectCount(any())).thenReturn(1L);

        ImportResult result = runImport(1L, Map.of(), List.of(validRow()));

        assertThat(result.getFailedRows()).isEqualTo(1);
        assertThat(result.getErrors().get(0).getErrorMessage()).contains("已存在");
        verify(laborRosterMapper, never()).insert(any());
    }

    @Test
    @DisplayName("teamId 参数格式非法 - 拒绝静默落库错误值")
    void parseTeamId_invalid_throws() {
        assertThatThrownBy(() -> handler.createImportListener(1L, Map.of("teamId", "abc")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("teamId 参数格式非法");
    }

    @Test
    @DisplayName("queryExportData - 携带 projectId/teamId 筛选参数")
    void queryExportData_withFilterParams() {
        when(laborRosterMapper.selectList(any())).thenReturn(Collections.emptyList());

        List<?> result = handler.queryExportData(Map.of("projectId", "1", "teamId", "10"));

        assertThat(result).isEmpty();
        verify(laborRosterMapper).selectList(any(LambdaQueryWrapper.class));
    }

    @Test
    @DisplayName("queryExportData - 实体转 DTO（姓名/身份证/电话/工种）")
    void queryExportData_mapsToDto() {
        BizLaborRoster entity = new BizLaborRoster();
        entity.setWorkerName("工人甲");
        entity.setIdCard("110101199001011234");
        entity.setPhone("13800138000");
        entity.setWorkerType("木工");
        when(laborRosterMapper.selectList(any())).thenReturn(Collections.singletonList(entity));

        List<?> result = handler.queryExportData(Collections.emptyMap());

        assertThat(result).hasSize(1);
        LaborRosterExcelDTO dto = (LaborRosterExcelDTO) result.get(0);
        assertThat(dto.getWorkerName()).isEqualTo("工人甲");
        assertThat(dto.getIdCard()).isEqualTo("110101199001011234");
        assertThat(dto.getPhone()).isEqualTo("13800138000");
        assertThat(dto.getWorkerType()).isEqualTo("木工");
    }
}
