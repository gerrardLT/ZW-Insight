package com.zwinsight.file.batch.listener;

import cn.hutool.core.util.StrUtil;
import com.zwinsight.file.batch.dto.ConstructionContractExcelDTO;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 施工合同导入监听器
 * <p>
 * 通过构造器注入校验函数和保存函数，避免直接依赖业务模块 Mapper。
 * 合同编号/税额拆分由业务模块保存逻辑自动生成计算。
 * </p>
 */
@Slf4j
public class ConstructionContractImportListener extends AbstractImportListener<ConstructionContractExcelDTO> {

    private final Function<String, Boolean> projectNameExistsChecker;
    private final Consumer<List<ConstructionContractExcelDTO>> batchSaveAction;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * @param projectNameExistsChecker 检查项目名称是否存在（合同必须挂靠已有项目）
     * @param batchSaveAction          批量保存动作
     */
    public ConstructionContractImportListener(
            Function<String, Boolean> projectNameExistsChecker,
            Consumer<List<ConstructionContractExcelDTO>> batchSaveAction) {
        this.projectNameExistsChecker = projectNameExistsChecker;
        this.batchSaveAction = batchSaveAction;
    }

    @Override
    protected String validate(ConstructionContractExcelDTO data) {
        if (StrUtil.isBlank(data.getProjectName())) {
            return "项目名称不能为空";
        }
        if (!projectNameExistsChecker.apply(data.getProjectName().trim())) {
            return "项目 [" + data.getProjectName() + "] 不存在";
        }
        if (StrUtil.isBlank(data.getContractAmount())) {
            return "合同金额不能为空";
        }
        try {
            new BigDecimal(data.getContractAmount().trim());
        } catch (NumberFormatException e) {
            return "合同金额格式错误";
        }
        if (StrUtil.isNotBlank(data.getTaxRate())) {
            try {
                new BigDecimal(data.getTaxRate().trim());
            } catch (NumberFormatException e) {
                return "税率格式错误";
            }
        }
        String dateError = validateDate(data.getSigningDate(), "签订日期");
        if (dateError != null) return dateError;
        dateError = validateDate(data.getStartDate(), "开工日期");
        if (dateError != null) return dateError;
        dateError = validateDate(data.getEndDate(), "竣工日期");
        if (dateError != null) return dateError;
        if (StrUtil.isNotBlank(data.getStartDate()) && StrUtil.isNotBlank(data.getEndDate())
                && LocalDate.parse(data.getEndDate().trim(), DATE_FORMATTER)
                        .isBefore(LocalDate.parse(data.getStartDate().trim(), DATE_FORMATTER))) {
            return "竣工日期不能早于开工日期";
        }
        return null;
    }

    private String validateDate(String value, String fieldLabel) {
        if (StrUtil.isBlank(value)) {
            return null;
        }
        try {
            LocalDate.parse(value.trim(), DATE_FORMATTER);
            return null;
        } catch (DateTimeParseException e) {
            return fieldLabel + "格式错误，应为 yyyy-MM-dd";
        }
    }

    @Override
    protected void batchSave(List<ConstructionContractExcelDTO> dataList) {
        batchSaveAction.accept(dataList);
        log.info("施工合同批量导入 {} 条", dataList.size());
    }
}
