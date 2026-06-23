package com.zwinsight.file.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 文件信息实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("file_info")
public class FileInfo extends BaseEntity {

    /**
     * 原始文件名
     */
    private String originalName;

    /**
     * 存储文件名
     */
    private String fileName;

    /**
     * 文件路径
     */
    private String filePath;

    /**
     * 文件大小（字节）
     */
    private Long fileSize;

    /**
     * 文件类型（MIME类型）
     */
    private String fileType;

    /**
     * 存储类型（MINIO/LOCAL）
     */
    private String storageType;

    /**
     * 业务类型
     */
    private String businessType;

    /**
     * 业务ID
     */
    private Long businessId;

    /**
     * 项目ID
     */
    private Long projectId;
}
