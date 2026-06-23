package com.zwinsight.material.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
     * 按项目查询库存列表（只读）
     */
    public List<BizProjectMaterialStock> getByProject(Long projectId) {
        LambdaQueryWrapper<BizProjectMaterialStock> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BizProjectMaterialStock::getProjectId, projectId)
                .orderByAsc(BizProjectMaterialStock::getMaterialName);
        return stockMapper.selectList(wrapper);
    }
}
