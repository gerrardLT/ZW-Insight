package com.zwinsight.file.batch.domain;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 批量导入结果
 */
@Data
public class ImportResult {

    /**
     * 总行数（不含表头）
     */
    private int totalRows;

    /**
     * 成功导入行数
     */
    private int successRows;

    /**
     * 失败行数
     */
    private int failedRows;

    /**
     * 错误明细列表
     */
    private List<ImportError> errors = new ArrayList<>();

    /**
     * 是否全部成功
     */
    public boolean isAllSuccess() {
        return failedRows == 0;
    }

    /**
     * 添加错误记录
     */
    public void addError(int rowNumber, String errorMessage) {
        this.errors.add(new ImportError(rowNumber, errorMessage));
        this.failedRows++;
    }

    /**
     * 增加成功计数
     */
    public void incrementSuccess(int count) {
        this.successRows += count;
    }
}
