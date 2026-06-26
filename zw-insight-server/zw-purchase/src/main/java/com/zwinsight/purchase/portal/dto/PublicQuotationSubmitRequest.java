package com.zwinsight.purchase.portal.dto;

import com.zwinsight.purchase.domain.BizQuotationDetail;
import lombok.Data;

import java.util.List;

/**
 * 公开报价提交请求 DTO（免登录，需短信验证码）
 */
@Data
public class PublicQuotationSubmitRequest {

    /**
     * 供应商手机号
     */
    private String phone;

    /**
     * 短信验证码
     */
    private String smsCode;

    /**
     * 报价明细列表
     */
    private List<BizQuotationDetail> details;
}
