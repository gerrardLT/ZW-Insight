package com.zwinsight.basedata.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.basedata.domain.BdMaterial;
import com.zwinsight.basedata.mapper.BdMaterialMapper;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 材料字典服务
 */
@Service
@RequiredArgsConstructor
public class MaterialService {

    private final BdMaterialMapper materialMapper;

    /**
     * 分页查询材料（支持分类筛选）
     */
    public PageResult<BdMaterial> page(int page, int size, String materialName, Long categoryId, String categoryName) {
        Page<BdMaterial> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<BdMaterial> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StrUtil.isNotBlank(materialName), BdMaterial::getMaterialName, materialName)
                .eq(categoryId != null, BdMaterial::getCategoryId, categoryId)
                .like(StrUtil.isNotBlank(categoryName), BdMaterial::getCategoryName, categoryName)
                .orderByDesc(BdMaterial::getCreatedAt);
        Page<BdMaterial> result = materialMapper.selectPage(pageParam, wrapper);
        return PageResult.of(result);
    }

    /**
     * 根据ID查询
     */
    public BdMaterial getById(Long id) {
        BdMaterial material = materialMapper.selectById(id);
        if (material == null) {
            throw new BusinessException("材料不存在");
        }
        return material;
    }

    /**
     * 新增材料
     */
    public void save(BdMaterial material) {
        materialMapper.insert(material);
    }

    /**
     * 更新材料
     */
    public void update(BdMaterial material) {
        BdMaterial existing = materialMapper.selectById(material.getId());
        if (existing == null) {
            throw new BusinessException("材料不存在");
        }
        materialMapper.updateById(material);
    }

    /**
     * 删除材料
     * <p>注：入库明细、库存等业务表均以 material_name 快照存储物料，与材料主数据无 material_id
     * 外键关联，删除主数据不影响历史单据快照，故不做引用校验。</p>
     */
    public void delete(Long id) {
        materialMapper.deleteById(id);
    }

    /**
     * 批量删除
     */
    @Transactional(rollbackFor = Exception.class)
    public void batchDelete(List<Long> ids) {
        materialMapper.deleteBatchIds(ids);
    }
}
