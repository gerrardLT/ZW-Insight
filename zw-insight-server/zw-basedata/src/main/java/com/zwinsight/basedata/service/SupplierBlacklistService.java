package com.zwinsight.basedata.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.basedata.domain.BizSupplierBlacklist;
import com.zwinsight.basedata.mapper.BizSupplierBlacklistMapper;
import com.zwinsight.common.result.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/**
 * 供应商黑名单服务
 */
@Service
@RequiredArgsConstructor
public class SupplierBlacklistService {

    private final BizSupplierBlacklistMapper blacklistMapper;

    /**
     * 分页查询黑名单
     */
    public PageResult<BizSupplierBlacklist> page(int page, int size) {
        Page<BizSupplierBlacklist> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<BizSupplierBlacklist> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BizSupplierBlacklist::getStatus, 1)
                .orderByDesc(BizSupplierBlacklist::getBlacklistDate);
        Page<BizSupplierBlacklist> result = blacklistMapper.selectPage(pageParam, wrapper);
        return PageResult.of(result);
    }

    /**
     * 加入黑名单
     */
    public void add(Long supplierId, String supplierName, String reason) {
        BizSupplierBlacklist blacklist = new BizSupplierBlacklist();
        blacklist.setSupplierId(supplierId);
        blacklist.setSupplierName(supplierName);
        blacklist.setReason(reason);
        blacklist.setBlacklistDate(LocalDate.now());
        blacklist.setStatus(1);
        blacklistMapper.insert(blacklist);
    }

    /**
     * 移出黑名单
     */
    public void remove(Long id) {
        BizSupplierBlacklist blacklist = blacklistMapper.selectById(id);
        if (blacklist != null) {
            blacklist.setStatus(0);
            blacklistMapper.updateById(blacklist);
        }
    }

    /**
     * 检查是否在黑名单中
     */
    public boolean isBlacklisted(Long supplierId) {
        long count = blacklistMapper.selectCount(
                new LambdaQueryWrapper<BizSupplierBlacklist>()
                        .eq(BizSupplierBlacklist::getSupplierId, supplierId)
                        .eq(BizSupplierBlacklist::getStatus, 1));
        return count > 0;
    }

    /**
     * 获取供应商黑名单原因
     *
     * @param supplierId 供应商ID
     * @return 黑名单原因，如果未找到则返回"未知原因"
     */
    public String getBlacklistReason(Long supplierId) {
        BizSupplierBlacklist record = blacklistMapper.selectOne(
                new LambdaQueryWrapper<BizSupplierBlacklist>()
                        .eq(BizSupplierBlacklist::getSupplierId, supplierId)
                        .eq(BizSupplierBlacklist::getStatus, 1)
                        .last("LIMIT 1"));
        return record != null ? record.getReason() : "未知原因";
    }
}
