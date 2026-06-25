package com.zwinsight.file.batch.listener;

import cn.hutool.core.util.StrUtil;
import com.zwinsight.file.batch.dto.MachineLedgerExcelDTO;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 机械台账导入监听器
 * <p>
 * 通过构造器注入校验函数和保存函数，避免直接依赖业务模块 Mapper。
 * </p>
 */
@Slf4j
public class MachineLedgerImportListener extends AbstractImportListener<MachineLedgerExcelDTO> {

    private final Function<String, Boolean> machineCodeExistsChecker;
    private final Consumer<List<MachineLedgerExcelDTO>> batchSaveAction;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * @param machineCodeExistsChecker 检查机械编号是否已存在（返回 true 表示已存在）
     * @param batchSaveAction          批量保存动作
     */
    public MachineLedgerImportListener(
            Function<String, Boolean> machineCodeExistsChecker,
            Consumer<List<MachineLedgerExcelDTO>> batchSaveAction) {
        this.machineCodeExistsChecker = machineCodeExistsChecker;
        this.batchSaveAction = batchSaveAction;
    }

    @Override
    protected String validate(MachineLedgerExcelDTO data) {
        if (StrUtil.isBlank(data.getMachineName())) {
            return "机械名称不能为空";
        }
        if (StrUtil.isBlank(data.getMachineCode())) {
            return "机械编号不能为空";
        }
        if (StrUtil.isBlank(data.getMachineType())) {
            return "机械类型不能为空";
        }
        // 校验权属字段
        if (StrUtil.isNotBlank(data.getOwnerType())) {
            String ownerType = data.getOwnerType().trim();
            if (!"自有".equals(ownerType) && !"租赁".equals(ownerType)
                    && !"OWN".equals(ownerType) && !"RENT".equals(ownerType)) {
                return "权属字段仅支持：自有/租赁";
            }
        }
        // 校验购置日期格式
        if (StrUtil.isNotBlank(data.getPurchaseDate())) {
            try {
                LocalDate.parse(data.getPurchaseDate().trim(), DATE_FORMATTER);
            } catch (DateTimeParseException e) {
                return "购置日期格式错误，应为 yyyy-MM-dd";
            }
        }
        // 唯一性校验：机械编号不能重复
        if (machineCodeExistsChecker.apply(data.getMachineCode().trim())) {
            return "机械编号 [" + data.getMachineCode() + "] 已存在";
        }
        return null;
    }

    @Override
    protected void batchSave(List<MachineLedgerExcelDTO> dataList) {
        batchSaveAction.accept(dataList);
        log.info("机械台账批量导入 {} 条", dataList.size());
    }
}
