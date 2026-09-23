package com.zwinsight.dashboard.dto;

import lombok.Data;

/**
 * 风险处理流转请求（V2026_59）
 * <p>
 * 采用请求体而非 query 参数：① 符合 RESTful 约定（PUT 携 body）；
 * ② 三端一致——移动端 uni.request 对 PUT 会把 data 放 body，若后端用
 * {@code @RequestParam} 则取不到参数；③ 避免一致性审计工具把 query string
 * 误当成路径的一部分而报 FRONTEND_EXTRA_API（2026-09-23 实测踩坑）。
 * </p>
 */
@Data
public class RiskHandleRequest {

    /** 处理动作（PROCESSING-认领/RESOLVED-解决/IGNORED-忽略/OPEN-重开） */
    private String action;

    /** 处理备注（IGNORED 时必填，留痕可审计） */
    private String handleNote;
}
