package com.zwinsight.basedata.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.basedata.domain.BdOwner;
import com.zwinsight.basedata.mapper.BdOwnerMapper;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 甲方单位服务
 */
@Service
@RequiredArgsConstructor
public class OwnerService {

    private final BdOwnerMapper ownerMapper;

    /**
     * 分页查询甲方
     */
    public PageResult<BdOwner> page(int page, int size, String ownerName, Integer status) {
        Page<BdOwner> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<BdOwner> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StrUtil.isNotBlank(ownerName), BdOwner::getOwnerName, ownerName)
                .eq(status != null, BdOwner::getStatus, status)
                .orderByDesc(BdOwner::getCreatedAt);
        Page<BdOwner> result = ownerMapper.selectPage(pageParam, wrapper);
        return PageResult.of(result);
    }

    /**
     * 根据ID查询
     */
    public BdOwner getById(Long id) {
        BdOwner owner = ownerMapper.selectById(id);
        if (owner == null) {
            throw new BusinessException("甲方单位不存在");
        }
        return owner;
    }

    /**
     * 新增甲方
     */
    public void save(BdOwner owner) {
        ownerMapper.insert(owner);
    }

    /**
     * 更新甲方
     */
    public void update(BdOwner owner) {
        BdOwner existing = ownerMapper.selectById(owner.getId());
        if (existing == null) {
            throw new BusinessException("甲方单位不存在");
        }
        ownerMapper.updateById(owner);
    }

    /**
     * 删除甲方
     */
    public void delete(Long id) {
        ownerMapper.deleteById(id);
    }
}
