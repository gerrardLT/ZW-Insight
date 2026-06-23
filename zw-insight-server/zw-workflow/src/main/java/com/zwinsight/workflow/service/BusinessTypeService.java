package com.zwinsight.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.common.config.SecurityContextHolder;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.workflow.domain.WfBusinessType;
import com.zwinsight.workflow.mapper.WfBusinessTypeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 业务类型服务（树形CRUD）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BusinessTypeService {

    private final WfBusinessTypeMapper businessTypeMapper;

    /**
     * 查询业务类型树
     *
     * @return 树形业务类型列表
     */
    public List<Map<String, Object>> getTree() {
        Long tenantId = SecurityContextHolder.getTenantId();
        LambdaQueryWrapper<WfBusinessType> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WfBusinessType::getTenantId, tenantId)
                .orderByAsc(WfBusinessType::getSortOrder);
        List<WfBusinessType> allTypes = businessTypeMapper.selectList(wrapper);
        return buildTree(allTypes, 0L);
    }

    /**
     * 新增业务类型
     *
     * @param businessType 业务类型
     * @return 创建后的业务类型
     */
    @Transactional(rollbackFor = Exception.class)
    public WfBusinessType create(WfBusinessType businessType) {
        // 检查编码唯一性
        Long tenantId = SecurityContextHolder.getTenantId();
        LambdaQueryWrapper<WfBusinessType> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WfBusinessType::getTypeCode, businessType.getTypeCode())
                .eq(WfBusinessType::getTenantId, tenantId);
        Long count = businessTypeMapper.selectCount(wrapper);
        if (count > 0) {
            throw new BusinessException("业务类型编码已存在: " + businessType.getTypeCode());
        }

        if (businessType.getParentId() == null) {
            businessType.setParentId(0L);
        }
        if (businessType.getSortOrder() == null) {
            businessType.setSortOrder(0);
        }
        businessTypeMapper.insert(businessType);
        return businessType;
    }

    /**
     * 更新业务类型
     *
     * @param businessType 业务类型
     */
    @Transactional(rollbackFor = Exception.class)
    public void update(WfBusinessType businessType) {
        WfBusinessType existing = businessTypeMapper.selectById(businessType.getId());
        if (existing == null) {
            throw new BusinessException("业务类型不存在");
        }
        businessTypeMapper.updateById(businessType);
    }

    /**
     * 删除业务类型
     *
     * @param id 业务类型ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        // 检查是否有子节点
        LambdaQueryWrapper<WfBusinessType> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WfBusinessType::getParentId, id);
        Long childCount = businessTypeMapper.selectCount(wrapper);
        if (childCount > 0) {
            throw new BusinessException("存在子节点，不允许删除");
        }
        businessTypeMapper.deleteById(id);
    }

    /**
     * 根据ID查询
     *
     * @param id 业务类型ID
     * @return 业务类型
     */
    public WfBusinessType getById(Long id) {
        return businessTypeMapper.selectById(id);
    }

    // ===== 私有方法 =====

    private List<Map<String, Object>> buildTree(List<WfBusinessType> allTypes, Long parentId) {
        return allTypes.stream()
                .filter(t -> Objects.equals(t.getParentId(), parentId))
                .map(t -> {
                    Map<String, Object> node = new LinkedHashMap<>();
                    node.put("id", t.getId());
                    node.put("typeName", t.getTypeName());
                    node.put("typeCode", t.getTypeCode());
                    node.put("parentId", t.getParentId());
                    node.put("sortOrder", t.getSortOrder());
                    List<Map<String, Object>> children = buildTree(allTypes, t.getId());
                    if (!children.isEmpty()) {
                        node.put("children", children);
                    }
                    return node;
                })
                .collect(Collectors.toList());
    }
}
