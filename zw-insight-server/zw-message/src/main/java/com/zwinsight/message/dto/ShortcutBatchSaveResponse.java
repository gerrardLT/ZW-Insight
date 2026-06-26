package com.zwinsight.message.dto;

import lombok.Data;

import java.util.List;

/**
 * 快捷入口批量保存响应
 */
@Data
public class ShortcutBatchSaveResponse {

    /**
     * 成功保存的ID列表
     */
    private List<Long> savedIds;

    /**
     * 被过滤的无效ID列表
     */
    private List<Long> invalidIds;
}
