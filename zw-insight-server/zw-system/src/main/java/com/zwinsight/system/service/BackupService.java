package com.zwinsight.system.service;

import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.file.service.MinioService;
import com.zwinsight.system.domain.SysBackupRecord;
import com.zwinsight.system.domain.SysBackupRestoreLog;
import com.zwinsight.system.mapper.SysBackupRecordMapper;
import com.zwinsight.system.mapper.SysBackupRestoreLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * 数据库备份服务
 *
 * <p>执行链路: ProcessBuilder 执行 mysqldump → GZIP 压缩 → MinIO 上传。
 * 恢复链路: MinIO 下载 → 解压 → mysql 命令恢复 → 记录恢复日志。
 * 使用 {@link AtomicBoolean} 防止并发备份，支持超时控制与失败时临时文件清理。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BackupService {

    private final SysBackupRecordMapper backupRecordMapper;
    private final SysBackupRestoreLogMapper restoreLogMapper;
    private final MinioService minioService;

    /** mysqldump 可执行文件路径 */
    @Value("${backup.mysqldump-path:/usr/bin/mysqldump}")
    private String mysqldumpPath;

    /** mysql 可执行文件路径 */
    @Value("${backup.mysql-path:/usr/bin/mysql}")
    private String mysqlPath;

    /** 备份/恢复进程超时时间(秒) */
    @Value("${backup.timeout-seconds:3600}")
    private int timeoutSeconds;

    /** 数据库连接信息 */
    @Value("${spring.datasource.url}")
    private String datasourceUrl;

    @Value("${spring.datasource.username}")
    private String datasourceUsername;

    @Value("${spring.datasource.password}")
    private String datasourcePassword;

    /** MinIO 备份对象前缀 */
    private static final String BACKUP_OBJECT_PREFIX = "backup/db/";

    /** 系统定时任务操作人ID */
    private static final long SYSTEM_OPERATOR_ID = 0L;

    private static final DateTimeFormatter FILE_TS_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /** 解析 jdbc:mysql://host:port/dbname 形式的 URL */
    private static final Pattern JDBC_URL_PATTERN =
            Pattern.compile("jdbc:mysql://([^:/]+)(?::(\\d+))?/([^?;]+)");

    /** 并发备份保护标记 */
    private final AtomicBoolean running = new AtomicBoolean(false);

    /**
     * 执行数据库备份: mysqldump → GZIP 压缩 → MinIO 上传。
     *
     * @param operatorId 操作人ID
     * @return 备份记录
     */
    public SysBackupRecord executeBackup(Long operatorId) {
        return executeBackup(operatorId, "MANUAL");
    }

    /**
     * 执行数据库备份。
     *
     * @param operatorId 操作人ID
     * @param backupType 备份类型: MANUAL / SCHEDULED
     * @return 备份记录
     */
    public SysBackupRecord executeBackup(Long operatorId, String backupType) {
        // AtomicBoolean check-and-set 防止并发备份
        if (!running.compareAndSet(false, true)) {
            throw new BusinessException(409, "已有备份任务进行中");
        }

        long startTime = System.currentTimeMillis();
        DbConnection conn = parseJdbcUrl(datasourceUrl);
        String timestamp = LocalDateTime.now().format(FILE_TS_FORMAT);
        String fileName = conn.dbName + "_" + timestamp + ".sql.gz";

        Path sqlFile = null;
        Path gzFile = null;
        try {
            sqlFile = Files.createTempFile("backup_" + timestamp + "_", ".sql");
            gzFile = Files.createTempFile("backup_" + timestamp + "_", ".sql.gz");

            // 1. ProcessBuilder 执行 mysqldump，stdout 重定向到临时 .sql 文件
            ProcessBuilder pb = new ProcessBuilder(
                    mysqldumpPath,
                    "-h" + conn.host,
                    "-P" + conn.port,
                    "-u" + datasourceUsername,
                    "-p" + datasourcePassword,
                    "--single-transaction",
                    "--routines",
                    "--triggers",
                    conn.dbName
            );
            pb.redirectOutput(sqlFile.toFile());
            pb.redirectErrorStream(false);
            Path errFile = Files.createTempFile("backup_" + timestamp + "_", ".err");
            pb.redirectError(errFile.toFile());

            Process process = pb.start();
            // 2. 超时控制
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                deleteQuietly(errFile);
                throw new BusinessException(500, "备份超时，已强制终止 mysqldump 进程");
            }
            int exitCode = process.exitValue();
            if (exitCode != 0) {
                String err = readErr(errFile);
                deleteQuietly(errFile);
                throw new BusinessException(500, "备份失败: mysqldump 退出码 " + exitCode
                        + (err.isEmpty() ? "" : ", " + err));
            }
            deleteQuietly(errFile);

            // 3. GZIP 压缩 .sql → .sql.gz
            gzipFile(sqlFile, gzFile);
            long fileSize = Files.size(gzFile);

            // 4. 上传 MinIO（真实客户端）
            String objectName = BACKUP_OBJECT_PREFIX + fileName;
            try (InputStream gzIn = Files.newInputStream(gzFile)) {
                minioService.upload(objectName, gzIn, fileSize, "application/gzip");
            } catch (BusinessException be) {
                throw be;
            } catch (Exception e) {
                throw new BusinessException(500, "存储失败: " + e.getMessage(), e);
            }

            long durationMs = System.currentTimeMillis() - startTime;

            // 5. 保存备份记录
            SysBackupRecord record = new SysBackupRecord();
            record.setFileName(fileName);
            record.setFileSize(fileSize);
            record.setDurationMs(durationMs);
            record.setStoragePath(objectName);
            record.setBackupType(backupType);
            record.setStatus("SUCCESS");
            record.setOperatorId(operatorId);
            record.setCreatedAt(LocalDateTime.now());
            backupRecordMapper.insert(record);

            log.info("数据库备份成功: {} ({} bytes, {} ms)", fileName, fileSize, durationMs);
            return record;
        } catch (BusinessException be) {
            recordFailure(fileName, backupType, operatorId, startTime, be.getMessage());
            throw be;
        } catch (Exception e) {
            log.error("数据库备份异常", e);
            recordFailure(fileName, backupType, operatorId, startTime, e.getMessage());
            throw new BusinessException(500, "备份失败: " + e.getMessage(), e);
        } finally {
            // 清理临时文件
            deleteQuietly(sqlFile);
            deleteQuietly(gzFile);
            running.set(false);
        }
    }

    /**
     * 从 MinIO 下载备份 → 解压 → mysql 命令恢复 → 记录恢复日志。
     *
     * @param backupId   源备份记录ID
     * @param operatorId 操作人ID
     */
    public void restore(Long backupId, Long operatorId) {
        SysBackupRecord record = backupRecordMapper.selectById(backupId);
        if (record == null) {
            throw new BusinessException(404, "备份记录不存在");
        }

        DbConnection conn = parseJdbcUrl(datasourceUrl);
        Path gzFile = null;
        Path sqlFile = null;
        String errorMessage = null;
        boolean success = false;
        try {
            gzFile = Files.createTempFile("restore_", ".sql.gz");
            sqlFile = Files.createTempFile("restore_", ".sql");

            // 1. 从 MinIO 下载
            try (InputStream in = minioService.download(record.getStoragePath());
                 OutputStream out = new BufferedOutputStream(Files.newOutputStream(gzFile))) {
                in.transferTo(out);
            } catch (Exception e) {
                throw new BusinessException(500, "存储下载失败: " + e.getMessage(), e);
            }

            // 2. 解压 .sql.gz → .sql
            gunzipFile(gzFile, sqlFile);

            // 3. ProcessBuilder 执行 mysql 恢复，stdin 重定向自 .sql 文件
            ProcessBuilder pb = new ProcessBuilder(
                    mysqlPath,
                    "-h" + conn.host,
                    "-P" + conn.port,
                    "-u" + datasourceUsername,
                    "-p" + datasourcePassword,
                    conn.dbName
            );
            pb.redirectInput(sqlFile.toFile());
            Path errFile = Files.createTempFile("restore_", ".err");
            pb.redirectError(errFile.toFile());

            Process process = pb.start();
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                deleteQuietly(errFile);
                throw new BusinessException(500, "恢复超时，已强制终止 mysql 进程");
            }
            int exitCode = process.exitValue();
            String err = readErr(errFile);
            deleteQuietly(errFile);
            if (exitCode != 0) {
                throw new BusinessException(500, "恢复失败: mysql 退出码 " + exitCode
                        + (err.isEmpty() ? "" : ", " + err));
            }

            success = true;
            log.info("数据库恢复成功: backupId={}, file={}", backupId, record.getFileName());
        } catch (BusinessException be) {
            errorMessage = be.getMessage();
            throw be;
        } catch (Exception e) {
            log.error("数据库恢复异常: backupId={}", backupId, e);
            errorMessage = e.getMessage();
            throw new BusinessException(500, "恢复失败: " + e.getMessage(), e);
        } finally {
            deleteQuietly(gzFile);
            deleteQuietly(sqlFile);
            // 记录恢复日志
            recordRestoreLog(backupId, operatorId, success, errorMessage);
        }
    }

    /**
     * 定时备份任务。cron 通过配置 {@code backup.cron} 注入，默认每日凌晨 2 点执行。
     */
    @Scheduled(cron = "${backup.cron:0 0 2 * * ?}")
    public void scheduledBackup() {
        log.info("开始执行定时数据库备份任务...");
        try {
            executeBackup(SYSTEM_OPERATOR_ID, "SCHEDULED");
            log.info("定时数据库备份任务完成");
        } catch (Exception e) {
            // 定时任务失败仅记录日志，不抛出中断调度
            log.error("定时数据库备份任务失败: {}", e.getMessage(), e);
        }
    }

    // ===================== 私有辅助方法 =====================

    private void recordFailure(String fileName, String backupType, Long operatorId,
                               long startTime, String errorMessage) {
        try {
            SysBackupRecord record = new SysBackupRecord();
            record.setFileName(fileName);
            record.setDurationMs(System.currentTimeMillis() - startTime);
            record.setStoragePath("");
            record.setBackupType(backupType);
            record.setStatus("FAILED");
            record.setErrorMessage(truncate(errorMessage, 2000));
            record.setOperatorId(operatorId);
            record.setCreatedAt(LocalDateTime.now());
            backupRecordMapper.insert(record);
        } catch (Exception e) {
            log.error("记录备份失败信息异常", e);
        }
    }

    private void recordRestoreLog(Long backupId, Long operatorId, boolean success, String errorMessage) {
        try {
            SysBackupRestoreLog logEntry = new SysBackupRestoreLog();
            logEntry.setBackupId(backupId);
            logEntry.setOperatorId(operatorId);
            logEntry.setRestoreTime(LocalDateTime.now());
            logEntry.setResult(success ? "SUCCESS" : "FAILED");
            logEntry.setErrorMessage(success ? null : truncate(errorMessage, 2000));
            logEntry.setCreatedAt(LocalDateTime.now());
            restoreLogMapper.insert(logEntry);
        } catch (Exception e) {
            log.error("记录恢复日志异常", e);
        }
    }

    private void gzipFile(Path source, Path target) throws IOException {
        try (InputStream in = Files.newInputStream(source);
             GZIPOutputStream gzOut = new GZIPOutputStream(
                     new BufferedOutputStream(Files.newOutputStream(target)))) {
            in.transferTo(gzOut);
        }
    }

    private void gunzipFile(Path source, Path target) throws IOException {
        try (GZIPInputStream gzIn = new GZIPInputStream(Files.newInputStream(source));
             OutputStream out = new BufferedOutputStream(Files.newOutputStream(target))) {
            gzIn.transferTo(out);
        }
    }

    private String readErr(Path errFile) {
        try {
            if (errFile != null && Files.exists(errFile)) {
                return truncate(Files.readString(errFile).trim(), 500);
            }
        } catch (IOException e) {
            log.warn("读取错误输出文件失败: {}", e.getMessage());
        }
        return "";
    }

    private void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("清理临时文件失败: {}", path, e);
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    /**
     * 从 JDBC URL 解析数据库连接信息(host/port/dbName)。
     */
    private DbConnection parseJdbcUrl(String url) {
        if (url == null) {
            throw new BusinessException(500, "数据源 URL 未配置");
        }
        Matcher matcher = JDBC_URL_PATTERN.matcher(url);
        if (!matcher.find()) {
            throw new BusinessException(500, "无法解析数据源 URL: " + url);
        }
        String host = matcher.group(1);
        String port = matcher.group(2) != null ? matcher.group(2) : "3306";
        String dbName = matcher.group(3);
        return new DbConnection(host, port, dbName);
    }

    /**
     * 数据库连接信息。
     */
    private record DbConnection(String host, String port, String dbName) {
    }
}
