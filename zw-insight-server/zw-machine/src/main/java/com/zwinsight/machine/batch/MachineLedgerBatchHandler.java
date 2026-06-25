package com.zwinsight.machine.batch;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.file.batch.dto.MachineLedgerExcelDTO;
import com.zwinsight.file.batch.enums.ModuleCode;
import com.zwinsight.file.batch.listener.AbstractImportListener;
import com.zwinsight.file.batch.listener.MachineLedgerImportListener;
import com.zwinsight.file.batch.service.BatchModuleHandler;
import com.zwinsight.machine.domain.BizMachineLedger;
import com.zwinsight.machine.mapper.BizMachineLedgerMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * 机械台账批量导入导出处理器
 */
@Component
@RequiredArgsConstructor
public class MachineLedgerBatchHandler implements BatchModuleHandler {

    private final BizMachineLedgerMapper machineLedgerMapper;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Override
    public boolean supports(ModuleCode moduleCode) {
        return ModuleCode.MACHINE_LEDGER == moduleCode;
    }

    @Override
    public Class<?> getImportDtoClass() {
        return MachineLedgerExcelDTO.class;
    }

    @Override
    public AbstractImportListener<?> createImportListener(Long projectId) {
        return new MachineLedgerImportListener(
                // 唯一性校验：检查机械编号是否存在
                machineCode -> {
                    Long count = machineLedgerMapper.selectCount(
                            new LambdaQueryWrapper<BizMachineLedger>()
                                    .eq(BizMachineLedger::getMachineCode, machineCode)
                    );
                    return count != null && count > 0;
                },
                // 批量保存
                dataList -> {
                    for (MachineLedgerExcelDTO dto : dataList) {
                        BizMachineLedger entity = new BizMachineLedger();
                        entity.setMachineName(dto.getMachineName().trim());
                        entity.setMachineCode(dto.getMachineCode().trim());
                        entity.setMachineType(dto.getMachineType().trim());
                        entity.setBrand(StrUtil.trimToNull(dto.getBrand()));
                        entity.setSpecification(StrUtil.trimToNull(dto.getSpecification()));
                        entity.setCurrentProject(StrUtil.trimToNull(dto.getCurrentProject()));
                        entity.setStatus("REGISTERED");

                        // 权属映射
                        if (StrUtil.isNotBlank(dto.getOwnerType())) {
                            String ot = dto.getOwnerType().trim();
                            entity.setOwnerType("自有".equals(ot) ? "OWN" : "租赁".equals(ot) ? "RENT" : ot);
                        }
                        // 购置日期
                        if (StrUtil.isNotBlank(dto.getPurchaseDate())) {
                            entity.setPurchaseDate(LocalDate.parse(dto.getPurchaseDate().trim(), DATE_FORMATTER));
                        }

                        machineLedgerMapper.insert(entity);
                    }
                }
        );
    }

    @Override
    public List<?> queryExportData(Map<String, Object> params) {
        List<BizMachineLedger> list = machineLedgerMapper.selectList(null);
        return list.stream().map(entity -> {
            MachineLedgerExcelDTO dto = new MachineLedgerExcelDTO();
            dto.setMachineName(entity.getMachineName());
            dto.setMachineCode(entity.getMachineCode());
            dto.setMachineType(entity.getMachineType());
            dto.setBrand(entity.getBrand());
            dto.setSpecification(entity.getSpecification());
            dto.setOwnerType(mapOwnerType(entity.getOwnerType()));
            dto.setCurrentProject(entity.getCurrentProject());
            dto.setPurchaseDate(entity.getPurchaseDate() != null ? entity.getPurchaseDate().toString() : "");
            return dto;
        }).toList();
    }

    private String mapOwnerType(String ownerType) {
        if ("OWN".equals(ownerType)) return "自有";
        if ("RENT".equals(ownerType)) return "租赁";
        return ownerType;
    }
}
