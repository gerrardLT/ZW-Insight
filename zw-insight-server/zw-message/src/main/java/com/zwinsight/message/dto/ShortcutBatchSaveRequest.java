package com.zwinsight.message.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 快捷入口批量保存请求
 */
@Data
public class ShortcutBatchSaveRequest {

    /**
     * 功能ID列表（按排序顺序）
     */
    @NotNull(message = "功能ID列表不能为null")
    private List<Long> shortcutIds;
}
