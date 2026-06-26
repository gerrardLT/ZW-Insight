package com.zwinsight.message.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.message.domain.MsgAvailableShortcut;
import com.zwinsight.message.domain.MsgUserShortcut;
import com.zwinsight.message.dto.ShortcutBatchSaveResponse;
import com.zwinsight.message.mapper.MsgAvailableShortcutMapper;
import com.zwinsight.message.mapper.MsgUserShortcutMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 用户快捷入口服务
 */
@Service
@RequiredArgsConstructor
public class UserShortcutService {

    private static final int MAX_SHORTCUT_COUNT = 8;
    private static final int DEFAULT_SHORTCUT_COUNT = 4;
    private static final int AVAILABLE_LIST_LIMIT = 50;

    private final MsgUserShortcutMapper userShortcutMapper;
    private final MsgAvailableShortcutMapper availableShortcutMapper;

    /**
     * 根据用户ID获取快捷入口列表（按排序号排序）
     */
    public List<MsgUserShortcut> getByUserId(Long userId) {
        LambdaQueryWrapper<MsgUserShortcut> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MsgUserShortcut::getUserId, userId)
                .orderByAsc(MsgUserShortcut::getSortOrder);
        return userShortcutMapper.selectList(wrapper);
    }

    /**
     * 新增快捷入口
     */
    public void save(MsgUserShortcut shortcut) {
        userShortcutMapper.insert(shortcut);
    }

    /**
     * 删除快捷入口
     */
    public void delete(Long id) {
        userShortcutMapper.deleteById(id);
    }

    /**
     * 更新排序
     */
    public void updateSort(List<MsgUserShortcut> shortcuts) {
        for (MsgUserShortcut shortcut : shortcuts) {
            MsgUserShortcut update = new MsgUserShortcut();
            update.setId(shortcut.getId());
            update.setSortOrder(shortcut.getSortOrder());
            userShortcutMapper.updateById(update);
        }
    }

    /**
     * 批量保存用户快捷入口配置（整体替换方式）
     * <p>
     * 1. 校验 shortcutIds 数量 1-8 个
     * 2. 去重（保留首次出现的顺序位置）
     * 3. 过滤无效 ID（不在启用的 available_shortcut 中）
     * 4. 过滤后为空则拒绝保存
     * 5. 先删除该用户所有记录，再按顺序插入
     * 6. 返回 savedIds 和 invalidIds
     *
     * @param userId      用户ID
     * @param shortcutIds 功能ID列表（按排序顺序）
     * @return 保存结果
     */
    @Transactional(rollbackFor = Exception.class)
    public ShortcutBatchSaveResponse batchSave(Long userId, List<Long> shortcutIds) {
        // 1. 校验数量（原始列表）
        if (shortcutIds == null || shortcutIds.isEmpty()) {
            throw new BusinessException(400, "快捷入口数量不能为空");
        }
        if (shortcutIds.size() > MAX_SHORTCUT_COUNT) {
            throw new BusinessException(400, "快捷入口数量超出上限（最多" + MAX_SHORTCUT_COUNT + "个）");
        }

        // 2. 去重，保留首次出现的顺序位置
        LinkedHashSet<Long> deduplicatedSet = new LinkedHashSet<>(shortcutIds);
        List<Long> deduplicatedIds = new ArrayList<>(deduplicatedSet);

        // 3. 查询所有启用的 available_shortcut
        Set<Long> enabledIds = getEnabledShortcutIds();

        // 4. 分离有效ID和无效ID
        List<Long> validIds = new ArrayList<>();
        List<Long> invalidIds = new ArrayList<>();
        for (Long id : deduplicatedIds) {
            if (enabledIds.contains(id)) {
                validIds.add(id);
            } else {
                invalidIds.add(id);
            }
        }

        // 5. 过滤后为空时拒绝保存
        if (validIds.isEmpty()) {
            throw new BusinessException(400, "无有效功能项可保存");
        }

        // 6. 整体替换：先删除该用户所有记录
        LambdaQueryWrapper<MsgUserShortcut> deleteWrapper = new LambdaQueryWrapper<>();
        deleteWrapper.eq(MsgUserShortcut::getUserId, userId);
        userShortcutMapper.delete(deleteWrapper);

        // 7. 按顺序插入新记录
        for (int i = 0; i < validIds.size(); i++) {
            Long shortcutId = validIds.get(i);
            MsgAvailableShortcut available = availableShortcutMapper.selectById(shortcutId);

            MsgUserShortcut userShortcut = new MsgUserShortcut();
            userShortcut.setUserId(userId);
            userShortcut.setShortcutId(shortcutId);
            userShortcut.setSortOrder(i);
            if (available != null) {
                userShortcut.setMenuName(available.getName());
                userShortcut.setMenuPath(available.getRoutePath());
                userShortcut.setMenuIcon(available.getIcon());
            }
            userShortcutMapper.insert(userShortcut);
        }

        // 8. 构建返回结果
        ShortcutBatchSaveResponse response = new ShortcutBatchSaveResponse();
        response.setSavedIds(validIds);
        response.setInvalidIds(invalidIds);
        return response;
    }

    /**
     * 获取所有启用的可选快捷功能列表（上限50项）
     *
     * @return 启用状态的可选快捷功能列表
     */
    public List<MsgAvailableShortcut> getAvailableList() {
        LambdaQueryWrapper<MsgAvailableShortcut> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MsgAvailableShortcut::getStatus, "ENABLED")
                .orderByAsc(MsgAvailableShortcut::getSortOrder)
                .last("LIMIT " + AVAILABLE_LIST_LIMIT);
        return availableShortcutMapper.selectList(wrapper);
    }

    /**
     * 获取用户已选快捷入口配置
     * <p>
     * 按 sort_order 升序排列；若用户未配置过，返回默认列表（不超过4项）
     *
     * @param userId 用户ID
     * @return 用户配置列表
     */
    public List<MsgUserShortcut> getUserConfig(Long userId) {
        // 查询用户已选配置
        LambdaQueryWrapper<MsgUserShortcut> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MsgUserShortcut::getUserId, userId)
                .orderByAsc(MsgUserShortcut::getSortOrder);
        List<MsgUserShortcut> userShortcuts = userShortcutMapper.selectList(wrapper);

        // 已配置过则直接返回
        if (userShortcuts != null && !userShortcuts.isEmpty()) {
            return userShortcuts;
        }

        // 未配置过，返回默认列表（从可选列表中取前4项）
        List<MsgAvailableShortcut> availableList = getAvailableList();
        List<MsgUserShortcut> defaultList = new ArrayList<>();
        int limit = Math.min(availableList.size(), DEFAULT_SHORTCUT_COUNT);
        for (int i = 0; i < limit; i++) {
            MsgAvailableShortcut available = availableList.get(i);
            MsgUserShortcut defaultShortcut = new MsgUserShortcut();
            defaultShortcut.setUserId(userId);
            defaultShortcut.setShortcutId(available.getId());
            defaultShortcut.setMenuName(available.getName());
            defaultShortcut.setMenuPath(available.getRoutePath());
            defaultShortcut.setMenuIcon(available.getIcon());
            defaultShortcut.setSortOrder(i);
            defaultList.add(defaultShortcut);
        }
        return defaultList;
    }

    /**
     * 获取所有启用的快捷功能ID集合（内部使用）
     */
    private Set<Long> getEnabledShortcutIds() {
        LambdaQueryWrapper<MsgAvailableShortcut> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MsgAvailableShortcut::getStatus, "ENABLED")
                .select(MsgAvailableShortcut::getId);
        return availableShortcutMapper.selectList(wrapper)
                .stream()
                .map(MsgAvailableShortcut::getId)
                .collect(Collectors.toSet());
    }
}
