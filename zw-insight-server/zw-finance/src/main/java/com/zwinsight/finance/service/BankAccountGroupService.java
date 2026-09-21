package com.zwinsight.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.finance.domain.BizBankAccount;
import com.zwinsight.finance.domain.BizBankAccountGroup;
import com.zwinsight.finance.mapper.BizBankAccountGroupMapper;
import com.zwinsight.finance.mapper.BizBankAccountMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 银行账户分组服务（多级树，司库集中管控维度）
 */
@Service
@RequiredArgsConstructor
public class BankAccountGroupService {

    private final BizBankAccountGroupMapper groupMapper;
    private final BizBankAccountMapper accountMapper;

    private static final String STATUS_ENABLED = "ENABLED";
    private static final String STATUS_DISABLED = "DISABLED";

    /**
     * 查询全部启用的分组树（按 level+sort_order 升序）
     */
    public List<BizBankAccountGroup> tree() {
        LambdaQueryWrapper<BizBankAccountGroup> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAsc(BizBankAccountGroup::getLevel)
                .orderByAsc(BizBankAccountGroup::getSortOrder);
        List<BizBankAccountGroup> all = groupMapper.selectList(wrapper);
        return buildTree(all, 0L);
    }

    /**
     * 分页查询（支持按名称/编码/层级过滤）
     */
    public List<BizBankAccountGroup> list(String groupName, String groupCode, Integer level) {
        LambdaQueryWrapper<BizBankAccountGroup> wrapper = new LambdaQueryWrapper<>();
        if (groupName != null && !groupName.isBlank()) {
            wrapper.like(BizBankAccountGroup::getGroupName, groupName);
        }
        if (groupCode != null && !groupCode.isBlank()) {
            wrapper.like(BizBankAccountGroup::getGroupCode, groupCode);
        }
        if (level != null) {
            wrapper.eq(BizBankAccountGroup::getLevel, level);
        }
        wrapper.orderByAsc(BizBankAccountGroup::getLevel)
                .orderByAsc(BizBankAccountGroup::getSortOrder);
        return groupMapper.selectList(wrapper);
    }

    /**
     * 新增分组（编码唯一，父级存在性校验，自动计算层级）
     */
    public void save(BizBankAccountGroup group) {
        // 编码唯一
        Long count = groupMapper.selectCount(new LambdaQueryWrapper<BizBankAccountGroup>()
                .eq(BizBankAccountGroup::getGroupCode, group.getGroupCode()));
        if (count > 0) {
            throw new BusinessException(400, "分组编码[" + group.getGroupCode() + "]已存在");
        }
        // 父级校验
        if (group.getParentId() != null && group.getParentId() != 0L) {
            BizBankAccountGroup parent = groupMapper.selectById(group.getParentId());
            if (parent == null) {
                throw new BusinessException(400, "父级分组不存在");
            }
            if (parent.getLevel() >= 4) {
                throw new BusinessException(400, "最多支持四级分组");
            }
            group.setLevel(parent.getLevel() + 1);
        } else {
            group.setParentId(0L);
            group.setLevel(1);
        }
        group.setStatus(STATUS_ENABLED);
        if (group.getSortOrder() == null) {
            group.setSortOrder(0);
        }
        groupMapper.insert(group);
    }

    /**
     * 更新分组
     */
    public void update(Long id, BizBankAccountGroup group) {
        BizBankAccountGroup existing = groupMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(404, "分组不存在");
        }
        existing.setGroupName(group.getGroupName());
        existing.setGroupCode(group.getGroupCode());
        if (group.getSortOrder() != null) {
            existing.setSortOrder(group.getSortOrder());
        }
        if (group.getRemark() != null) {
            existing.setRemark(group.getRemark());
        }
        groupMapper.updateById(existing);
    }

    /**
     * 删除分组（逻辑删除前校验子分组与关联账户）
     */
    public void delete(Long id) {
        BizBankAccountGroup existing = groupMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(404, "分组不存在");
        }
        // 子分组存在则拒绝删除
        Long childCount = groupMapper.selectCount(new LambdaQueryWrapper<BizBankAccountGroup>()
                .eq(BizBankAccountGroup::getParentId, id));
        if (childCount > 0) {
            throw new BusinessException(400, "存在子分组，不可删除");
        }
        // 关联账户存在则拒绝删除（账户必须先归属其他分组）
        Long accountCount = accountMapper.selectCount(new LambdaQueryWrapper<BizBankAccount>()
                .eq(BizBankAccount::getGroupId, id));
        if (accountCount > 0) {
            throw new BusinessException(400, "有" + accountCount + "个账户归属此分组，请先转移后再删除");
        }
        groupMapper.deleteById(id);
    }

    /**
     * 启用/停用分组
     */
    public void toggleStatus(Long id, boolean enabled) {
        BizBankAccountGroup existing = groupMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(404, "分组不存在");
        }
        existing.setStatus(enabled ? STATUS_ENABLED : STATUS_DISABLED);
        groupMapper.updateById(existing);
    }

    // ==================== 私有方法 ====================

    /**
     * 构建分组树（递归挂载 children）
     */
    private List<BizBankAccountGroup> buildTree(List<BizBankAccountGroup> all, Long parentId) {
        Map<Long, List<BizBankAccountGroup>> byParent = all.stream()
                .collect(Collectors.groupingBy(g -> g.getParentId() == null ? 0L : g.getParentId()));
        return attachChildren(byParent, parentId);
    }

    private List<BizBankAccountGroup> attachChildren(Map<Long, List<BizBankAccountGroup>> byParent, Long parentId) {
        List<BizBankAccountGroup> children = byParent.getOrDefault(parentId, List.of());
        for (BizBankAccountGroup child : children) {
            child.setChildren(attachChildren(byParent, child.getId()));
        }
        return children;
    }
}
