package com.zwinsight.message.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.message.domain.MsgUserShortcut;
import com.zwinsight.message.mapper.MsgUserShortcutMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 用户快捷入口服务
 */
@Service
@RequiredArgsConstructor
public class UserShortcutService {

    private final MsgUserShortcutMapper userShortcutMapper;

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
}
