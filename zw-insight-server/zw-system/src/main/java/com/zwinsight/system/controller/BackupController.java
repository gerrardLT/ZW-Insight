package com.zwinsight.system.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.config.SecurityContextHolder;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.result.R;
import com.zwinsight.file.service.MinioService;
import com.zwinsight.system.domain.SysBackupRecord;
import com.zwinsight.system.mapper.SysBackupRecordMapper;
import com.zwinsight.system.service.BackupService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 数据库备份管理接口。
 *
 * <p>提供手动触发备份、备份记录分页查询、备份文件下载、删除备份记录(同时删除 MinIO 文件)、
 * 以及数据库恢复能力。备份/恢复为同步执行的长耗时操作，由 {@link BackupService} 内部
 * 通过 AtomicBoolean 保证并发安全。
 */
@Slf4j
@RestController
@RequestMapping("/v1/system/backup")
@RequiredArgsConstructor
public class BackupController {

    private final BackupService backupService;
    private final SysBackupRecordMapper backupRecordMapper;
    private final MinioService minioService;

    /** 分页查询每页最大数量 */
    private static final int MAX_PAGE_SIZE = 100;

    /**
     * 手动触发数据库备份。
     *
     * <p>注意：备份为同步执行的长耗时操作（mysqldump → GZIP → MinIO 上传），
     * 请求会阻塞至备份完成或失败。
     *
     * @return 备份记录
     */
    @PostMapping("/execute")
    public R<SysBackupRecord> execute() {
        Long operatorId = SecurityContextHolder.getUserId();
        return R.ok("备份成功", backupService.executeBackup(operatorId));
    }

    /**
     * 备份记录分页列表，按创建时间倒序。
     *
     * @param page 页码，默认 1
     * @param size 每页大小，默认 20，最大 100
     * @return 分页结果
     */
    @GetMapping("/list")
    public R<PageResult<SysBackupRecord>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (size > MAX_PAGE_SIZE) {
            size = MAX_PAGE_SIZE;
        }
        Page<SysBackupRecord> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<SysBackupRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(SysBackupRecord::getCreatedAt);
        Page<SysBackupRecord> result = backupRecordMapper.selectPage(pageParam, wrapper);
        return R.ok(PageResult.of(result));
    }

    /**
     * 下载备份文件，从 MinIO 流式输出到响应。
     *
     * @param id       备份记录ID
     * @param response HTTP 响应
     */
    @GetMapping("/download/{id}")
    public void download(@PathVariable Long id, HttpServletResponse response) throws IOException {
        SysBackupRecord record = backupRecordMapper.selectById(id);
        if (record == null) {
            throw new BusinessException(404, "备份记录不存在");
        }
        if (!StringUtils.hasText(record.getStoragePath())) {
            throw new BusinessException(400, "该备份记录无可下载的存储文件");
        }

        String fileName = StringUtils.hasText(record.getFileName())
                ? record.getFileName() : ("backup_" + id + ".sql.gz");
        String encodedName = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replaceAll("\\+", "%20");

        response.setContentType("application/gzip");
        response.setHeader("Content-Disposition",
                "attachment;filename*=utf-8''" + encodedName);
        if (record.getFileSize() != null && record.getFileSize() > 0) {
            response.setContentLengthLong(record.getFileSize());
        }

        try (InputStream in = minioService.download(record.getStoragePath());
             OutputStream out = response.getOutputStream()) {
            in.transferTo(out);
            out.flush();
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            log.error("下载备份文件失败: id={}, path={}", id, record.getStoragePath(), e);
            throw new BusinessException(500, "存储文件下载失败: " + e.getMessage(), e);
        }
    }

    /**
     * 删除备份记录及其 MinIO 存储文件。
     *
     * <p>先删除 MinIO 文件，删除成功后再删除数据库记录；若 MinIO 删除失败，
     * 则保留数据库记录并返回错误（Req 11.11）。对于无存储文件的失败备份记录，
     * 直接删除数据库记录。
     *
     * @param id 备份记录ID
     */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        SysBackupRecord record = backupRecordMapper.selectById(id);
        if (record == null) {
            throw new BusinessException(404, "备份记录不存在");
        }

        // 先删除 MinIO 文件（仅当存在存储路径时）
        if (StringUtils.hasText(record.getStoragePath())) {
            try {
                minioService.delete(record.getStoragePath());
            } catch (Exception e) {
                // MinIO 删除失败 → 保留数据库记录，返回错误
                log.error("删除备份存储文件失败，保留数据库记录: id={}, path={}",
                        id, record.getStoragePath(), e);
                throw new BusinessException(500, "存储文件删除失败: " + e.getMessage(), e);
            }
        }

        // MinIO 删除成功（或无存储文件）→ 删除数据库记录
        backupRecordMapper.deleteById(id);
        log.info("删除备份记录成功: id={}", id);
        return R.ok();
    }

    /**
     * 从指定备份恢复数据库。
     *
     * <p>高风险操作：从 MinIO 下载备份 → 解压 → 执行 mysql 恢复。
     * 二次确认（@SecondaryConfirm）待 6.6 任务落地后补充。
     *
     * @param id 源备份记录ID
     */
    @PostMapping("/restore/{id}")
    public R<Void> restore(@PathVariable Long id) {
        Long operatorId = SecurityContextHolder.getUserId();
        backupService.restore(id, operatorId);
        return R.ok("恢复成功", null);
    }
}
