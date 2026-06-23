package com.zwinsight.basedata.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.basedata.domain.BdMaterial;
import com.zwinsight.basedata.domain.BdMaterialCategory;
import com.zwinsight.basedata.mapper.BdMaterialCategoryMapper;
import com.zwinsight.basedata.mapper.BdMaterialMapper;
import com.zwinsight.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 材料分类服务
 */
@Service
@RequiredArgsConstructor
public class MaterialCategoryService {

    private final BdMaterialCategoryMapper categoryMapper;
    private final BdMaterialMapper materialMapper;

    /**
     * 获取分类树形列表
     */
    public List<BdMaterialCategory> listTree() {
        List<BdMaterialCategory> allCategories = categoryMapper.selectList(
                new LambdaQueryWrapper<BdMaterialCategory>()
                        .orderByAsc(BdMaterialCategory::getSortOrder));
        return buildTree(allCategories, 0L);
    }

    /**
     * 新增分类
     */
    public void save(BdMaterialCategory category) {
        categoryMapper.insert(category);
    }

    /**
     * 更新分类
     */
    public void update(BdMaterialCategory category) {
        BdMaterialCategory existing = categoryMapper.selectById(category.getId());
        if (existing == null) {
            throw new BusinessException("分类不存在");
        }
        categoryMapper.updateById(category);
    }

    /**
     * 删除分类
     */
    public void delete(Long id) {
        // 检查是否有子分类
        long childCount = categoryMapper.selectCount(
                new LambdaQueryWrapper<BdMaterialCategory>()
                        .eq(BdMaterialCategory::getParentId, id));
        if (childCount > 0) {
            throw new BusinessException("存在子分类，不能删除");
        }
        // 检查是否有关联材料
        long materialCount = materialMapper.selectCount(
                new LambdaQueryWrapper<BdMaterial>()
                        .eq(BdMaterial::getCategoryId, id));
        if (materialCount > 0) {
            throw new BusinessException("分类下存在材料，不能删除");
        }
        categoryMapper.deleteById(id);
    }

    /**
     * 构建树形结构
     */
    private List<BdMaterialCategory> buildTree(List<BdMaterialCategory> categories, Long parentId) {
        Map<Long, List<BdMaterialCategory>> grouped = categories.stream()
                .collect(Collectors.groupingBy(BdMaterialCategory::getParentId));
        return buildChildren(grouped, parentId);
    }

    private List<BdMaterialCategory> buildChildren(Map<Long, List<BdMaterialCategory>> grouped, Long parentId) {
        List<BdMaterialCategory> children = grouped.getOrDefault(parentId, new ArrayList<>());
        // 递归不在实体中设children，直接返回扁平列表由前端处理
        // 若需要嵌套结构，可在此扩展DTO
        return children;
    }
}
