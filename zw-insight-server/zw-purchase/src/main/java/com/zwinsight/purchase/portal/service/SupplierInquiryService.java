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
import com.zwinsight.purchase.portal.dto.PublicInquiryDetailVO;
import com.zwinsight.purchase.portal.dto.PublicInquiryVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Arrays;
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

    // ========================= 公开询价接口（免登录） =========================

    /**
     * 公开询价列表（仅 inviteMode=PUBLIC 且 status 为 OPEN 或 PUBLISHED）
     *
     * @param page 页码
     * @param size 每页数量
     * @return 公开询价分页列表
     */
    public PageResult<PublicInquiryVO> listPublicInquiries(int page, int size) {
        Page<BizInquiry> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<BizInquiry> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BizInquiry::getInviteMode, "PUBLIC")
                .in(BizInquiry::getStatus, Arrays.asList("OPEN", "PUBLISHED"))
                .orderByDesc(BizInquiry::getPublishTime);
        Page<BizInquiry> result = inquiryMapper.selectPage(pageParam, wrapper);

        // 转换为 VO
        List<PublicInquiryVO> voList = result.getRecords().stream().map(inquiry -> {
            PublicInquiryVO vo = new PublicInquiryVO();
            vo.setId(inquiry.getId());
            vo.setTitle(inquiry.getTitle());
            vo.setStatus(inquiry.getStatus());
            vo.setPublishTime(inquiry.getPublishTime());
            vo.setDeadline(inquiry.getDeadline());
            return vo;
        }).collect(Collectors.toList());

        PageResult<PublicInquiryVO> pageResult = new PageResult<>();
        pageResult.setRecords(voList);
        pageResult.setTotal(result.getTotal());
        pageResult.setPage(result.getCurrent());
        pageResult.setSize(result.getSize());
        pageResult.setPages(result.getPages());
        return pageResult;
    }

    /**
     * 公开询价详情（仅公开询价可查看，免登录）
     *
     * @param inquiryId 询价ID
     * @return 询价详情（含物料明细）
     */
    public PublicInquiryDetailVO getPublicInquiryDetail(Long inquiryId) {
        BizInquiry inquiry = inquiryMapper.selectById(inquiryId);
        if (inquiry == null) {
            throw new BusinessException("询价单不存在");
        }

        // 校验是否为公开询价且状态为 OPEN/PUBLISHED
        if (!"PUBLIC".equals(inquiry.getInviteMode())) {
            throw new BusinessException("该询价单非公开询价，无权查看");
        }
        if (!Arrays.asList("OPEN", "PUBLISHED").contains(inquiry.getStatus())) {
            throw new BusinessException("该询价单当前状态不可查看");
        }

        // 构造详情 VO
        PublicInquiryDetailVO detailVO = new PublicInquiryDetailVO();
        detailVO.setId(inquiry.getId());
        detailVO.setTitle(inquiry.getTitle());
        detailVO.setStatus(inquiry.getStatus());
        detailVO.setPublishTime(inquiry.getPublishTime());
        detailVO.setDeadline(inquiry.getDeadline());

        // 查询物料明细
        LambdaQueryWrapper<BizInquiryItem> itemWrapper = new LambdaQueryWrapper<>();
        itemWrapper.eq(BizInquiryItem::getInquiryId, inquiryId);
        List<BizInquiryItem> items = inquiryItemMapper.selectList(itemWrapper);

        List<PublicInquiryDetailVO.InquiryItemVO> itemVOList = items.stream().map(item -> {
            PublicInquiryDetailVO.InquiryItemVO itemVO = new PublicInquiryDetailVO.InquiryItemVO();
            itemVO.setMaterialName(item.getMaterialName());
            itemVO.setSpecification(item.getSpecification());
            itemVO.setUnit(item.getUnit());
            itemVO.setQuantity(item.getQuantity());
            return itemVO;
        }).collect(Collectors.toList());

        detailVO.setItems(itemVOList);
        return detailVO;
    }

    /**
     * 校验询价是否已截止（公开报价提交前调用）
     * <p>
     * 如果询价已超过 deadline 则拒绝，抛出 BusinessException。
     * 如果未设置 deadline，默认按发布后7天计算截止时间。
     *
     * @param inquiryId 询价ID
     * @throws BusinessException 询价不存在、非公开或已截止时抛出
     */
    public void checkDeadline(Long inquiryId) {
        BizInquiry inquiry = inquiryMapper.selectById(inquiryId);
        if (inquiry == null) {
            throw new BusinessException("询价单不存在");
        }

        // 校验是否为公开询价且状态允许报价
        if (!"PUBLIC".equals(inquiry.getInviteMode())) {
            throw new BusinessException("该询价单非公开询价，无法提交报价");
        }
        if (!Arrays.asList("OPEN", "PUBLISHED").contains(inquiry.getStatus())) {
            throw new BusinessException("当前询价单状态不允许报价");
        }

        // 校验截止时间
        LocalDateTime now = LocalDateTime.now();
        if (inquiry.getDeadline() != null) {
            if (now.isAfter(inquiry.getDeadline())) {
                throw new BusinessException("报价已截止");
            }
        } else if (inquiry.getPublishTime() != null) {
            // 未设置截止时间时，默认发布后7天截止
            LocalDateTime fallbackDeadline = inquiry.getPublishTime().plusDays(7);
            if (now.isAfter(fallbackDeadline)) {
                throw new BusinessException("报价已截止");
            }
        }
    }
}
