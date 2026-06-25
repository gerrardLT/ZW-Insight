package com.zwinsight.common.reference;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 引用信息 VO - 表示一条引用记录的详情
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReferenceInfoVO {

    /**
     * 引用类型（中文名，如"花名册"、"用工单"）
     */
    private String referenceType;

    /**
     * 引用单据编号（如果配置了 codeColumn）
     */
    private String documentCode;

    /**
     * 引用时间（记录创建时间）
     */
    private LocalDateTime referenceTime;
}
