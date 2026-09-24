package com.zwinsight.finance.dto;

import lombok.Data;

import java.time.LocalDate;

/**
 * 应收台账下钻信息维护请求（驾驶舱 UI 原型 §10 下钻 8 级链，V2026_69）
 * <p>§10 的 8 级中，项目 / 应收款 / 应收日期 已由台账既有字段覆盖；其余 5 级
 * （对应工程节点 / 实际申请日期 / 甲方审核状态 / 负责人 / 下一步动作）系统内无来源单据，
 * 由本请求人工登记。</p>
 * <p><b>字符串字段：null = 本次不修改，空串 = 清空该项登记</b>——两者语义不同：
 * 前者保留原值，后者把已登记内容撤回为「未登记」。不做此区分会导致
 * 「只想改负责人却把工程节点抹掉」的静默数据丢失。</p>
 * <p><b>日期字段：null = 不修改，且不支持清空</b>（LocalDate 无法区分「未传」与「传空」）。
 * 登记错了只能改成正确日期；该限制已在前端表单与接口文档中如实告知，不静默吞掉。</p>
 */
@Data
public class ReceivableDrillInfoRequest {

    /** 对应工程节点（§10 第 3 级） */
    private String milestoneNode;

    /** 实际申请日期（§10 第 5 级） */
    private LocalDate applyDate;

    /** 甲方审核状态（§10 第 6 级；SUBMITTED/UNDER_REVIEW/CONFIRMED/DISPUTED，非法值拒绝） */
    private String ownerReviewStatus;

    /** 甲方审核日期 */
    private LocalDate ownerReviewDate;

    /** 催收负责人ID（§10 第 7 级） */
    private Long ownerId;

    /** 催收负责人姓名（与 ownerId 同时提供；只给 id 时服务端不猜姓名） */
    private String ownerName;

    /** 下一步动作（§10 第 8 级） */
    private String nextAction;
}
