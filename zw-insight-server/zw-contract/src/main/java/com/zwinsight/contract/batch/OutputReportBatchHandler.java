package com.zwinsight.contract.batch;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.contract.domain.BizOutputReport;
import com.zwinsight.contract.mapper.BizOutputReportMapper;
import com.zwinsight.file.batch.dto.OutputReportExcelDTO;
import com.zwinsight.file.batch.enums.ModuleCode;
import com.zwinsight.file.batch.listener.AbstractImportListener;
import com.zwinsight.file.batch.service.BatchModuleHandler;
import com.zwinsight.project.domain.BizProject;
import com.zwinsight.project.mapper.BizProjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 产值上报导出处理器（仅导出，不支持导入）
 */
@Component
@RequiredArgsConstructor
public class OutputReportBatchHandler implements BatchModuleHandler {

    private final BizOutputReportMapper outputReportMapper;
    private final BizProjectMapper projectMapper;

    @Override
    public boolean supports(ModuleCode moduleCode) {
        return ModuleCode.OUTPUT_REPORT == moduleCode;
    }

    @Override
    public Class<?> getImportDtoClass() {
        return OutputReportExcelDTO.class;
    }

    @Override
    public AbstractImportListener<?> createImportListener(Long projectId) {
        throw new BusinessException("产值上报模块不支持批量导入");
    }

    @Override
    public List<?> queryExportData(Map<String, Object> params) {
        LambdaQueryWrapper<BizOutputReport> wrapper = new LambdaQueryWrapper<>();
        if (params != null && params.get("projectId") != null) {
            wrapper.eq(BizOutputReport::getProjectId, Long.valueOf(params.get("projectId").toString()));
        }
        wrapper.orderByDesc(BizOutputReport::getCreatedAt);
        List<BizOutputReport> list = outputReportMapper.selectList(wrapper);

        Map<Long, String> projectNames = projectMapper.selectList(null).stream()
                .collect(Collectors.toMap(BizProject::getId, BizProject::getProjectName, (a, b) -> a));
        return list.stream().map(entity -> {
            OutputReportExcelDTO dto = new OutputReportExcelDTO();
            dto.setProjectName(projectNames.getOrDefault(entity.getProjectId(), ""));
            dto.setReportPeriod(entity.getReportPeriod());
            dto.setCurrentOutput(entity.getCurrentOutput() != null ? entity.getCurrentOutput().toPlainString() : "");
            dto.setCumulativeOutput(entity.getCumulativeOutput() != null ? entity.getCumulativeOutput().toPlainString() : "");
            dto.setConfirmDate(entity.getConfirmDate() != null ? entity.getConfirmDate().toString() : "");
            dto.setStatus(statusLabel(entity.getStatus()));
            return dto;
        }).toList();
    }

    private String statusLabel(String status) {
        if (status == null) {
            return "";
        }
        return switch (status) {
            case "DRAFT" -> "草稿";
            case "SUBMITTED" -> "审批中";
            case "APPROVED" -> "已审批";
            case "REJECTED" -> "已驳回";
            default -> status;
        };
    }
}
