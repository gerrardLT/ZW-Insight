package com.zwinsight.labor.batch;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.file.batch.dto.LaborContractExcelDTO;
import com.zwinsight.file.batch.enums.ModuleCode;
import com.zwinsight.file.batch.listener.AbstractImportListener;
import com.zwinsight.file.batch.listener.LaborContractImportListener;
import com.zwinsight.file.batch.service.BatchModuleHandler;
import com.zwinsight.labor.domain.BizLaborContract;
import com.zwinsight.labor.mapper.BizLaborContractMapper;
import com.zwinsight.labor.service.LaborContractService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * 劳务合同批量导入导出处理器
 * <p>
 * 保存走 {@link LaborContractService#save}（累计字段初始化 + DRAFT 状态 + 黑名单/预算控制注解）。
 * projectId 由页面导入时作为上下文传入。
 * </p>
 */
@Component
@RequiredArgsConstructor
public class LaborContractBatchHandler implements BatchModuleHandler {

    private final BizLaborContractMapper laborContractMapper;
    private final LaborContractService laborContractService;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Override
    public boolean supports(ModuleCode moduleCode) {
        return ModuleCode.LABOR_CONTRACT == moduleCode;
    }

    @Override
    public Class<?> getImportDtoClass() {
        return LaborContractExcelDTO.class;
    }

    @Override
    public AbstractImportListener<?> createImportListener(Long projectId) {
        return new LaborContractImportListener(
                // 合同编号查重（仅填写了编号的行生效）
                contractCode -> {
                    Long count = laborContractMapper.selectCount(
                            new LambdaQueryWrapper<BizLaborContract>()
                                    .eq(BizLaborContract::getContractCode, contractCode)
                    );
                    return count != null && count > 0;
                },
                // 批量保存
                dataList -> {
                    for (LaborContractExcelDTO dto : dataList) {
                        BizLaborContract entity = new BizLaborContract();
                        entity.setProjectId(projectId);
                        entity.setContractCode(StrUtil.trimToNull(dto.getContractCode()));
                        entity.setContractName(dto.getContractName().trim());
                        entity.setTeamName(StrUtil.trimToNull(dto.getTeamName()));
                        entity.setPartyAName(StrUtil.trimToNull(dto.getPartyAName()));
                        entity.setPartyBName(StrUtil.trimToNull(dto.getPartyBName()));
                        entity.setContractAmount(new BigDecimal(dto.getContractAmount().trim()));
                        entity.setPaymentTerms(StrUtil.trimToNull(dto.getPaymentTerms()));
                        if (StrUtil.isNotBlank(dto.getSigningDate())) {
                            entity.setSigningDate(LocalDate.parse(dto.getSigningDate().trim(), DATE_FORMATTER));
                        }
                        if (StrUtil.isNotBlank(dto.getStartDate())) {
                            entity.setStartDate(LocalDate.parse(dto.getStartDate().trim(), DATE_FORMATTER));
                        }
                        if (StrUtil.isNotBlank(dto.getEndDate())) {
                            entity.setEndDate(LocalDate.parse(dto.getEndDate().trim(), DATE_FORMATTER));
                        }
                        laborContractService.save(entity);
                    }
                }
        );
    }

    @Override
    public List<?> queryExportData(Map<String, Object> params) {
        LambdaQueryWrapper<BizLaborContract> wrapper = new LambdaQueryWrapper<>();
        if (params != null && params.get("projectId") != null) {
            wrapper.eq(BizLaborContract::getProjectId, Long.valueOf(params.get("projectId").toString()));
        }
        wrapper.orderByDesc(BizLaborContract::getCreatedAt);
        List<BizLaborContract> list = laborContractMapper.selectList(wrapper);
        return list.stream().map(entity -> {
            LaborContractExcelDTO dto = new LaborContractExcelDTO();
            dto.setContractCode(entity.getContractCode());
            dto.setContractName(entity.getContractName());
            dto.setTeamName(entity.getTeamName());
            dto.setPartyAName(entity.getPartyAName());
            dto.setPartyBName(entity.getPartyBName());
            dto.setSigningDate(entity.getSigningDate() != null ? entity.getSigningDate().toString() : "");
            dto.setStartDate(entity.getStartDate() != null ? entity.getStartDate().toString() : "");
            dto.setEndDate(entity.getEndDate() != null ? entity.getEndDate().toString() : "");
            dto.setContractAmount(entity.getContractAmount() != null ? entity.getContractAmount().toPlainString() : "");
            dto.setPaymentTerms(entity.getPaymentTerms());
            return dto;
        }).toList();
    }
}
