package com.zwinsight.file.batch.controller;

import com.zwinsight.common.result.R;
import com.zwinsight.common.security.RequiresPermission;
import com.zwinsight.file.batch.domain.ExportRequest;
import com.zwinsight.file.batch.domain.ExportStatus;
import com.zwinsight.file.batch.domain.ImportResult;
import com.zwinsight.file.batch.service.BatchImportExportService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 批量导入导出 REST API
 */
@RestController
@RequestMapping("/api/v1/batch")
@RequiresPermission("system:view")
@RequiredArgsConstructor
public class BatchImportExportController {

    private final BatchImportExportService batchImportExportService;

    /**
     * 批量导入
     * <p>
     * POST /api/v1/batch/import?moduleCode=MACHINE_LEDGER&projectId=123
     * <p>
     * 支持额外业务参数透传（如 teamId），除 moduleCode/file/projectId 外的
     * 表单字段均作为 extraParams 传递给模块处理器。
     *
     * @param moduleCode 模块编码
     * @param file       Excel 文件
     * @param projectId  项目ID（可选）
     * @param allParams  全部表单参数（含额外业务参数）
     * @return 导入结果
     */
    @PostMapping("/import/preview")
    public R<Map<String, Object>> previewImport(@RequestParam String moduleCode,
            @RequestParam MultipartFile file, @RequestParam(required = false) Long projectId,
            @RequestParam Map<String, Object> allParams) {
        Map<String, Object> params = new java.util.HashMap<>(allParams);
        params.keySet().removeAll(java.util.Set.of("moduleCode", "file", "projectId"));
        return R.ok(batchImportExportService.previewImport(moduleCode, file, projectId, params));
    }

    @PostMapping("/import/confirm")
    public R<ImportResult> confirmImport(@RequestParam String moduleCode,
            @RequestParam MultipartFile file, @RequestParam(required = false) Long projectId,
            @RequestParam Map<String, Object> allParams) {
        Object token = allParams.get("previewToken");
        if (token == null || token.toString().isBlank()) {
            throw new com.zwinsight.common.exception.BusinessException(400, "请先预览并人工确认导入文件");
        }
        return importData(moduleCode, file, projectId, allParams);
    }

    @PostMapping("/import")
    public R<ImportResult> importData(
            @RequestParam("moduleCode") String moduleCode,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "projectId", required = false) Long projectId,
            @RequestParam Map<String, Object> allParams) {
        Map<String, Object> extraParams = new java.util.HashMap<>(allParams);
        extraParams.remove("moduleCode");
        extraParams.remove("file");
        extraParams.remove("projectId");
        // Legacy /import consumers remain compatible. Any supplied receipt is still verified by the service.
        ImportResult result = batchImportExportService.importData(moduleCode, file, projectId, extraParams);
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
