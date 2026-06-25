package com.zwinsight.file.batch.service;

import com.zwinsight.file.batch.enums.ModuleCode;
import com.zwinsight.file.batch.listener.AbstractImportListener;

import java.util.List;
import java.util.Map;

/**
 * 批量导入导出模块处理器接口
 * <p>
 * 各业务模块实现此接口并注册为 Spring Bean，
 * BatchImportExportServiceImpl 通过策略模式调用。
 * </p>
 */
public interface BatchModuleHandler {

    /**
     * 当前处理器是否支持指定模块
     *
     * @param moduleCode 模块编码
     * @return true 表示支持
     */
    boolean supports(ModuleCode moduleCode);

    /**
     * 获取导入 DTO 类（用于 EasyExcel 解析和模板生成）
     *
     * @return DTO Class
     */
    Class<?> getImportDtoClass();

    /**
     * 创建导入监听器实例
     *
     * @param projectId 项目ID（可选）
     * @return 导入监听器
     */
    AbstractImportListener<?> createImportListener(Long projectId);

    /**
     * 查询导出数据
     *
     * @param params 查询参数
     * @return 导出数据列表（DTO 列表）
     */
    List<?> queryExportData(Map<String, Object> params);
}
