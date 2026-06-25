package com.zwinsight.purchase.portal.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.purchase.domain.*;
import com.zwinsight.purchase.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 供应商门户 - 报价服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SupplierQuotationService {

    private final BizQuotationMapper quotationMapper;
    private final BizQuotationDetailMapper quotationDetailMapper;
    private final BizInquiryMapper inquiryMapper;
    private final BizInquirySupplierMapper inquirySupplierMapper;

    /**
     * 供应商提交报价
     *
     * @param supplierId   供应商ID
     * @param supplierName 供应商名称
     * @param inquiryId    询价单ID
     * @param details      报价明细列表
     */
    @Transactional(rollbackFor = Exception.class)
    public void submitQuotation(Long supplierId, String supplierName, Long inquiryId, List<BizQuotationDetail> details) {
        // 1. 校验询价单存在
        BizInquiry inquiry = inquiryMapper.selectById(inquiryId);
        if (inquiry == null) {
            throw new BusinessException("询价单不存在");
        }

        // 2. 校验询价单状态
        if (!"PUBLISHED".equals(inquiry.getStatus()) && !"QUOTED".equals(inquiry.getStatus())) {
            throw new BusinessException("当前询价单状态不允许报价");
        }

        // 3. 校验截止时间
        if (inquiry.getDeadline() != null) {
            if (LocalDateTime.now().isAfter(inquiry.getDeadline())) {
                throw new BusinessException("报价已截止");
            }
        } else if (inquiry.getPublishTime() != null) {
            // 未设置截止时间时，默认发布后7天截止
            LocalDateTime fallbackDeadline = inquiry.getPublishTime().plusDays(7);
            if (LocalDateTime.now().isAfter(fallbackDeadline)) {
                throw new BusinessException("报价已截止");
            }
        }

        // 4. 校验供应商是否被邀请
        LambdaQueryWrapper<BizInquirySupplier> supplierWrapper = new LambdaQueryWrapper<>();
        supplierWrapper.eq(BizInquirySupplier::getSupplierId, supplierId)
                .eq(BizInquirySupplier::getInquiryId, inquiryId);
        Long supplierCount = inquirySupplierMapper.selectCount(supplierWrapper);
        if (supplierCount == 0) {
            throw new BusinessException("您未被邀请参与此询价");
        }

        // 5. 校验是否重复报价
        LambdaQueryWrapper<BizQuotation> dupWrapper = new LambdaQueryWrapper<>();
        dupWrapper.eq(BizQuotation::getInquiryId, inquiryId)
                .eq(BizQuotation::getSupplierId, supplierId)
                .eq(BizQuotation::getStatus, "SUBMITTED");
        Long dupCount = quotationMapper.selectCount(dupWrapper);
        if (dupCount > 0) {
            throw new BusinessException("您已对该询价提交过报价，不可重复提交");
        }

        // 6. 计算报价总额
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (BizQuotationDetail detail : details) {
            if (detail.getTotalPrice() != null) {
                totalAmount = totalAmount.add(detail.getTotalPrice());
            } else if (detail.getUnitPrice() != null) {
                // 如果只有单价没有总价，暂先累加单价
                totalAmount = totalAmount.add(detail.getUnitPrice());
            }
        }

        // 7. 保存报价主表
        BizQuotation quotation = new BizQuotation();
        quotation.setInquiryId(inquiryId);
        quotation.setSupplierId(supplierId);
        quotation.setSupplierName(supplierName);
        quotation.setTotalAmount(totalAmount);
        quotation.setStatus("SUBMITTED");
        quotation.setSubmitTime(LocalDateTime.now());
        quotationMapper.insert(quotation);

        // 8. 保存报价明细
        for (BizQuotationDetail detail : details) {
            detail.setQuotationId(quotation.getId());
            quotationDetailMapper.insert(detail);
        }

        // 9. 更新询价单状态为 QUOTED
        if ("PUBLISHED".equals(inquiry.getStatus())) {
            inquiry.setStatus("QUOTED");
            inquiryMapper.updateById(inquiry);
        }

        log.info("供应商[{}]提交报价成功，询价单ID: {}, 报价总额: {}", supplierName, inquiryId, totalAmount);
    }

    /**
     * 查询"我的报价记录"
     *
     * @param supplierId 供应商ID
     * @param page       页码
     * @param size       每页数量
     * @return 分页报价记录
     */
    public PageResult<BizQuotation> myQuotations(Long supplierId, int page, int size) {
        Page<BizQuotation> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<BizQuotation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BizQuotation::getSupplierId, supplierId)
                .orderByDesc(BizQuotation::getSubmitTime);
        Page<BizQuotation> result = quotationMapper.selectPage(pageParam, wrapper);
        return PageResult.of(result);
    }
}
