package com.zwinsight.purchase.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.purchase.domain.BizInquiry;
import com.zwinsight.purchase.domain.BizQuotation;
import com.zwinsight.purchase.domain.BizQuotationDetail;
import com.zwinsight.purchase.mapper.BizInquiryMapper;
import com.zwinsight.purchase.mapper.BizQuotationDetailMapper;
import com.zwinsight.purchase.mapper.BizQuotationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 报价服务
 */
@Service
@RequiredArgsConstructor
public class QuotationService {

    private final BizQuotationMapper quotationMapper;
    private final BizQuotationDetailMapper quotationDetailMapper;
    private final BizInquiryMapper inquiryMapper;

    /**
     * 供应商提报报价
     */
    @Transactional(rollbackFor = Exception.class)
    public void submitQuote(BizQuotation quotation, List<BizQuotationDetail> details) {
        // 校验询价单状态
        BizInquiry inquiry = inquiryMapper.selectById(quotation.getInquiryId());
        if (inquiry == null) {
            throw new BusinessException("询价单不存在");
        }
        if (!"PUBLISHED".equals(inquiry.getStatus()) && !"QUOTED".equals(inquiry.getStatus())) {
            throw new BusinessException("当前询价单状态不允许报价");
        }

        // 保存报价主表
        quotation.setStatus("SUBMITTED");
        quotation.setSubmitTime(LocalDateTime.now());

        // 计算报价总额
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (BizQuotationDetail detail : details) {
            if (detail.getTotalPrice() != null) {
                totalAmount = totalAmount.add(detail.getTotalPrice());
            }
        }
        quotation.setTotalAmount(totalAmount);
        quotationMapper.insert(quotation);

        // 保存报价明细
        for (BizQuotationDetail detail : details) {
            detail.setQuotationId(quotation.getId());
            quotationDetailMapper.insert(detail);
        }

        // 更新询价单状态为 QUOTED
        if ("PUBLISHED".equals(inquiry.getStatus())) {
            inquiry.setStatus("QUOTED");
            inquiryMapper.updateById(inquiry);
        }
    }

    /**
     * 获取询价单下所有报价汇总
     */
    public List<BizQuotation> getByInquiry(Long inquiryId) {
        LambdaQueryWrapper<BizQuotation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BizQuotation::getInquiryId, inquiryId)
                .eq(BizQuotation::getStatus, "SUBMITTED")
                .orderByAsc(BizQuotation::getTotalAmount);
        return quotationMapper.selectList(wrapper);
    }

    /**
     * 获取排名（按总价从低到高排序）
     */
    public List<BizQuotation> getRanking(Long inquiryId) {
        List<BizQuotation> quotations = getByInquiry(inquiryId);
        // 按总价排序（最低价优先）
        return quotations.stream()
                .sorted(Comparator.comparing(BizQuotation::getTotalAmount))
                .collect(Collectors.toList());
    }
}
