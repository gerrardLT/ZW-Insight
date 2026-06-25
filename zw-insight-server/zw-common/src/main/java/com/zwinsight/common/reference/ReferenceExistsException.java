package com.zwinsight.common.reference;

import lombok.Getter;

import java.io.Serial;
import java.util.List;

/**
 * 引用存在异常
 * <p>
 * 当删除操作被引用校验拦截时抛出此异常，携带引用详情信息供前端展示。
 * </p>
 */
@Getter
public class ReferenceExistsException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 被引用的实体名称
     */
    private final String entityName;

    /**
     * 引用详情列表（最多 10 条）
     */
    private final List<ReferenceInfoVO> references;

    /**
     * 引用总数
     */
    private final long totalCount;

    public ReferenceExistsException(String entityName, List<ReferenceInfoVO> references, long totalCount) {
        super(String.format("该记录被 %d 条数据引用，无法删除", totalCount));
        this.entityName = entityName;
        this.references = references;
        this.totalCount = totalCount;
    }
}
