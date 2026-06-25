package com.zwinsight.workflow.service.rollback;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 回滚执行结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RollbackResult {

    /**
     * 是否成功
     */
    private boolean success;

    /**
     * 回滚状态：1-成功 2-失败 3-冲突待确认
     */
    private int status;

    /**
     * 描述信息
     */
    private String message;

    /**
     * 冲突字段列表（当 status=3 时有值）
     */
    private List<String> conflictFields;

    /**
     * 重试次数
     */
    private int retryCount;

    public static RollbackResult success() {
        return RollbackResult.builder()
                .success(true)
                .status(1)
                .message("回滚成功")
                .retryCount(0)
                .build();
    }

    public static RollbackResult failed(String message, int retryCount) {
        return RollbackResult.builder()
                .success(false)
                .status(2)
                .message(message)
                .retryCount(retryCount)
                .build();
    }

    public static RollbackResult conflict(List<String> conflictFields) {
        return RollbackResult.builder()
                .success(false)
                .status(3)
                .message("存在数据冲突，需人工确认")
                .conflictFields(conflictFields)
                .retryCount(0)
                .build();
    }
}
