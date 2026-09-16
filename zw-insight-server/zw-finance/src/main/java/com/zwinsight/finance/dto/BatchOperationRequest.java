package com.zwinsight.finance.dto;

import lombok.Data;

import java.util.List;

/**
 * 付款申请批量操作请求。
 * <p>
 * 统一批量入口：action 决定语义（delete=批量删除草稿 / submit=批量提交），
 * 空列表与未知 action 均被 Service 拒绝，整体单事务（任一失败回滚全部）。
 * </p>
 */
@Data
public class BatchOperationRequest {

    /** 目标单据 ID 列表（非空） */
    private List<Long> ids;

    /** 操作类型：delete / submit */
    private String action;
}
