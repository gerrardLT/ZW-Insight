package com.zwinsight.site.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 现场影像与业务凭证存证请求（P3-A 真实证据链）
 */
@Data
public class EvidenceSubmitDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 业务领域类型 (INSPECTION, LOG, CHANGE, SIGN, WATERMARK) */
    private String businessType;

    /** 业务单据ID */
    private Long businessId;

    /** 所属项目ID */
    private Long projectId;

    /** 文件存储标识/URL */
    private String fileUrl;

    /** 客户端采集文件 SHA-256 摘要 */
    private String clientSha256;

    /** 拍摄/采集时间（客户端设备时间戳） */
    private Long capturedAtDevice;

    /** 经度 */
    private BigDecimal longitude;

    /** 纬度 */
    private BigDecimal latitude;

    /** 水印防伪载荷 (JSON) */
    private String watermarkPayload;
}
