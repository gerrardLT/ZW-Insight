package com.zwinsight.basedata.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.basedata.domain.BdInspectionScheme;
import com.zwinsight.basedata.mapper.BdInspectionSchemeMapper;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 检查方案服务
 */
@Service("basedataInspectionSchemeService")
@RequiredArgsConstructor
public class InspectionSchemeService {

    private final BdInspectionSchemeMapper schemeMapper;

    /**
     * 分页查询检查方案（支持类型和状态筛选）
     */
    public PageResult<BdInspectionScheme> page(int page, int size, String schemeName, String schemeType, Integer status) {
        Page<BdInspectionScheme> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<BdInspectionScheme> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StrUtil.isNotBlank(schemeName), BdInspectionScheme::getSchemeName, schemeName)
                .eq(StrUtil.isNotBlank(schemeType), BdInspectionScheme::getSchemeType, schemeType)
                .eq(status != null, BdInspectionScheme::getStatus, status)
                .orderByDesc(BdInspectionScheme::getCreatedAt);
        Page<BdInspectionScheme> result = schemeMapper.selectPage(pageParam, wrapper);
        return PageResult.of(result);
    }

    /**
     * 根据ID查询
     */
    public BdInspectionScheme getById(Long id) {
        BdInspectionScheme scheme = schemeMapper.selectById(id);
        if (scheme == null) {
            throw new BusinessException("检查方案不存在");
        }
        return scheme;
    }

    /**
     * 新增检查方案
     */
    public void save(BdInspectionScheme scheme) {
        schemeMapper.insert(scheme);
    }

    /**
     * 更新检查方案
     */
    public void update(BdInspectionScheme scheme) {
        BdInspectionScheme existing = schemeMapper.selectById(scheme.getId());
        if (existing == null) {
            throw new BusinessException("检查方案不存在");
        }
        schemeMapper.updateById(scheme);
    }

    /**
     * 删除检查方案
     */
    public void delete(Long id) {
        schemeMapper.deleteById(id);
    }
}
