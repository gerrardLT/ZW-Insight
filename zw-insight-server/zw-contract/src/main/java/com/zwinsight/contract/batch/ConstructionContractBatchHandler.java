package com.zwinsight.contract.batch;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.contract.domain.BizConstructionContract;
import com.zwinsight.contract.mapper.BizConstructionContractMapper;
import com.zwinsight.contract.service.ConstructionContractService;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.file.batch.dto.ConstructionContractExcelDTO;
import com.zwinsight.file.batch.enums.ModuleCode;
import com.zwinsight.file.batch.listener.AbstractImportListener;
import com.zwinsight.file.batch.listener.ConstructionContractImportListener;
import com.zwinsight.file.batch.service.BatchModuleHandler;
import com.zwinsight.project.domain.BizProject;
import com.zwinsight.project.mapper.BizProjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * 施工合同批量导入导出处理器
 */
@Component
@RequiredArgsConstructor
public class ConstructionContractBatchHandler implements BatchModuleHandler {

    private final BizConstructionContractMapper contractMapper;
    private final ConstructionContractService contractService;
    private final BizProjectMapper projectMapper;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Override
    public boolean supports(ModuleCode moduleCode) {
        return ModuleCode.CONTRACT == moduleCode;
    }

    @Override
    public Class<?> getImportDtoClass() {
        return ConstructionContractExcelDTO.class;
    }

    @Override
    public AbstractImportListener<?> createImportListener(Long projectId) {
        return new ConstructionContractImportListener(
                // 项目存在性校验：合同必须挂靠已有项目
                projectName -> findProject(projectName) != null,
                // 批量保存（走 ConstructionContractService.save：自动编号/税额拆分/累计字段初始化）
                dataList -> {
                    for (ConstructionContractExcelDTO dto : dataList) {
                        BizProject project = findProject(dto.getProjectName().trim());
                        if (project == null) {
                            throw new BusinessException("项目 [" + dto.getProjectName() + "] 不存在");
                        }
                        BizConstructionContract entity = new BizConstructionContract();
                        entity.setProjectId(project.getId());
                        entity.setProjectName(project.getProjectName());
                        entity.setContractType("REGISTER");
                        entity.setPartyAName(StrUtil.trimToNull(dto.getPartyAName()));
                        entity.setContractAmount(new BigDecimal(dto.getContractAmount().trim()));
                        if (StrUtil.isNotBlank(dto.getTaxRate())) {
                            entity.setTaxRate(new BigDecimal(dto.getTaxRate().trim()));
                        }
                        if (StrUtil.isNotBlank(dto.getSigningDate())) {
                            entity.setSigningDate(LocalDate.parse(dto.getSigningDate().trim(), DATE_FORMATTER));
                        }
                        if (StrUtil.isNotBlank(dto.getStartDate())) {
                            entity.setStartDate(LocalDate.parse(dto.getStartDate().trim(), DATE_FORMATTER));
                        }
                        if (StrUtil.isNotBlank(dto.getEndDate())) {
                            entity.setEndDate(LocalDate.parse(dto.getEndDate().trim(), DATE_FORMATTER));
                        }
                        contractService.save(entity);
                    }
                }
        );
    }

    @Override
    public List<?> queryExportData(Map<String, Object> params) {
        LambdaQueryWrapper<BizConstructionContract> wrapper = new LambdaQueryWrapper<>();
        if (params != null && params.get("projectId") != null) {
            wrapper.eq(BizConstructionContract::getProjectId, Long.valueOf(params.get("projectId").toString()));
        }
        List<BizConstructionContract> list = contractMapper.selectList(wrapper);
        return list.stream().map(entity -> {
            ConstructionContractExcelDTO dto = new ConstructionContractExcelDTO();
            dto.setProjectName(entity.getProjectName());
            dto.setPartyAName(entity.getPartyAName());
            dto.setSigningDate(entity.getSigningDate() != null ? entity.getSigningDate().toString() : "");
            dto.setStartDate(entity.getStartDate() != null ? entity.getStartDate().toString() : "");
            dto.setEndDate(entity.getEndDate() != null ? entity.getEndDate().toString() : "");
            dto.setContractAmount(entity.getContractAmount() != null ? entity.getContractAmount().toPlainString() : "");
            dto.setTaxRate(entity.getTaxRate() != null ? entity.getTaxRate().toPlainString() : "");
            return dto;
        }).toList();
    }

    private BizProject findProject(String projectName) {
        return projectMapper.selectOne(
                new LambdaQueryWrapper<BizProject>()
                        .eq(BizProject::getProjectName, projectName)
                        .last("LIMIT 1")
        );
    }
}
