package com.zwinsight.file.batch.listener;

import cn.hutool.core.util.StrUtil;
import com.zwinsight.file.batch.dto.LaborRosterExcelDTO;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * 劳务花名册导入监听器
 * <p>
 * 通过构造器注入校验函数和保存函数，避免直接依赖业务模块 Mapper。
 * </p>
 */
@Slf4j
public class LaborRosterImportListener extends AbstractImportListener<LaborRosterExcelDTO> {

    private final Function<String, Boolean> idCardExistsChecker;
    private final Consumer<List<LaborRosterExcelDTO>> batchSaveAction;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final Pattern ID_CARD_PATTERN = Pattern.compile("^\\d{15}(\\d{2}[0-9Xx])?$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");

    /**
     * @param idCardExistsChecker 检查身份证号是否已存在
     * @param batchSaveAction     批量保存动作
     */
    public LaborRosterImportListener(
            Function<String, Boolean> idCardExistsChecker,
            Consumer<List<LaborRosterExcelDTO>> batchSaveAction) {
        this.idCardExistsChecker = idCardExistsChecker;
        this.batchSaveAction = batchSaveAction;
    }

    @Override
    protected String validate(LaborRosterExcelDTO data) {
        if (StrUtil.isBlank(data.getWorkerName())) {
            return "工人姓名不能为空";
        }
        if (StrUtil.isBlank(data.getIdCard())) {
            return "身份证号不能为空";
        }
        // 身份证格式校验
        if (!ID_CARD_PATTERN.matcher(data.getIdCard().trim()).matches()) {
            return "身份证号格式错误";
        }
        // 手机号格式校验（非必填但填了需要格式正确）
        if (StrUtil.isNotBlank(data.getPhone()) && !PHONE_PATTERN.matcher(data.getPhone().trim()).matches()) {
            return "联系电话格式错误";
        }
        // 入场日期格式校验
        if (StrUtil.isNotBlank(data.getEntryDate())) {
            try {
                LocalDate.parse(data.getEntryDate().trim(), DATE_FORMATTER);
            } catch (DateTimeParseException e) {
                return "入场日期格式错误，应为 yyyy-MM-dd";
            }
        }
        // 唯一性校验
        if (idCardExistsChecker.apply(data.getIdCard().trim())) {
            return "身份证号 [" + data.getIdCard() + "] 已存在";
        }
        return null;
    }

    @Override
    protected void batchSave(List<LaborRosterExcelDTO> dataList) {
        batchSaveAction.accept(dataList);
        log.info("劳务花名册批量导入 {} 条", dataList.size());
    }
}
