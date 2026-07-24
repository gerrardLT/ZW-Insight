package com.zwinsight.purchase.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.purchase.domain.BizInquiry;
import com.zwinsight.purchase.domain.BizInquiryItem;
import com.zwinsight.purchase.domain.BizInquirySupplier;
import com.zwinsight.purchase.domain.BizQuotation;
import com.zwinsight.purchase.mapper.BizInquiryItemMapper;
import com.zwinsight.purchase.mapper.BizInquiryMapper;
import com.zwinsight.purchase.mapper.BizInquirySupplierMapper;
import com.zwinsight.purchase.mapper.BizQuotationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 询价单服务
 */
@Service
@RequiredArgsConstructor
public class InquiryService {

    private final BizInquiryMapper inquiryMapper;
    private final BizInquiryItemMapper inquiryItemMapper;
    private final BizInquirySupplierMapper inquirySupplierMapper;
    private final BizQuotationMapper quotationMapper;

    /**
     * 分页查询
     */
    public PageResult<BizInquiry> page(int page, int size, String title, String status) {
        Page<BizInquiry> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<BizInquiry> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StrUtil.isNotBlank(title), BizInquiry::getTitle, title)
                .eq(StrUtil.isNotBlank(status), BizInquiry::getStatus, status)
                .orderByDesc(BizInquiry::getCreatedAt);
        Page<BizInquiry> result = inquiryMapper.selectPage(pageParam, wrapper);
        fillQuotationCount(result.getRecords());
        return PageResult.of(result);
    }

    /**
     * 按询价单聚合填充报价数（一次 group by 查询，避免 N+1）
     */
    private void fillQuotationCount(List<BizInquiry> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        List<Long> ids = records.stream().map(BizInquiry::getId).collect(Collectors.toList());
        QueryWrapper<BizQuotation> qw = new QueryWrapper<>();
        qw.select("inquiry_id", "COUNT(*) AS cnt")
                .in("inquiry_id", ids)
                .groupBy("inquiry_id");
        List<Map<String, Object>> rows = quotationMapper.selectMaps(qw);
        Map<Long, Integer> countMap = new HashMap<>();
        for (Map<String, Object> row : rows) {
            Object k = row.get("inquiry_id");
            Object v = row.get("cnt");
            if (k != null) {
                countMap.put(Long.parseLong(k.toString()), v == null ? 0 : Integer.parseInt(v.toString()));
            }
        }
        for (BizInquiry r : records) {
            r.setQuotationCount(countMap.getOrDefault(r.getId(), 0));
        }
    }

    /**
     * 根据ID查询
     */
    public BizInquiry getById(Long id) {
        BizInquiry inquiry = inquiryMapper.selectById(id);
        if (inquiry == null) {
            throw new BusinessException("询价单不存在");
        }
        return inquiry;
    }

    /**
     * 保存询价单（草稿）
     * <p>同时持久化随主表提交的物料明细与定向供应商，补齐三方比价链路入口。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void save(BizInquiry inquiry) {
        inquiry.setStatus("DRAFT");
        inquiryMapper.insert(inquiry);
        saveItems(inquiry.getId(), inquiry.getItems());
        saveSuppliers(inquiry.getId(), inquiry.getSuppliers());
    }

    /**
     * 更新询价单
     * <p>若随主表提交了物料明细/供应商，则整体替换（先删后插），保证与前端提交内容一致。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void update(BizInquiry inquiry) {
        BizInquiry existing = inquiryMapper.selectById(inquiry.getId());
        if (existing == null) {
            throw new BusinessException("询价单不存在");
        }
        if (!"DRAFT".equals(existing.getStatus())) {
            throw new BusinessException("仅草稿状态可编辑");
        }
        inquiryMapper.updateById(inquiry);
        if (inquiry.getItems() != null) {
            LambdaQueryWrapper<BizInquiryItem> delItems = new LambdaQueryWrapper<>();
            delItems.eq(BizInquiryItem::getInquiryId, inquiry.getId());
            inquiryItemMapper.delete(delItems);
            saveItems(inquiry.getId(), inquiry.getItems());
        }
        if (inquiry.getSuppliers() != null) {
            LambdaQueryWrapper<BizInquirySupplier> delSuppliers = new LambdaQueryWrapper<>();
            delSuppliers.eq(BizInquirySupplier::getInquiryId, inquiry.getId());
            inquirySupplierMapper.delete(delSuppliers);
            saveSuppliers(inquiry.getId(), inquiry.getSuppliers());
        }
    }

    /**
     * 持久化物料明细
     */
    private void saveItems(Long inquiryId, List<BizInquiryItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        for (BizInquiryItem item : items) {
            item.setId(null);
            item.setInquiryId(inquiryId);
            inquiryItemMapper.insert(item);
        }
    }

    /**
     * 持久化定向供应商
     */
    private void saveSuppliers(Long inquiryId, List<BizInquirySupplier> suppliers) {
        if (suppliers == null || suppliers.isEmpty()) {
            return;
        }
        for (BizInquirySupplier supplier : suppliers) {
            supplier.setId(null);
            supplier.setInquiryId(inquiryId);
            inquirySupplierMapper.insert(supplier);
        }
    }

    /**
     * 删除询价单
     */
    public void delete(Long id) {
        BizInquiry existing = inquiryMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("询价单不存在");
        }
        if (!"DRAFT".equals(existing.getStatus())) {
            throw new BusinessException("仅草稿状态可删除");
        }
        inquiryMapper.deleteById(id);
    }

    /**
     * 发布询价单（DRAFT→PUBLISHED）
     */
    @Transactional(rollbackFor = Exception.class)
    public void publish(Long id) {
        BizInquiry inquiry = inquiryMapper.selectById(id);
        if (inquiry == null) {
            throw new BusinessException("询价单不存在");
        }
        if (!"DRAFT".equals(inquiry.getStatus())) {
            throw new BusinessException("仅草稿状态可发布");
        }

        // 校验：至少有一个物料明细
        LambdaQueryWrapper<BizInquiryItem> itemWrapper = new LambdaQueryWrapper<>();
        itemWrapper.eq(BizInquiryItem::getInquiryId, id);
        Long itemCount = inquiryItemMapper.selectCount(itemWrapper);
        if (itemCount == 0) {
            throw new BusinessException("请至少添加一个询价物料");
        }

        // 定向模式下校验供应商
        if ("DIRECTED".equals(inquiry.getInviteMode())) {
            LambdaQueryWrapper<BizInquirySupplier> supplierWrapper = new LambdaQueryWrapper<>();
            supplierWrapper.eq(BizInquirySupplier::getInquiryId, id);
            Long supplierCount = inquirySupplierMapper.selectCount(supplierWrapper);
            if (supplierCount == 0) {
                throw new BusinessException("定向邀请模式下请至少指定一个供应商");
            }
        }

        inquiry.setStatus("PUBLISHED");
        inquiry.setPublishTime(LocalDateTime.now());
        inquiryMapper.updateById(inquiry);
    }

    /**
     * 获取询价物料列表
     */
    public List<BizInquiryItem> getItems(Long inquiryId) {
        LambdaQueryWrapper<BizInquiryItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BizInquiryItem::getInquiryId, inquiryId);
        return inquiryItemMapper.selectList(wrapper);
    }

    /**
     * 获取询价供应商列表
     */
    public List<BizInquirySupplier> getSuppliers(Long inquiryId) {
        LambdaQueryWrapper<BizInquirySupplier> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BizInquirySupplier::getInquiryId, inquiryId);
        return inquirySupplierMapper.selectList(wrapper);
    }
}
