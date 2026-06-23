package com.zwinsight.system.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.system.domain.SysTenantType;
import com.zwinsight.system.mapper.SysTenantTypeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 租户类型管理服务
 */
@Service
@RequiredArgsConstructor
public class SysTenantTypeService {

    private final SysTenantTypeMapper tenantTypeMapper;

    /**
     * 分页查询
     */
    public PageResult<SysTenantType> page(int page, int size, String typeName, Integer status) {
        Page<SysTenantType> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<SysTenantType> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StrUtil.isNotBlank(typeName), SysTenantType::getTypeName, typeName)
                .eq(status != null, SysTenantType::getStatus, status)
                .orderByAsc(SysTenantType::getSortOrder);
        Page<SysTenantType> result = tenantTypeMapper.selectPage(pageParam, wrapper);
        return PageResult.of(result);
    }

    /**
     * 根据ID查询
     */
    public SysTenantType getById(Long id) {
        SysTenantType tenantType = tenantTypeMapper.selectById(id);
        if (tenantType == null) {
            throw new BusinessException("租户类型不存在");
        }
        return tenantType;
    }

    /**
     * 新增
     */
    public void save(SysTenantType tenantType) {
        tenantTypeMapper.insert(tenantType);
    }

    /**
     * 更新
     */
    public void update(SysTenantType tenantType) {
        SysTenantType existing = tenantTypeMapper.selectById(tenantType.getId());
        if (existing == null) {
            throw new BusinessException("租户类型不存在");
        }
        tenantTypeMapper.updateById(tenantType);
    }

    /**
     * 删除
     */
    public void delete(Long id) {
        SysTenantType existing = tenantTypeMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("租户类型不存在");
        }
        tenantTypeMapper.deleteById(id);
    }

    /**
     * 批量删除
     */
    public void batchDelete(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException("删除ID列表不能为空");
        }
        tenantTypeMapper.deleteBatchIds(ids);
    }
}
