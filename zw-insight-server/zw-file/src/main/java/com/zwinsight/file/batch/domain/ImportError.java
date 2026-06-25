package com.zwinsight.file.batch.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 导入错误行信息
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ImportError {

    /**
     * 行号（Excel 中的行号，从1开始，含表头）
     */
    private int rowNumber;

    /**
     * 错误原因
     */
    private String errorMessage;
}
