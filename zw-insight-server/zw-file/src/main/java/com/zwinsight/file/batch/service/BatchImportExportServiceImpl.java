package com.zwinsight.file.batch.service;

import cn.hutool.core.util.IdUtil;
import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.write.metadata.WriteSheet;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.file.batch.domain.ExportStatus;
import com.zwinsight.file.batch.domain.ImportResult;
import com.zwinsight.file.batch.enums.ModuleCode;
import com.zwinsight.file.batch.listener.AbstractImportListener;
import com.zwinsight.file.service.MinioService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 批量导入导出服务实现
 * <p>
 * 通过策略模式注入各模块的 {@link BatchModuleHandler}，
 * 各业务模块自行实现 Handler 并注册为 Spring Bean，
 * 避免 zw-file 直接依赖业务模块。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BatchImportExportServiceImpl implements BatchImportExportService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final MinioService minioService;
    private final List<BatchModuleHandler> moduleHandlers;

    /**
     * Redis key 前缀
     */
    private static final String EXPORT_TASK_KEY_PREFIX = "export:task:";

    /**
     * 导出任务 Redis 过期时间（小时）
     */
    private static final long EXPORT_TASK_EXPIRE_HOURS = 24;

    @Override
    public ImportResult importData(String moduleCode, MultipartFile file, Long projectId) {
        ModuleCode module = ModuleCode.fromCode(moduleCode);

        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择要导入的文件");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || (!originalFilename.endsWith(".xlsx") && !originalFilename.endsWith(".xls"))) {
            throw new BusinessException("仅支持 .xlsx 或 .xls 格式的 Excel 文件");
        }

        BatchModuleHandler handler = getHandler(module);

        try {
            AbstractImportListener<?> listener = handler.createImportListener(projectId);
            Class<?> dtoClass = handler.getImportDtoClass();

            EasyExcel.read(file.getInputStream(), dtoClass, listener)
                    .sheet()
                    .doRead();

            return listener.getImportResult();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("导入失败: moduleCode={}", moduleCode, e);
            throw new BusinessException("导入失败: " + e.getMessage());
        }
    }

    @Override
    public Long asyncExport(String moduleCode, Map<String, Object> params) {
        ModuleCode.fromCode(moduleCode); // 验证模块编码

        // 生成任务ID（雪花ID）
        Long taskId = IdUtil.getSnowflakeNextId();

        // 初始化 Redis 状态
        ExportStatus status = ExportStatus.pending();
        String redisKey = EXPORT_TASK_KEY_PREFIX + taskId;
        redisTemplate.opsForValue().set(redisKey, status, EXPORT_TASK_EXPIRE_HOURS, TimeUnit.HOURS);

        // 异步执行导出
        executeExportAsync(taskId, moduleCode, params);

        return taskId;
    }

    @Override
    public ExportStatus getExportStatus(Long taskId) {
        String redisKey = EXPORT_TASK_KEY_PREFIX + taskId;
        Object obj = redisTemplate.opsForValue().get(redisKey);
        if (obj == null) {
            throw new BusinessException("导出任务不存在或已过期");
        }
        return (ExportStatus) obj;
    }

    @Override
    public void downloadExportFile(Long taskId, HttpServletResponse response) {
        ExportStatus status = getExportStatus(taskId);
        if (!ExportStatus.STATUS_COMPLETED.equals(status.getStatus())) {
            throw new BusinessException("导出任务尚未完成，当前状态: " + status.getStatus());
        }

        try {
            InputStream inputStream = minioService.download(status.getObjectName());
            String encodedFileName = URLEncoder.encode(status.getFileName(), StandardCharsets.UTF_8)
                    .replaceAll("\\+", "%20");

            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment;filename*=utf-8''" + encodedFileName);

            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                response.getOutputStream().write(buffer, 0, bytesRead);
            }
            response.getOutputStream().flush();
            inputStream.close();
        } catch (Exception e) {
            log.error("下载导出文件失败: taskId={}", taskId, e);
            throw new BusinessException("下载导出文件失败: " + e.getMessage());
        }
    }

    @Override
    public void downloadTemplate(String moduleCode, HttpServletResponse response) {
        ModuleCode module = ModuleCode.fromCode(moduleCode);
        BatchModuleHandler handler = getHandler(module);

        try {
            String fileName = module.getTemplateFileName();
            String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8)
                    .replaceAll("\\+", "%20");

            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment;filename*=utf-8''" + encodedFileName);

            // 生成空模板（仅包含表头）
            EasyExcel.write(response.getOutputStream(), handler.getImportDtoClass())
                    .sheet(module.getName())
                    .doWrite(java.util.Collections.emptyList());
        } catch (Exception e) {
            log.error("下载模板失败: moduleCode={}", moduleCode, e);
            throw new BusinessException("下载模板失败: " + e.getMessage());
        }
    }

    // ===================== 异步导出核心逻辑 =====================

    /**
     * 异步执行导出任务
     */
    @Async("batchExportExecutor")
    public void executeExportAsync(Long taskId, String moduleCode, Map<String, Object> params) {
        String redisKey = EXPORT_TASK_KEY_PREFIX + taskId;
        ModuleCode module = ModuleCode.fromCode(moduleCode);

        try {
            // 更新状态为处理中
            ExportStatus status = ExportStatus.pending();
            status.processing(10);
            redisTemplate.opsForValue().set(redisKey, status, EXPORT_TASK_EXPIRE_HOURS, TimeUnit.HOURS);

            BatchModuleHandler handler = getHandler(module);

            // 执行导出写入内存
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            try (ExcelWriter excelWriter = EasyExcel.write(outputStream).build()) {
                WriteSheet writeSheet = EasyExcel.writerSheet(0, module.getName())
                        .head(handler.getImportDtoClass())
                        .build();
                List<?> exportData = handler.queryExportData(params);
                excelWriter.write(exportData, writeSheet);
            }

            // 更新进度
            status.processing(70);
            redisTemplate.opsForValue().set(redisKey, status, EXPORT_TASK_EXPIRE_HOURS, TimeUnit.HOURS);

            // 上传至 MinIO
            String objectName = "exports/" + moduleCode.toLowerCase() + "/" + taskId + ".xlsx";
            byte[] bytes = outputStream.toByteArray();
            ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes);

            minioService.upload(objectName, inputStream, bytes.length,
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

            // 更新为完成状态
            String fileName = module.getName() + "_导出.xlsx";
            status.completed(objectName, fileName);
            redisTemplate.opsForValue().set(redisKey, status, EXPORT_TASK_EXPIRE_HOURS, TimeUnit.HOURS);

            log.info("异步导出完成: taskId={}, moduleCode={}", taskId, moduleCode);
        } catch (Exception e) {
            log.error("异步导出失败: taskId={}, moduleCode={}", taskId, moduleCode, e);
            ExportStatus failedStatus = ExportStatus.pending();
            failedStatus.failed("导出失败: " + e.getMessage());
            redisTemplate.opsForValue().set(redisKey, failedStatus, EXPORT_TASK_EXPIRE_HOURS, TimeUnit.HOURS);
        }
    }

    // ===================== 私有辅助方法 =====================

    /**
     * 获取模块对应的处理器
     */
    private BatchModuleHandler getHandler(ModuleCode module) {
        return moduleHandlers.stream()
                .filter(h -> h.supports(module))
                .findFirst()
                .orElseThrow(() -> new BusinessException("模块 [" + module.getName() + "] 尚未实现导入导出处理器"));
    }
}
