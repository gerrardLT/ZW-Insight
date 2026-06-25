package com.zwinsight.contract.dto;

import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.read.listener.ReadListener;
import com.zwinsight.common.exception.BusinessException;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * BOQ Excel 读取监听器
 * <p>
 * 行校验规则：
 * - itemCode 和 itemName 必填
 * - 条目上限 5000 条
 * - 错误收集最多 100 条
 */
@Slf4j
public class BoqReadListener implements ReadListener<BoqExcelRow> {

    private static final int MAX_ITEMS = 5000;
    private static final int MAX_ERRORS = 100;

    /** 成功解析的行数据 */
    @Getter
    private final List<BoqExcelRow> dataList = new ArrayList<>();

    /** 错误信息列表 */
    @Getter
    private final List<String> errors = new ArrayList<>();

    @Override
    public void invoke(BoqExcelRow row, AnalysisContext context) {
        int rowIndex = context.readRowHolder().getRowIndex() + 1; // 转为1-based行号

        // 条目上限检查
        if (dataList.size() >= MAX_ITEMS) {
            throw new BusinessException("清单条目超过上限" + MAX_ITEMS + "条，请精简后重新上传");
        }

        // 行校验: itemCode 和 itemName 必填
        boolean hasError = false;
        if (row.getItemCode() == null || row.getItemCode().trim().isEmpty()) {
            if (errors.size() < MAX_ERRORS) {
                errors.add("第" + rowIndex + "行：项目编码不能为空");
            }
            hasError = true;
        }
        if (row.getItemName() == null || row.getItemName().trim().isEmpty()) {
            if (errors.size() < MAX_ERRORS) {
                errors.add("第" + rowIndex + "行：项目名称不能为空");
            }
            hasError = true;
        }

        if (!hasError) {
            // 去除首尾空格
            row.setItemCode(row.getItemCode().trim());
            row.setItemName(row.getItemName().trim());
            if (row.getUnit() != null) {
                row.setUnit(row.getUnit().trim());
            }
            dataList.add(row);
        }
    }

    @Override
    public void doAfterAllAnalysed(AnalysisContext context) {
        log.info("BOQ Excel解析完成，成功解析{}条，错误{}条", dataList.size(), errors.size());
    }

    /**
     * 是否存在解析错误
     */
    public boolean hasErrors() {
        return !errors.isEmpty();
    }
}
