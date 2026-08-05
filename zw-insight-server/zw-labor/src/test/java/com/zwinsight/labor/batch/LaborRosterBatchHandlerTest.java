package com.zwinsight.labor.batch;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.file.batch.dto.LaborRosterExcelDTO;
import com.zwinsight.file.batch.enums.ModuleCode;
import com.zwinsight.file.batch.listener.AbstractImportListener;
import com.zwinsight.file.batch.listener.LaborRosterImportListener;
import com.zwinsight.labor.domain.BizLaborRoster;
import com.zwinsight.labor.mapper.BizLaborRosterMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * LaborRosterBatchHandler 单元测试（批量导入导出处理器）
 */
@ExtendWith(MockitoExtension.class)
class LaborRosterBatchHandlerTest {

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

    @Test
    @DisplayName("queryExportData - 实体转 DTO（姓名/身份证/电话/工种）")
    void queryExportData_mapsToDto() {
        BizLaborRoster entity = new BizLaborRoster();
        entity.setWorkerName("工人甲");
        entity.setIdCard("110101199001011234");
        entity.setPhone("13800138000");
        entity.setWorkerType("木工");
        when(laborRosterMapper.selectList(null)).thenReturn(Collections.singletonList(entity));

        List<?> result = handler.queryExportData(Collections.emptyMap());

        assertThat(result).hasSize(1);
        LaborRosterExcelDTO dto = (LaborRosterExcelDTO) result.get(0);
        assertThat(dto.getWorkerName()).isEqualTo("工人甲");
        assertThat(dto.getIdCard()).isEqualTo("110101199001011234");
        assertThat(dto.getPhone()).isEqualTo("13800138000");
        assertThat(dto.getWorkerType()).isEqualTo("木工");
    }
}
