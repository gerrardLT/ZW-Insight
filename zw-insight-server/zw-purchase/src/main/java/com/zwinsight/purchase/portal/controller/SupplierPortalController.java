package com.zwinsight.purchase.portal.controller;

import com.zwinsight.common.result.PageResult;
import com.zwinsight.common.result.R;
import com.zwinsight.purchase.domain.BizInquiry;
import com.zwinsight.purchase.domain.BizQuotation;
import com.zwinsight.purchase.domain.BizQuotationDetail;
import com.zwinsight.purchase.portal.context.SupplierSecurityContext;
import com.zwinsight.purchase.portal.dto.SendCodeRequest;
import com.zwinsight.purchase.portal.dto.SupplierLoginRequest;
import com.zwinsight.purchase.portal.dto.SupplierQuotationSubmitDTO;
import com.zwinsight.purchase.portal.service.SupplierAuthService;
import com.zwinsight.purchase.portal.service.SupplierInquiryService;
import com.zwinsight.purchase.portal.service.SupplierQuotationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 供应商门户 REST API
 * <p>
 * 独立于主系统的供应商自助服务接口。
 * /auth/** 路径无需认证，其余路径需要供应商专用 JWT。
 */
@RestController
@RequestMapping("/api/v1/supplier-portal")
@RequiredArgsConstructor
public class SupplierPortalController {

    private final SupplierAuthService authService;
    private final SupplierInquiryService inquiryService;
    private final SupplierQuotationService quotationService;

    // ========================= 认证接口 =========================

    /**
     * 发送验证码
     */
    @PostMapping("/auth/send-code")
    public R<Void> sendCode(@RequestBody SendCodeRequest request) {
        authService.sendCode(request.getPhone());
        return R.ok("验证码发送成功", null);
    }

    /**
     * 验证码登录
     */
    @PostMapping("/auth/login")
    public R<Map<String, Object>> login(@RequestBody SupplierLoginRequest request) {
        Map<String, Object> result = authService.login(request.getPhone(), request.getCode());
        return R.ok("登录成功", result);
    }

    // ========================= 询价接口 =========================

    /**
     * 获取被邀请的询价列表
     */
    @GetMapping("/inquiry/list")
    public R<PageResult<BizInquiry>> inquiryList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        Long supplierId = SupplierSecurityContext.getSupplierId();
        return R.ok(inquiryService.listMyInquiries(supplierId, page, size));
    }

    /**
     * 询价详情（含材料清单）
     */
    @GetMapping("/inquiry/{id}")
    public R<Map<String, Object>> inquiryDetail(@PathVariable Long id) {
        Long supplierId = SupplierSecurityContext.getSupplierId();
        return R.ok(inquiryService.getInquiryDetail(supplierId, id));
    }

    // ========================= 报价接口 =========================

    /**
     * 提交报价
     */
    @PostMapping("/quotation")
    public R<Void> submitQuotation(@RequestBody SupplierQuotationSubmitDTO dto) {
        Long supplierId = SupplierSecurityContext.getSupplierId();
        String supplierName = SupplierSecurityContext.getSupplierName();
        quotationService.submitQuotation(supplierId, supplierName, dto.getInquiryId(), dto.getDetails());
        return R.ok("报价提交成功", null);
    }

    /**
     * 我的报价记录
     */
    @GetMapping("/quotation/mine")
    public R<PageResult<BizQuotation>> myQuotations(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        Long supplierId = SupplierSecurityContext.getSupplierId();
        return R.ok(quotationService.myQuotations(supplierId, page, size));
    }
}
