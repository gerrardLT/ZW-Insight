package com.zwinsight.site.dto;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 现场可信证据存证回执响应
 */
@Data
@Builder
public class EvidenceVO implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 服务端证据ID */
    private Long evidenceId;

    /** 业务领域类型 */
    private String businessType;

    /** 业务单据ID */
    private Long businessId;

    /** 项目ID */
    private Long projectId;

    /** 文件存储标识/URL */
    private String fileUrl;

    /** 经核验的 SHA-256 哈希 */
    private String verifiedSha256;

    /** 设备采集时间 */
    private Long capturedAtDevice;

    /** 服务端核验接收时间 */
    private LocalDateTime receivedAtServer;

    /** 存证验真状态: VERIFIED (已验真), PENDING (待比对), HASH_MISMATCH (哈希不符) */
    private String verificationStatus;

    /** 操作人用户ID */
    private Long operatorUserId;

    /** 经度 */
    private BigDecimal longitude;

    /** 纬度 */
    private BigDecimal latitude;
}
