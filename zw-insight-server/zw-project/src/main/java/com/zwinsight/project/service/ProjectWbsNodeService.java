package com.zwinsight.project.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.project.domain.BizProjectWbsNode;
import com.zwinsight.project.mapper.BizProjectMapper;
import com.zwinsight.project.mapper.BizProjectWbsNodeMapper;
import com.zwinsight.project.util.ProjectNameFiller;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * WBS 节点服务（工作分解结构）。
 * <p>
 * 承载项目范围的主数据：项目 → 阶段(level 1) → 工作包(level 2) → 任务(level 3)。
 * CBS 成本账户通过 wbsNodeId 挂到工作包，使成本口径与范围/进度口径统一。
 * </p>
 *
 * <h3>关键不变量</h3>
 * <ol>
 *   <li><b>编号唯一</b>：同一租户+项目下 nodeCode 唯一（DB 唯一键 + 应用层前置校验，给出友好错误）</li>
 *   <li><b>层级自洽</b>：level 由父节点推导（root=1，child=parent+1），不接受调用方任意指定，
 *       防止「父子层级倒挂」导致树渲染错乱</li>
 *   <li><b>禁止环</b>：移动父节点时校验新父节点不得是自身或自身后代</li>
 *   <li><b>删除保护</b>：存在子节点时禁止删除（先删叶子），避免产生孤儿节点</li>
 *   <li><b>日期合法</b>：endDate 不得早于 startDate</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectWbsNodeService {

    /** 最大层级深度（防止无限嵌套拖垮树渲染与递归） */
    private static final int MAX_LEVEL = 5;

    /** 节点状态 */
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_INACTIVE = "INACTIVE";
    public static final String STATUS_CLOSED = "CLOSED";

    private final BizProjectWbsNodeMapper wbsNodeMapper;
    private final BizProjectMapper projectMapper;

    // ==================== 查询 ====================

    /**
     * 分页查询 WBS 节点。
     *
     * @param page      页码
     * @param size      每页大小
     * @param projectId 项目ID（必填，WBS 必须归属项目）
     * @param parentId  父节点ID；传 {@code rootOnly=true} 时忽略
     * @param status    状态过滤（可空）
     * @param keyword   编号/名称模糊匹配（可空）
     */
    public PageResult<BizProjectWbsNode> page(int page, int size, Long projectId, Long parentId,
                                              String status, String keyword) {
        if (projectId == null) {
            throw new BusinessException("项目ID不能为空");
        }
        Page<BizProjectWbsNode> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<BizProjectWbsNode> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BizProjectWbsNode::getProjectId, projectId)
                .eq(parentId != null, BizProjectWbsNode::getParentId, parentId)
                .eq(StrUtil.isNotBlank(status), BizProjectWbsNode::getStatus, status)
                .and(StrUtil.isNotBlank(keyword), w -> w
                        .like(BizProjectWbsNode::getNodeCode, keyword)
                        .or().like(BizProjectWbsNode::getNodeName, keyword))
                .orderByAsc(BizProjectWbsNode::getSortOrder)
                .orderByAsc(BizProjectWbsNode::getNodeCode);

        Page<BizProjectWbsNode> result = wbsNodeMapper.selectPage(pageParam, wrapper);
        ProjectNameFiller.fill(result.getRecords(), projectMapper,
                BizProjectWbsNode::getProjectId, BizProjectWbsNode::setProjectName);
        return PageResult.of(result);
    }

    /**
     * 获取项目 WBS 树（一次性取全量后内存装配，避免逐层查询的 N+1）。
     *
     * @param projectId 项目ID
     * @return 根节点列表，每个节点已装配 children
     */
    public List<BizProjectWbsNode> getTree(Long projectId) {
        if (projectId == null) {
            throw new BusinessException("项目ID不能为空");
        }
        List<BizProjectWbsNode> all = wbsNodeMapper.selectList(
                new LambdaQueryWrapper<BizProjectWbsNode>()
                        .eq(BizProjectWbsNode::getProjectId, projectId)
                        .orderByAsc(BizProjectWbsNode::getSortOrder)
                        .orderByAsc(BizProjectWbsNode::getNodeCode));
        return assembleTree(all);
    }

    /**
     * 内存装配树：O(n) 建索引 + O(n) 挂父子，孤儿节点（父已删）提升为根，不丢数据。
     */
    private List<BizProjectWbsNode> assembleTree(List<BizProjectWbsNode> all) {
        if (all == null || all.isEmpty()) {
            return new ArrayList<>();
        }
        Map<Long, BizProjectWbsNode> byId = new HashMap<>(all.size() * 2);
        for (BizProjectWbsNode node : all) {
            node.setChildren(new ArrayList<>());
            byId.put(node.getId(), node);
        }

        List<BizProjectWbsNode> roots = new ArrayList<>();
        for (BizProjectWbsNode node : all) {
            Long parentId = node.getParentId();
            BizProjectWbsNode parent = parentId != null ? byId.get(parentId) : null;
            if (parent != null && !Objects.equals(parent.getId(), node.getId())) {
                parent.getChildren().add(node);
            } else {
                // 根节点，或父节点已不存在的孤儿节点（提升为根，保证数据可见不丢失）
                roots.add(node);
            }
        }
        return roots;
    }

    /**
     * 查询单个节点（含项目名称回填）。
     */
    public BizProjectWbsNode getById(Long id) {
        BizProjectWbsNode node = wbsNodeMapper.selectById(id);
        if (node == null) {
            throw new BusinessException("WBS 节点不存在：" + id);
        }
        ProjectNameFiller.fill(Collections.singletonList(node), projectMapper,
                BizProjectWbsNode::getProjectId, BizProjectWbsNode::setProjectName);
        return node;
    }

    /**
     * 查询节点的直接子节点。
     */
    public List<BizProjectWbsNode> listChildren(Long id) {
        return wbsNodeMapper.selectList(new LambdaQueryWrapper<BizProjectWbsNode>()
                .eq(BizProjectWbsNode::getParentId, id)
                .orderByAsc(BizProjectWbsNode::getSortOrder)
                .orderByAsc(BizProjectWbsNode::getNodeCode));
    }

    /**
     * 下拉选择用的扁平列表（只返回 id/code/name/level，前端树选择器自行装配）。
     */
    public List<BizProjectWbsNode> listForSelect(Long projectId) {
        if (projectId == null) {
            throw new BusinessException("项目ID不能为空");
        }
        return wbsNodeMapper.selectList(new LambdaQueryWrapper<BizProjectWbsNode>()
                .eq(BizProjectWbsNode::getProjectId, projectId)
                .eq(BizProjectWbsNode::getStatus, STATUS_ACTIVE)
                .orderByAsc(BizProjectWbsNode::getNodeLevel)
                .orderByAsc(BizProjectWbsNode::getSortOrder)
                .orderByAsc(BizProjectWbsNode::getNodeCode));
    }

    // ==================== 写入 ====================

    /**
     * 新增 WBS 节点。
     * <p>level 由父节点推导，调用方传入的 level 被忽略（保证层级自洽）。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public BizProjectWbsNode create(BizProjectWbsNode node) {
        validateBasic(node, true);

        // 校验项目存在
        if (projectMapper.selectById(node.getProjectId()) == null) {
            throw new BusinessException("项目不存在：" + node.getProjectId());
        }

        // 推导层级
        if (node.getParentId() != null) {
            BizProjectWbsNode parent = getById(node.getParentId());
            if (!Objects.equals(parent.getProjectId(), node.getProjectId())) {
                throw new BusinessException("父节点不属于同一项目，禁止跨项目挂载");
            }
            int childLevel = (parent.getNodeLevel() != null ? parent.getNodeLevel() : 1) + 1;
            if (childLevel > MAX_LEVEL) {
                throw new BusinessException("WBS 层级不得超过 " + MAX_LEVEL + " 级");
            }
            node.setNodeLevel(childLevel);
        } else {
            node.setNodeLevel(1);
        }

        // 编号唯一性前置校验（DB 唯一键兜底，此处给出友好提示）
        assertCodeUnique(node.getProjectId(), node.getNodeCode(), null);

        if (StrUtil.isBlank(node.getStatus())) {
            node.setStatus(STATUS_ACTIVE);
        }
        if (node.getSortOrder() == null) {
            node.setSortOrder(nextSortOrder(node.getProjectId(), node.getParentId()));
        }

        wbsNodeMapper.insert(node);
        log.info("WBS 节点创建成功，id={}, projectId={}, code={}, level={}",
                node.getId(), node.getProjectId(), node.getNodeCode(), node.getNodeLevel());
        return node;
    }

    /**
     * 更新 WBS 节点。
     * <p>允许改名称/描述/日期/状态/排序；改父节点时做环检测并重算层级（含子树）。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public BizProjectWbsNode update(Long id, BizProjectWbsNode patch) {
        BizProjectWbsNode existing = getById(id);

        if (StrUtil.isNotBlank(patch.getNodeCode()) && !patch.getNodeCode().equals(existing.getNodeCode())) {
            assertCodeUnique(existing.getProjectId(), patch.getNodeCode(), id);
            existing.setNodeCode(patch.getNodeCode());
        }
        if (StrUtil.isNotBlank(patch.getNodeName())) {
            existing.setNodeName(patch.getNodeName());
        }
        if (patch.getDescription() != null) {
            existing.setDescription(patch.getDescription());
        }
        if (patch.getStartDate() != null) {
            existing.setStartDate(patch.getStartDate());
        }
        if (patch.getEndDate() != null) {
            existing.setEndDate(patch.getEndDate());
        }
        if (StrUtil.isNotBlank(patch.getStatus())) {
            existing.setStatus(patch.getStatus());
        }
        if (patch.getSortOrder() != null) {
            existing.setSortOrder(patch.getSortOrder());
        }

        validateDateRange(existing.getStartDate(), existing.getEndDate(), existing.getNodeCode());

        // 父节点变更：环检测 + 层级重算
        if (patch.getParentId() != null && !Objects.equals(patch.getParentId(), existing.getParentId())) {
            moveNode(existing, patch.getParentId());
        }

        wbsNodeMapper.updateById(existing);
        log.info("WBS 节点更新成功，id={}, code={}", id, existing.getNodeCode());
        return existing;
    }

    /**
     * 移动节点到新父节点（含环检测与子树层级重算）。
     */
    private void moveNode(BizProjectWbsNode node, Long newParentId) {
        if (Objects.equals(newParentId, node.getId())) {
            throw new BusinessException("不能将节点挂载为自身的子节点");
        }

        int newLevel;
        if (newParentId == null) {
            newLevel = 1;
        } else {
            BizProjectWbsNode newParent = getById(newParentId);
            if (!Objects.equals(newParent.getProjectId(), node.getProjectId())) {
                throw new BusinessException("父节点不属于同一项目，禁止跨项目挂载");
            }
            // 环检测：新父节点不得是自身的后代
            Set<Long> descendantIds = collectDescendantIds(node.getId());
            if (descendantIds.contains(newParentId)) {
                throw new BusinessException("目标父节点是当前节点的下级，移动将产生环");
            }
            newLevel = (newParent.getNodeLevel() != null ? newParent.getNodeLevel() : 1) + 1;
        }

        int depth = maxSubtreeDepth(node.getId());
        if (newLevel + depth - 1 > MAX_LEVEL) {
            throw new BusinessException("移动后子树层级将超过 " + MAX_LEVEL + " 级");
        }

        node.setParentId(newParentId);
        int oldLevel = node.getNodeLevel() != null ? node.getNodeLevel() : 1;
        node.setNodeLevel(newLevel);

        // 子树层级整体平移
        if (newLevel != oldLevel) {
            relevelDescendants(node.getId(), newLevel - oldLevel);
        }
    }

    /**
     * 删除节点（逻辑删除）。存在子节点时拒绝，避免产生孤儿。
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        BizProjectWbsNode node = getById(id);

        Long childCount = wbsNodeMapper.selectCount(new LambdaQueryWrapper<BizProjectWbsNode>()
                .eq(BizProjectWbsNode::getParentId, id));
        if (childCount != null && childCount > 0) {
            throw new BusinessException("该节点下还有 " + childCount + " 个子节点，请先删除子节点："
                    + node.getNodeCode());
        }

        wbsNodeMapper.deleteById(id);
        log.info("WBS 节点删除成功，id={}, projectId={}, code={}",
                id, node.getProjectId(), node.getNodeCode());
    }

    /**
     * 批量删除（逐个走单删校验，任一失败整体回滚）。
     */
    @Transactional(rollbackFor = Exception.class)
    public int batchDelete(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException("ID 列表不能为空");
        }
        for (Long id : ids) {
            delete(id);
        }
        log.info("WBS 节点批量删除成功，count={}", ids.size());
        return ids.size();
    }

    // ==================== 内部工具 ====================

    private void validateBasic(BizProjectWbsNode node, boolean isCreate) {
        if (isCreate && node.getProjectId() == null) {
            throw new BusinessException("项目ID不能为空");
        }
        if (StrUtil.isBlank(node.getNodeCode())) {
            throw new BusinessException("WBS 节点编号不能为空");
        }
        if (StrUtil.isBlank(node.getNodeName())) {
            throw new BusinessException("WBS 节点名称不能为空");
        }
        if (node.getNodeCode().length() > 50) {
            throw new BusinessException("WBS 节点编号长度不得超过 50 字符");
        }
        if (node.getNodeName().length() > 200) {
            throw new BusinessException("WBS 节点名称长度不得超过 200 字符");
        }
        validateDateRange(node.getStartDate(), node.getEndDate(), node.getNodeCode());
    }

    private void validateDateRange(LocalDate start, LocalDate end, String code) {
        if (start != null && end != null && end.isBefore(start)) {
            throw new BusinessException("WBS 节点[" + code + "]结束日期不得早于开始日期");
        }
    }

    private void assertCodeUnique(Long projectId, String code, Long excludeId) {
        LambdaQueryWrapper<BizProjectWbsNode> wrapper = new LambdaQueryWrapper<BizProjectWbsNode>()
                .eq(BizProjectWbsNode::getProjectId, projectId)
                .eq(BizProjectWbsNode::getNodeCode, code);
        if (excludeId != null) {
            wrapper.ne(BizProjectWbsNode::getId, excludeId);
        }
        Long count = wbsNodeMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw new BusinessException("WBS 节点编号已存在：" + code);
        }
    }

    /** 同层下一个排序号（追加到末尾） */
    private int nextSortOrder(Long projectId, Long parentId) {
        LambdaQueryWrapper<BizProjectWbsNode> wrapper = new LambdaQueryWrapper<BizProjectWbsNode>()
                .eq(BizProjectWbsNode::getProjectId, projectId)
                .orderByDesc(BizProjectWbsNode::getSortOrder)
                .last("LIMIT 1");
        if (parentId != null) {
            wrapper.eq(BizProjectWbsNode::getParentId, parentId);
        } else {
            wrapper.isNull(BizProjectWbsNode::getParentId);
        }
        BizProjectWbsNode last = wbsNodeMapper.selectOne(wrapper);
        return last != null && last.getSortOrder() != null ? last.getSortOrder() + 1 : 1;
    }

    /** 收集全部后代 ID（BFS，避免递归栈溢出） */
    private Set<Long> collectDescendantIds(Long rootId) {
        Set<Long> result = new HashSet<>();
        List<Long> frontier = new ArrayList<>();
        frontier.add(rootId);
        while (!frontier.isEmpty()) {
            List<BizProjectWbsNode> children = wbsNodeMapper.selectList(
                    new LambdaQueryWrapper<BizProjectWbsNode>()
                            .select(BizProjectWbsNode::getId)
                            .in(BizProjectWbsNode::getParentId, frontier));
            if (children.isEmpty()) {
                break;
            }
            frontier = children.stream()
                    .map(BizProjectWbsNode::getId)
                    .filter(id -> result.add(id))
                    .collect(Collectors.toList());
        }
        return result;
    }

    /** 子树最大深度（含自身，根为 1） */
    private int maxSubtreeDepth(Long rootId) {
        int depth = 1;
        List<Long> frontier = new ArrayList<>();
        frontier.add(rootId);
        while (!frontier.isEmpty()) {
            List<BizProjectWbsNode> children = wbsNodeMapper.selectList(
                    new LambdaQueryWrapper<BizProjectWbsNode>()
                            .select(BizProjectWbsNode::getId)
                            .in(BizProjectWbsNode::getParentId, frontier));
            if (children.isEmpty()) {
                break;
            }
            depth++;
            frontier = children.stream().map(BizProjectWbsNode::getId).collect(Collectors.toList());
        }
        return depth;
    }

    /** 子树层级整体平移 delta（正=下移，负=上移） */
    private void relevelDescendants(Long rootId, int delta) {
        List<Long> frontier = new ArrayList<>();
        frontier.add(rootId);
        while (!frontier.isEmpty()) {
            List<BizProjectWbsNode> children = wbsNodeMapper.selectList(
                    new LambdaQueryWrapper<BizProjectWbsNode>()
                            .in(BizProjectWbsNode::getParentId, frontier));
            if (children.isEmpty()) {
                break;
            }
            for (BizProjectWbsNode child : children) {
                int oldLevel = child.getNodeLevel() != null ? child.getNodeLevel() : 1;
                child.setNodeLevel(Math.max(1, oldLevel + delta));
                wbsNodeMapper.updateById(child);
            }
            frontier = children.stream().map(BizProjectWbsNode::getId).collect(Collectors.toList());
        }
    }
}
