package com.zwinsight.common.dto;

import lombok.Data;

import java.util.List;

/**
 * 提交审批请求体（V2026_123）：ccUserIds 为可选抄送人。
 * <p>抄送仅在业务单据提交时生效，由 ApprovalService.startProcess 读取 variables.ccUserIds
 * 落库 biz_workflow_cc；请求体为空时行为与旧接口完全一致。</p>
 */
@Data
public class SubmitWithCcRequest {

    /** 抄送人用户ID列表（可选） */
    private List<Long> ccUserIds;
}
