package com.zwinsight.file.batch.service;

import com.zwinsight.file.batch.domain.ExportStatus;
import com.zwinsight.file.batch.domain.ImportResult;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 批量导入导出服务接口
 */
public interface BatchImportExportService {

    /**
     * 批量导入数据
     *
     * @param moduleCode 模块编码（如 MACHINE_LEDGER）
     * @param file       Excel 文件
     * @param projectId  项目ID（可选，部分模块需要）
     * @return 导入结果
     */
    ImportResult importData(String moduleCode, MultipartFile file, Long projectId);

    /**
     * 发起异步导出
     *
     * @param moduleCode 模块编码
     * @param params     查询参数（筛选条件）
     * @return 导出任务ID
     */
    Long asyncExport(String moduleCode, Map<String, Object> params);

    /**
     * 查询导出任务状态
     *
     * @param taskId 任务ID
     * @return 导出状态
     */
    ExportStatus getExportStatus(Long taskId);

    /**
     * 下载导出文件
     *
     * @param taskId   任务ID
     * @param response HTTP 响应
     */
    void downloadExportFile(Long taskId, HttpServletResponse response);

    /**
     * 下载导入模板
     *
     * @param moduleCode 模块编码
     * @param response   HTTP 响应
     */
    void downloadTemplate(String moduleCode, HttpServletResponse response);
}
