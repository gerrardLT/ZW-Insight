package com.zwinsight.purchase.portal.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.purchase.domain.BizInquiry;
import com.zwinsight.purchase.domain.BizInquiryItem;
import com.zwinsight.purchase.domain.BizInquirySupplier;
import com.zwinsight.purchase.mapper.BizInquiryItemMapper;
import com.zwinsight.purchase.mapper.BizInquiryMapper;
import com.zwinsight.purchase.mapper.BizInquirySupplierMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 供应商门户 - 询价查询服务
 */
@Service
@RequiredArgsConstructor
public class SupplierInquiryService {

    private final BizInquiryMapper inquiryMapper;
    private final BizInquiryItemMapper inquiryItemMapper;
    private final BizInquirySupplierMapper inquirySupplierMapper;

    /**
     * 查询当前供应商被邀请的询价列表（仅 PUBLISHED/QUOTED 状态）
     *
     * @param supplierId 供应商ID
     * @param page       页码
     * @param size       每页数量
     * @return 询价分页列表
     */
    public PageResult<BizInquiry> listMyInquiries(Long supplierId, int page, int size) {
        // 1. 查询供应商被邀请的询价ID列表
        LambdaQueryWrapper<BizInquirySupplier> supplierWrapper = new LambdaQueryWrapper<>();
        supplierWrapper.eq(BizInquirySupplier::getSupplierId, supplierId);
        List<BizInquirySupplier> supplierInquiries = inquirySupplierMapper.selectList(supplierWrapper);

        if (supplierInquiries.isEmpty()) {
            return new PageResult<>();
        }

        List<Long> inquiryIds = supplierInquiries.stream()
                .map(BizInquirySupplier::getInquiryId)
                .collect(Collectors.toList());

        // 2. 分页查询这些询价单（仅报价中的状态）
        Page<BizInquiry> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<BizInquiry> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(BizInquiry::getId, inquiryIds)
                .in(BizInquiry::getStatus, "PUBLISHED", "QUOTED")
                .orderByDesc(BizInquiry::getPublishTime);
        Page<BizInquiry> result = inquiryMapper.selectPage(pageParam, wrapper);
        return PageResult.of(result);
    }

    /**
     * 查询询价详情（含材料清单）
     *
     * @param supplierId 供应商ID
     * @param inquiryId  询价ID
     * @return 询价详情（包含物料列表）
     */
    public Map<String, Object> getInquiryDetail(Long supplierId, Long inquiryId) {
        // 校验供应商是否被邀请
        LambdaQueryWrapper<BizInquirySupplier> supplierWrapper = new LambdaQueryWrapper<>();
        supplierWrapper.eq(BizInquirySupplier::getSupplierId, supplierId)
                .eq(BizInquirySupplier::getInquiryId, inquiryId);
        Long count = inquirySupplierMapper.selectCount(supplierWrapper);
        if (count == 0) {
            throw new BusinessException("您无权查看此询价单");
        }

        // 查询询价主表
        BizInquiry inquiry = inquiryMapper.selectById(inquiryId);
        if (inquiry == null) {
            throw new BusinessException("询价单不存在");
        }

        // 查询物料明细
        LambdaQueryWrapper<BizInquiryItem> itemWrapper = new LambdaQueryWrapper<>();
        itemWrapper.eq(BizInquiryItem::getInquiryId, inquiryId);
        List<BizInquiryItem> items = inquiryItemMapper.selectList(itemWrapper);

        Map<String, Object> detail = new HashMap<>();
        detail.put("inquiry", inquiry);
        detail.put("items", items);
        return detail;
    }
}
