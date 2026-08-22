package com.zwinsight.file.batch.listener;

import cn.hutool.core.util.StrUtil;
import com.zwinsight.file.batch.dto.LaborContractExcelDTO;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 劳务合同导入监听器
 * <p>
 * 通过构造器注入校验函数和保存函数，避免直接依赖业务模块 Mapper。
 * </p>
 */
@Slf4j
public class LaborContractImportListener extends AbstractImportListener<LaborContractExcelDTO> {

    private final Function<String, Boolean> contractCodeExistsChecker;
    private final Consumer<List<LaborContractExcelDTO>> batchSaveAction;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * @param contractCodeExistsChecker 检查合同编号是否已存在（仅对填写了编号的行生效）
     * @param batchSaveAction           批量保存动作
     */
    public LaborContractImportListener(
            Function<String, Boolean> contractCodeExistsChecker,
            Consumer<List<LaborContractExcelDTO>> batchSaveAction) {
        this.contractCodeExistsChecker = contractCodeExistsChecker;
        this.batchSaveAction = batchSaveAction;
    }

    @Override
    protected String validate(LaborContractExcelDTO data) {
        if (StrUtil.isBlank(data.getContractName())) {
            return "合同名称不能为空";
        }
        if (StrUtil.isNotBlank(data.getContractCode())
                && contractCodeExistsChecker.apply(data.getContractCode().trim())) {
            return "合同编号 [" + data.getContractCode() + "] 已存在";
        }
        if (StrUtil.isBlank(data.getContractAmount())) {
            return "合同金额不能为空";
        }
        try {
            new BigDecimal(data.getContractAmount().trim());
        } catch (NumberFormatException e) {
            return "合同金额格式错误";
        }
        String dateError = validateDate(data.getSigningDate(), "签订日期");
        if (dateError != null) return dateError;
        dateError = validateDate(data.getStartDate(), "开始日期");
        if (dateError != null) return dateError;
        dateError = validateDate(data.getEndDate(), "结束日期");
        if (dateError != null) return dateError;
        if (StrUtil.isNotBlank(data.getStartDate()) && StrUtil.isNotBlank(data.getEndDate())
                && LocalDate.parse(data.getEndDate().trim(), DATE_FORMATTER)
                        .isBefore(LocalDate.parse(data.getStartDate().trim(), DATE_FORMATTER))) {
            return "结束日期不能早于开始日期";
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
    protected void batchSave(List<LaborContractExcelDTO> dataList) {
        batchSaveAction.accept(dataList);
        log.info("劳务合同批量导入 {} 条", dataList.size());
    }
}
