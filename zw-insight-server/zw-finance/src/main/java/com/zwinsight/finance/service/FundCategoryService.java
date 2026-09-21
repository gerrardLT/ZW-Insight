package com.zwinsight.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.finance.domain.BizFundCategory;
import com.zwinsight.finance.mapper.BizFundCategoryMapper;
import com.zwinsight.finance.mapper.BizPaymentApplyMapper;
import com.zwinsight.finance.domain.BizPaymentApply;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 资金收支分类科目服务（3级树 CRUD + 引用校验）
 * <p>纪律：删除前必须校验付款申请引用；系统内置科目不可删除；
 * 编码全局唯一（租户隔离由拦截器自动注入）。</p>
 */
@Service
@RequiredArgsConstructor
public class FundCategoryService {

    private final BizFundCategoryMapper fundCategoryMapper;
    private final BizPaymentApplyMapper paymentApplyMapper;

    private static final String DIRECTION_INCOME = "INCOME";
    private static final String DIRECTION_EXPENSE = "EXPENSE";
    private static final String STATUS_ENABLED = "ENABLED";
    private static final String STATUS_DISABLED = "DISABLED";

    /**
     * 查询科目树（按方向过滤，默认全部）
     */
    public List<BizFundCategory> tree(String direction) {
        LambdaQueryWrapper<BizFundCategory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(direction != null && !direction.isBlank(), BizFundCategory::getDirection, direction)
                .orderByAsc(BizFundCategory::getSortOrder)
                .orderByAsc(BizFundCategory::getId);
        List<BizFundCategory> all = fundCategoryMapper.selectList(wrapper);
        return buildTree(all, 0L);
    }

