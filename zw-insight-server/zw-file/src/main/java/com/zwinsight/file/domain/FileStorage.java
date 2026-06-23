package com.zwinsight.file.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 文件存储配置实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("file_storage")
public class FileStorage extends BaseEntity {

    /**
     * 存储类型（LOCAL/MINIO/ALIYUN/TENCENT/QINIU）
     */
    private String storageType;

    /**
     * 存储端点地址
     */
    private String endpoint;

    /**
     * 访问密钥
     */
    private String accessKey;

    /**
     * 密钥
     */
    private String secretKey;

    /**
     * 存储桶
     */
    private String bucket;

    /**
     * 基础路径
     */
    private String basePath;

    /**
     * 状态（1-启用 0-停用）
     */
    private Integer status;
}
