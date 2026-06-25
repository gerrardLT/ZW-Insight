package com.zwinsight.file.batch.domain;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 异步导出任务状态
 * 存储在 Redis 中，key: export:task:{taskId}
 */
@Data
public class ExportStatus implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 任务状态
     */
    private String status;

    /**
     * 进度百分比 0-100
     */
    private int progress;

    /**
     * MinIO 下载 URL（导出完成后填充）
     */
    private String fileUrl;

    /**
     * MinIO 对象路径（用于生成下载链接）
     */
    private String objectName;

    /**
     * 错误信息（导出失败时填充）
     */
    private String errorMsg;

    /**
     * 导出文件名
     */
    private String fileName;

    // ---- 状态常量 ----
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_PROCESSING = "PROCESSING";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_FAILED = "FAILED";

    /**
     * 创建初始状态
     */
    public static ExportStatus pending() {
        ExportStatus status = new ExportStatus();
        status.setStatus(STATUS_PENDING);
        status.setProgress(0);
        return status;
    }

    /**
     * 更新为处理中
     */
    public void processing(int progress) {
        this.status = STATUS_PROCESSING;
        this.progress = progress;
    }

    /**
     * 更新为完成
     */
    public void completed(String objectName, String fileName) {
        this.status = STATUS_COMPLETED;
        this.progress = 100;
        this.objectName = objectName;
        this.fileName = fileName;
    }

    /**
     * 更新为失败
     */
    public void failed(String errorMsg) {
        this.status = STATUS_FAILED;
        this.errorMsg = errorMsg;
    }
}
