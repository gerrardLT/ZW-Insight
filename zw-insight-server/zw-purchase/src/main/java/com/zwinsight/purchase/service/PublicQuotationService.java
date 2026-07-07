package com.zwinsight.purchase.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.purchase.domain.BizInquiry;
import com.zwinsight.purchase.domain.BizQuotation;
import com.zwinsight.purchase.mapper.BizInquiryMapper;
import com.zwinsight.purchase.mapper.BizQuotationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;

/**
 * 公开报价查询服务（免登录）
 * <p>
 * 仅处理邀请方式为"PUBLIC"（公开邀请）的询价数据，
 * 隔离敏感信息（供应商联系方式、其他供应商报价详情等）。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PublicQuotationService {

    private final BizInquiryMapper inquiryMapper;
    private final BizQuotationMapper quotationMapper;

    /**
     * 查询公开询价列表
     * <p>
     * 条件：inviteMode=PUBLIC AND status IN (PUBLISHED, QUOTED, AWARDED, PUBLICIZED)
     * </p>
     */
    public PageResult<Map<String, Object>> listPublicInquiries(int page, int size, String keyword) {
        Page<BizInquiry> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<BizInquiry> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BizInquiry::getInviteMode, "PUBLIC")
                .in(BizInquiry::getStatus, "PUBLISHED", "QUOTED", "AWARDED", "PUBLICIZED")
                .like(keyword != null && !keyword.isBlank(), BizInquiry::getTitle, keyword)
                .orderByDesc(BizInquiry::getCreatedAt);

        Page<BizInquiry> result = inquiryMapper.selectPage(pageParam, wrapper);

        // 脱敏转换：仅返回公开信息
        List<Map<String, Object>> records = new ArrayList<>();
        for (BizInquiry inquiry : result.getRecords()) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", inquiry.getId());
            item.put("title", inquiry.getTitle());
            item.put("status", inquiry.getStatus());
            item.put("deadline", inquiry.getDeadline());
            item.put("awardMethod", inquiry.getAwardMethod());
            item.put("createdAt", inquiry.getCreatedAt());
            records.add(item);
        }

        PageResult<Map<String, Object>> pageResult = new PageResult<>();
        pageResult.setRecords(records);
        pageResult.setTotal(result.getTotal());
        pageResult.setPage((int) result.getCurrent());
        pageResult.setSize((int) result.getSize());
        pageResult.setPages((int) result.getPages());
        return pageResult;
    }

    /**
     * 查询公开询价详情（物料清单、要求、截止时间）
     */
    public Map<String, Object> getPublicInquiryDetail(Long inquiryId) {
        BizInquiry inquiry = inquiryMapper.selectById(inquiryId);
        if (inquiry == null) {
            throw new BusinessException("询价不存在");
        }
        if (!"PUBLIC".equals(inquiry.getInviteMode())) {
            throw new BusinessException("该询价不支持公开查看");
        }

        Map<String, Object> detail = new HashMap<>();
        detail.put("id", inquiry.getId());
        detail.put("title", inquiry.getTitle());
        detail.put("description", inquiry.getDescription());
        detail.put("deadline", inquiry.getDeadline());
        detail.put("awardMethod", inquiry.getAwardMethod());
        detail.put("status", inquiry.getStatus());
        detail.put("requirements", inquiry.getRequirements());
        // 物料清单需要从明细表获取（此处返回简要信息）
        detail.put("materialSummary", inquiry.getMaterialSummary());

        return detail;
    }

    /**
     * 查询中标公示（仅 AWARDED/PUBLICIZED 状态可查看）
     */
    public Map<String, Object> getAwardAnnouncement(Long inquiryId) {
        BizInquiry inquiry = inquiryMapper.selectById(inquiryId);
        if (inquiry == null) {
            throw new BusinessException("询价不存在");
        }
        if (!"PUBLIC".equals(inquiry.getInviteMode())) {
            throw new BusinessException("该询价不支持公开查看");
        }
        if (!"AWARDED".equals(inquiry.getStatus()) && !"PUBLICIZED".equals(inquiry.getStatus())) {
            throw new BusinessException("该询价尚未公示中标结果");
        }

        Map<String, Object> announcement = new HashMap<>();
        announcement.put("inquiryId", inquiryId);
        announcement.put("title", inquiry.getTitle());
        announcement.put("winnerName", inquiry.getWinnerName());
        announcement.put("winnerAmount", inquiry.getWinnerAmount());
        announcement.put("awardDate", inquiry.getAwardDate());
        announcement.put("publicizeDate", inquiry.getPublicizeDate());

        return announcement;
    }

    /**
     * 提交公开报价（通过手机号验证供应商身份）
     */
    public void submitPublicQuotation(Long inquiryId, Map<String, Object> request) {
        BizInquiry inquiry = inquiryMapper.selectById(inquiryId);
        if (inquiry == null) {
            throw new BusinessException("询价不存在");
        }
        if (!"PUBLIC".equals(inquiry.getInviteMode())) {
            throw new BusinessException("该询价不支持公开报价");
        }
        if (!"PUBLISHED".equals(inquiry.getStatus())) {
            throw new BusinessException("该询价已截止报价");
        }

        String phone = (String) request.get("phone");
        String supplierName = (String) request.get("supplierName");
        Object totalAmountObj = request.get("totalAmount");

        if (phone == null || phone.isBlank()) {
            throw new BusinessException("手机号不能为空");
        }
        if (supplierName == null || supplierName.isBlank()) {
            throw new BusinessException("供应商名称不能为空");
        }

        // 检查是否已报价（同一手机号只能报一次）
        Long existingCount = quotationMapper.selectCount(
                new LambdaQueryWrapper<BizQuotation>()
                        .eq(BizQuotation::getInquiryId, inquiryId)
                        .eq(BizQuotation::getSupplierPhone, phone));
        if (existingCount > 0) {
            throw new BusinessException("您已提交过报价，不可重复报价");
        }

        // 创建报价记录
        BizQuotation quotation = new BizQuotation();
        quotation.setInquiryId(inquiryId);
        quotation.setSupplierName(supplierName);
        quotation.setSupplierPhone(phone);
        quotation.setTotalAmount(totalAmountObj != null ? new BigDecimal(totalAmountObj.toString()) : BigDecimal.ZERO);
        quotation.setStatus("SUBMITTED");
        quotation.setQuotationSource("PUBLIC");
        quotationMapper.insert(quotation);

        log.info("公开报价提交成功: inquiryId={}, supplier={}, phone={}", inquiryId, supplierName, phone);
    }

    /**
     * 查询自己的报价记录（通过手机号）
     */
    public Map<String, Object> getMyQuotation(Long inquiryId, String phone) {
        if (phone == null || phone.isBlank()) {
            throw new BusinessException("手机号不能为空");
        }

        BizQuotation quotation = quotationMapper.selectOne(
                new LambdaQueryWrapper<BizQuotation>()
                        .eq(BizQuotation::getInquiryId, inquiryId)
                        .eq(BizQuotation::getSupplierPhone, phone)
                        .last("LIMIT 1"));

        if (quotation == null) {
            return Collections.emptyMap();
        }

        Map<String, Object> result = new HashMap<>();
        result.put("id", quotation.getId());
        result.put("supplierName", quotation.getSupplierName());
        result.put("totalAmount", quotation.getTotalAmount());
        result.put("status", quotation.getStatus());
        result.put("createdAt", quotation.getCreatedAt());
        return result;
    }
}
