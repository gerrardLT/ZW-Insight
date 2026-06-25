package com.zwinsight.file.batch.domain;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * 异步导出请求
 */
@Data
public class ExportRequest {

    /**
     * 模块编码
     */
    private String moduleCode;

    /**
     * 查询参数（筛选条件）
     */
    private Map<String, Object> params = new HashMap<>();
}
