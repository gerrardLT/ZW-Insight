package com.zwinsight.purchase.portal.controller;

import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.result.R;
import com.zwinsight.purchase.portal.dto.PublicInquiryDetailVO;
import com.zwinsight.purchase.portal.dto.PublicInquiryVO;
import com.zwinsight.purchase.portal.dto.PublicQuotationSubmitRequest;
import com.zwinsight.purchase.portal.service.SupplierAuthService;
import com.zwinsight.purchase.portal.service.SupplierInquiryService;
import com.zwinsight.purchase.portal.service.SupplierQuotationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 公开询价接口（免登录）
 * <p>
 * 该接口不需要任何认证，允许供应商无需注册即可浏览公开询价信息。
 * 路径 /api/v1/supplier-portal/public/** 已在 WebMvcConfig 和
 * SupplierPortalWebMvcConfig 中配置放行。
 */
@RestController
@RequestMapping("/api/v1/supplier-portal/public/inquiry")
@RequiredArgsConstructor
public class PublicInquiryController {

    private final SupplierInquiryService inquiryService;
    private final SupplierAuthService authService;
    private final SupplierQuotationService quotationService;

    /**
     * 公开询价列表（无需认证）
     * <p>
     * 仅返回 inviteMode=PUBLIC 且 status 为 OPEN 或 PUBLISHED 的询价单。
     *
     * @param page 页码，默认 1
     * @param size 每页大小，默认 10
     * @return 公开询价分页列表
     */
    @GetMapping("/list")
    public R<PageResult<PublicInquiryVO>> publicList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return R.ok(inquiryService.listPublicInquiries(page, size));
    }

    /**
     * 公开询价详情（无需认证）
     * <p>
     * 仅公开询价（inviteMode=PUBLIC）且状态为 OPEN/PUBLISHED 时可查看。
     *
     * @param id 询价单ID
     * @return 询价详情（含物料明细）
     */
    @GetMapping("/{id}")
    public R<PublicInquiryDetailVO> publicDetail(@PathVariable Long id) {
        return R.ok(inquiryService.getPublicInquiryDetail(id));
    }

    /**
     * 提交报价（需短信验证码验证，免登录）
     * <p>
     * 流程：验证码校验 → 截止时间校验 → 关联/创建供应商 → 提交报价
     *
     * @param id      询价单ID
     * @param request 报价请求（含手机号、验证码、报价明细）
     * @return 操作结果
     */
    @PostMapping("/{id}/quote")
    public R<Void> submitQuote(
            @PathVariable Long id,
            @RequestBody PublicQuotationSubmitRequest request) {
        // 1. 验证短信验证码
        authService.verifyCode(request.getPhone(), request.getSmsCode());

        // 2. 校验询价是否已截止
        inquiryService.checkDeadline(id);

        // 3. 关联或创建临时供应商
        Long supplierId = authService.getOrCreateSupplierByPhone(request.getPhone());
        String supplierName = authService.getSupplierName(supplierId);

        // 4. 提交报价
        quotationService.submitPublicQuotation(supplierId, supplierName, id, request.getDetails());
        return R.ok("报价提交成功", null);
    }
}
