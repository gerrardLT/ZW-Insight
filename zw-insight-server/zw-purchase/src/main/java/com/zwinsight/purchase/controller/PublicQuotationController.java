package com.zwinsight.purchase.controller;

import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.result.R;
import com.zwinsight.purchase.service.PublicQuotationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 公开报价查询接口（无需登录）
 * <p>
 * 为"公开邀请"类型的询价提供免登录的报价查询和中标公示页面。
 * 该接口不在安全过滤器的保护范围内（需在 SecurityConfig 中配置白名单）。
 * </p>
 * <p>
 * 安全设计：
 * <ul>
 *   <li>仅暴露已发布且邀请方式为"公开"的询价数据</li>
 *   <li>不返回供应商联系方式等敏感信息</li>
 *   <li>报价明细仅在公示阶段（AWARDED）后才可查看</li>
 *   <li>通过询价编号+验证码组合访问（防爬虫）</li>
 * </ul>
 * </p>
 */
@RestController
@RequestMapping("/api/v1/public/quotation")
@RequiredArgsConstructor
public class PublicQuotationController {

    private final PublicQuotationService publicQuotationService;

    /**
     * 查询公开询价列表（已发布+公开邀请的询价）
     * <p>
     * 返回询价标题、物料摘要、报价截止日期、当前状态等公开信息。
     * </p>
     *
     * @param page 页码
     * @param size 每页大小
     * @param keyword 关键字搜索（物料名称/询价标题）
     */
    @GetMapping("/inquiries")
    public R<PageResult<Map<String, Object>>> listPublicInquiries(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword) {
        return R.ok(publicQuotationService.listPublicInquiries(page, size, keyword));
    }

    /**
     * 查询指定询价的公开详情（物料清单+报价截止时间+要求）
     *
     * @param inquiryId 询价ID
     */
    @GetMapping("/inquiries/{inquiryId}")
    public R<Map<String, Object>> getInquiryDetail(@PathVariable Long inquiryId) {
        return R.ok(publicQuotationService.getPublicInquiryDetail(inquiryId));
    }

    /**
     * 查询中标公示信息（仅已公示的询价）
     * <p>
     * 返回中标供应商名称、中标金额、公示日期。
     * 不返回其他供应商报价明细和联系方式。
     * </p>
     *
     * @param inquiryId 询价ID
     */
    @GetMapping("/inquiries/{inquiryId}/award")
    public R<Map<String, Object>> getAwardAnnouncement(@PathVariable Long inquiryId) {
        return R.ok(publicQuotationService.getAwardAnnouncement(inquiryId));
    }

    /**
     * 供应商提交公开报价（免登录，通过手机号+验证码验证身份）
     * <p>
     * 供应商通过手机号注册/验证身份后，提交物料报价。
     * 报价一旦提交不可修改。
     * </p>
     *
     * @param inquiryId 询价ID
     * @param request   报价请求体
     */
    @PostMapping("/inquiries/{inquiryId}/quote")
    public R<Void> submitPublicQuotation(
            @PathVariable Long inquiryId,
            @RequestBody Map<String, Object> request) {
        publicQuotationService.submitPublicQuotation(inquiryId, request);
        return R.ok();
    }

    /**
     * 查询已提交的报价（通过手机号查询自己的报价记录）
     *
     * @param inquiryId 询价ID
     * @param phone     供应商手机号
     */
    @GetMapping("/inquiries/{inquiryId}/my-quote")
    public R<Map<String, Object>> getMyQuotation(
            @PathVariable Long inquiryId,
            @RequestParam String phone) {
        return R.ok(publicQuotationService.getMyQuotation(inquiryId, phone));
    }
}
