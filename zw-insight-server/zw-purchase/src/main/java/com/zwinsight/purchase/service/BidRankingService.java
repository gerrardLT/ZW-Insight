package com.zwinsight.purchase.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.purchase.domain.BizBidResult;
import com.zwinsight.purchase.domain.BizInquiry;
import com.zwinsight.purchase.domain.BizQuotation;
import com.zwinsight.purchase.mapper.BizBidResultMapper;
import com.zwinsight.purchase.mapper.BizInquiryMapper;
import com.zwinsight.purchase.mapper.BizQuotationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 定标排名服务
 */
@Service
@RequiredArgsConstructor
public class BidRankingService {

    private final BizQuotationMapper quotationMapper;
    private final BizBidResultMapper bidResultMapper;
    private final BizInquiryMapper inquiryMapper;

    /**
     * 计算排名（按规则：LOWEST-最低价排名，COMPREHENSIVE-综合评审排名）
     */
    @Transactional(rollbackFor = Exception.class)
    public List<BizBidResult> calculateRanking(Long inquiryId) {
        BizInquiry inquiry = inquiryMapper.selectById(inquiryId);
        if (inquiry == null) {
            throw new BusinessException("询价单不存在");
        }
        if (!"QUOTED".equals(inquiry.getStatus()) && !"PUBLISHED".equals(inquiry.getStatus())) {
            throw new BusinessException("当前状态不允许计算排名");
        }

        // 获取所有已提交的报价
        LambdaQueryWrapper<BizQuotation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BizQuotation::getInquiryId, inquiryId)
                .eq(BizQuotation::getStatus, "SUBMITTED");
        List<BizQuotation> quotations = quotationMapper.selectList(wrapper);

        if (quotations.isEmpty()) {
            throw new BusinessException("暂无供应商报价，无法计算排名");
        }

        // 按总额升序排序（最低价优先）
        List<BizQuotation> sorted = quotations.stream()
                .sorted(Comparator.comparing(BizQuotation::getTotalAmount))
                .collect(Collectors.toList());

        // 清除旧的排名结果
        LambdaQueryWrapper<BizBidResult> deleteWrapper = new LambdaQueryWrapper<>();
        deleteWrapper.eq(BizBidResult::getInquiryId, inquiryId);
        bidResultMapper.delete(deleteWrapper);

        // 生成排名结果
        for (int i = 0; i < sorted.size(); i++) {
            BizQuotation q = sorted.get(i);
            BizBidResult result = new BizBidResult();
            result.setInquiryId(inquiryId);
            result.setSupplierId(q.getSupplierId());
            result.setSupplierName(q.getSupplierName());
            result.setRanking(i + 1);
            result.setTotalAmount(q.getTotalAmount());
            result.setIsWinner(0);
            bidResultMapper.insert(result);
        }

        // 返回排名结果
        LambdaQueryWrapper<BizBidResult> resultWrapper = new LambdaQueryWrapper<>();
        resultWrapper.eq(BizBidResult::getInquiryId, inquiryId)
                .orderByAsc(BizBidResult::getRanking);
        return bidResultMapper.selectList(resultWrapper);
    }

    /**
     * 确认中标（设定某供应商为中标方）
     */
    @Transactional(rollbackFor = Exception.class)
    public void confirmWinner(Long inquiryId, Long supplierId) {
        BizInquiry inquiry = inquiryMapper.selectById(inquiryId);
        if (inquiry == null) {
            throw new BusinessException("询价单不存在");
        }

        // 查找该供应商的排名记录
        LambdaQueryWrapper<BizBidResult> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BizBidResult::getInquiryId, inquiryId)
                .eq(BizBidResult::getSupplierId, supplierId);
        BizBidResult bidResult = bidResultMapper.selectOne(wrapper);
        if (bidResult == null) {
            throw new BusinessException("该供应商未参与排名");
        }

        // 先将所有结果的 isWinner 设为 0
        LambdaQueryWrapper<BizBidResult> allWrapper = new LambdaQueryWrapper<>();
        allWrapper.eq(BizBidResult::getInquiryId, inquiryId);
        List<BizBidResult> allResults = bidResultMapper.selectList(allWrapper);
        for (BizBidResult r : allResults) {
            r.setIsWinner(0);
            bidResultMapper.updateById(r);
        }

        // 设定该供应商为中标方
        bidResult.setIsWinner(1);
        bidResultMapper.updateById(bidResult);

        // 更新询价单状态为 AWARDED
        inquiry.setStatus("AWARDED");
        inquiryMapper.updateById(inquiry);
    }

    /**
     * 获取询价单的排名结果
     */
    public List<BizBidResult> getByInquiry(Long inquiryId) {
        LambdaQueryWrapper<BizBidResult> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BizBidResult::getInquiryId, inquiryId)
                .orderByAsc(BizBidResult::getRanking);
        return bidResultMapper.selectList(wrapper);
    }
}
