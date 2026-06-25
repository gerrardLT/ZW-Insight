package com.zwinsight.contract.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * BOQ 上传结果 VO
 */
@Data
public class BoqUploadResultVO {

    /** 总条目数 */
    private Integer totalItems;

    /** 层级数（最大层级深度） */
    private Integer levelCount;

    /** 合计金额（所有顶层条目合价之和） */
    private BigDecimal totalAmount;

    /** 文件存储地址 */
    private String fileUrl;
}
