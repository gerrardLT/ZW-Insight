package com.zwinsight.material.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.material.domain.BizProjectMaterialStock;
import com.zwinsight.material.mapper.BizProjectMaterialStockMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 项目材料库存查询服务
 */
@Service
@RequiredArgsConstructor
public class ProjectMaterialStockService {

    private final BizProjectMaterialStockMapper stockMapper;

    /**
     * 分页查询库存
     */
    public PageResult<BizProjectMaterialStock> page(int page, int size, Long projectId) {
        Page<BizProjectMaterialStock> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<BizProjectMaterialStock> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(projectId != null, BizProjectMaterialStock::getProjectId, projectId)
                .orderByAsc(BizProjectMaterialStock::getMaterialName);
        Page<BizProjectMaterialStock> result = stockMapper.selectPage(pageParam, wrapper);
        return PageResult.of(result);
    }

    /**
     * 按项目查询库存列表（只读）
     */
    public List<BizProjectMaterialStock> getByProject(Long projectId) {
        LambdaQueryWrapper<BizProjectMaterialStock> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BizProjectMaterialStock::getProjectId, projectId)
                .orderByAsc(BizProjectMaterialStock::getMaterialName);
        return stockMapper.selectList(wrapper);
    }
}