    /**
     * 查询启用状态的科目列表（供单据下拉选择）
     */
    public List<BizFundCategory> listEnabled(String direction) {
        LambdaQueryWrapper<BizFundCategory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BizFundCategory::getStatus, STATUS_ENABLED)
                .eq(direction != null && !direction.isBlank(), BizFundCategory::getDirection, direction)
                .orderByAsc(BizFundCategory::getSortOrder)
                .orderByAsc(BizFundCategory::getId);
        return fundCategoryMapper.selectList(wrapper);
    }

    /**
     * 新增科目（层级 = 父级层级 + 1，最多3级；编码唯一）
     */
    public void save(BizFundCategory category) {
        validateCategory(category);

        if (category.getParentId() == null) {
            category.setParentId(0L);
        }
        // 层级计算：顶级=1，子级=父级+1（上限3）
        int level = 1;
        if (category.getParentId() != 0L) {
            BizFundCategory parent = fundCategoryMapper.selectById(category.getParentId());
            if (parent == null) {
                throw new BusinessException(400, "父级科目不存在");
            }
            if (STATUS_DISABLED.equals(parent.getStatus())) {
                throw new BusinessException(400, "父级科目已停用，不允许新增子科目");
            }
            if (parent.getLevel() >= 3) {
                throw new BusinessException(400, "科目层级最多3级，不允许在[" + parent.getName() + "]下继续添加子科目");
            }
            level = parent.getLevel() + 1;
            // 子科目方向必须与父级一致
            if (!parent.getDirection().equals(category.getDirection())) {
                throw new BusinessException(400, "子科目方向必须与父级一致（" + parent.getDirection() + "）");
            }
        }
        category.setLevel(level);
        if (category.getIsSystem() == null) {
            category.setIsSystem(0);
        }
        if (category.getSortOrder() == null) {
            category.setSortOrder(0);
        }
        category.setStatus(STATUS_ENABLED);
        fundCategoryMapper.insert(category);
    }

    /**
     * 修改科目（编码不允许改；系统内置科目名称可改但方向不允许改）
     */
    public void update(BizFundCategory category) {
        BizFundCategory existing = fundCategoryMapper.selectById(category.getId());
        if (existing == null) {
            throw new BusinessException(404, "科目不存在");
        }
        if (category.getName() == null || category.getName().isBlank() || category.getName().length() > 100) {
            throw new BusinessException(400, "科目名称不合法，需1-100个字符");
        }
        // 系统内置科目方向不允许改（防止破坏种子体系）
        if (existing.getIsSystem() != null && existing.getIsSystem() == 1
                && category.getDirection() != null && !category.getDirection().equals(existing.getDirection())) {
            throw new BusinessException(400, "系统内置科目不允许修改方向");
        }
        existing.setName(category.getName());
        if (category.getSortOrder() != null) {
            existing.setSortOrder(category.getSortOrder());
        }
        fundCategoryMapper.updateById(existing);
    }

    /**
     * 停用科目（软停用：不物理删除，单据历史引用不受影响）
     */
    public void disable(Long id) {
        BizFundCategory existing = fundCategoryMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(404, "科目不存在");
        }
        if (existing.getIsSystem() != null && existing.getIsSystem() == 1) {
            throw new BusinessException(400, "系统内置科目不允许停用");
        }
        existing.setStatus(STATUS_DISABLED);
        fundCategoryMapper.updateById(existing);
    }

    /**
     * 删除科目（仅自定义科目且无子级且无付款引用时可删）
     */
    public void delete(Long id) {
        BizFundCategory existing = fundCategoryMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(404, "科目不存在");
        }
        if (existing.getIsSystem() != null && existing.getIsSystem() == 1) {
            throw new BusinessException(400, "系统内置科目不允许删除");
        }
        // 子级校验
        Long childCount = fundCategoryMapper.selectCount(
                new LambdaQueryWrapper<BizFundCategory>().eq(BizFundCategory::getParentId, id));
        if (childCount > 0) {
            throw new BusinessException(400, "存在子科目，不允许删除");
        }
        // 付款申请引用校验（按编码）
        Long refCount = paymentApplyMapper.selectCount(
                new LambdaQueryWrapper<BizPaymentApply>().eq(BizPaymentApply::getPaymentCategory, existing.getCode()));
        if (refCount > 0) {
            throw new BusinessException(400, "科目[" + existing.getName() + "]已被" + refCount + "笔付款申请引用，不允许删除");
        }
        fundCategoryMapper.deleteById(id);
    }

    /**
     * 按编码查询科目（付款/回款保存时校验编码有效性）
     */
    public BizFundCategory getByCode(String code, String expectDirection) {
        if (code == null || code.isBlank()) {
            return null;
        }
        BizFundCategory category = fundCategoryMapper.selectOne(
                new LambdaQueryWrapper<BizFundCategory>().eq(BizFundCategory::getCode, code));
        if (category == null) {
            throw new BusinessException(400, "资金分类科目编码[" + code + "]不存在");
        }
        if (!STATUS_ENABLED.equals(category.getStatus())) {
            throw new BusinessException(400, "资金分类科目[" + category.getName() + "]已停用");
        }
        if (expectDirection != null && !expectDirection.equals(category.getDirection())) {
            throw new BusinessException(400, "资金分类科目[" + category.getName() + "]方向不匹配（期望" + expectDirection + "）");
        }
        return category;
    }

    // ==================== 私有方法 ====================

    private void validateCategory(BizFundCategory category) {
        if (category.getCode() == null || category.getCode().isBlank() || category.getCode().length() > 50) {
            throw new BusinessException(400, "科目编码不合法，需1-50个字符");
        }
        if (category.getName() == null || category.getName().isBlank() || category.getName().length() > 100) {
            throw new BusinessException(400, "科目名称不合法，需1-100个字符");
        }
        if (!DIRECTION_INCOME.equals(category.getDirection()) && !DIRECTION_EXPENSE.equals(category.getDirection())) {
            throw new BusinessException(400, "科目方向不合法，需为 INCOME 或 EXPENSE");
        }
        // 编码唯一性
        Long count = fundCategoryMapper.selectCount(
                new LambdaQueryWrapper<BizFundCategory>().eq(BizFundCategory::getCode, category.getCode()));
        if (count > 0) {
            throw new BusinessException(400, "科目编码[" + category.getCode() + "]已存在");
        }
    }

    /**
     * 递归组装树（parentId=0 为顶级）
     */
    private List<BizFundCategory> buildTree(List<BizFundCategory> all, Long parentId) {
        Map<Long, List<BizFundCategory>> childrenMap = all.stream()
                .collect(Collectors.groupingBy(BizFundCategory::getParentId));
        List<BizFundCategory> roots = childrenMap.getOrDefault(parentId, List.of());
        roots.forEach(node -> attachChildren(node, childrenMap));
        return roots;
    }

    /**
     * 递归挂载子级（最多3层，受层级上限保护不会无限递归）
     */
    private void attachChildren(BizFundCategory node, Map<Long, List<BizFundCategory>> childrenMap) {
        List<BizFundCategory> children = childrenMap.getOrDefault(node.getId(), List.of());
        node.setChildren(children);
        children.forEach(child -> attachChildren(child, childrenMap));
    }
}
