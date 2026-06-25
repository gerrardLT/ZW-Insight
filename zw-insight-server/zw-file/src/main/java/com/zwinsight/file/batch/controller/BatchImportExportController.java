package com.zwinsight.file.batch.controller;

import com.zwinsight.common.result.R;
import com.zwinsight.file.batch.domain.ExportRequest;
import com.zwinsight.file.batch.domain.ExportStatus;
import com.zwinsight.file.batch.domain.ImportResult;
import com.zwinsight.file.batch.service.BatchImportExportService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 批量导入导出 REST API
 */
@RestController
@RequestMapping("/api/v1/batch")
@RequiredArgsConstructor
public class BatchImportExportController {

    private final BatchImportExportService batchImportExportService;

    /**
     * 批量导入
     * <p>
     * POST /api/v1/batch/import?moduleCode=MACHINE_LEDGER&projectId=123
     *
     * @param moduleCode 模块编码
     * @param file       Excel 文件
     * @param projectId  项目ID（可选）
     * @return 导入结果
     */
    @PostMapping("/import")
    public R<ImportResult> importData(
            @RequestParam("moduleCode") String moduleCode,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "projectId", required = false) Long projectId) {
        ImportResult result = batchImportExportService.importData(moduleCode, file, projectId);
        if (result.isAllSuccess()) {
            return R.ok("导入成功，共导入 " + result.getSuccessRows() + " 条数据", result);
        } else {
            return R.ok("部分导入成功：成功 " + result.getSuccessRows() + " 条，失败 " + result.getFailedRows() + " 条", result);
        }
    }

    /**
     * 发起异步导出
     * <p>
     * POST /api/v1/batch/export
     * Body: {"moduleCode": "MACHINE_LEDGER", "params": {"projectId": 123}}
     *
     * @param request 导出请求
     * @return 任务ID
     */
    @PostMapping("/export")
    public R<Long> asyncExport(@RequestBody ExportRequest request) {
        Long taskId = batchImportExportService.asyncExport(request.getModuleCode(), request.getParams());
        return R.ok("导出任务已提交", taskId);
    }

    /**
     * 查询导出任务状态
     * <p>
     * GET /api/v1/batch/export/{taskId}/status
     *
     * @param taskId 任务ID
     * @return 导出状态
     */
    @GetMapping("/export/{taskId}/status")
    public R<ExportStatus> getExportStatus(@PathVariable Long taskId) {
        ExportStatus status = batchImportExportService.getExportStatus(taskId);
        return R.ok(status);
    }

    /**
     * 下载导出文件
     * <p>
     * GET /api/v1/batch/export/{taskId}/download
     *
     * @param taskId   任务ID
     * @param response HTTP 响应
     */
    @GetMapping("/export/{taskId}/download")
    public void downloadExportFile(@PathVariable Long taskId, HttpServletResponse response) {
        batchImportExportService.downloadExportFile(taskId, response);
    }

    /**
     * 下载导入模板
     * <p>
     * GET /api/v1/batch/template/{moduleCode}
     *
     * @param moduleCode 模块编码
     * @param response   HTTP 响应
     */
    @GetMapping("/template/{moduleCode}")
    public void downloadTemplate(@PathVariable String moduleCode, HttpServletResponse response) {
        batchImportExportService.downloadTemplate(moduleCode, response);
    }
}
