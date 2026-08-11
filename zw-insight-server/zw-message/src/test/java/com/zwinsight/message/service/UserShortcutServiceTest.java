package com.zwinsight.message.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.message.domain.MsgAvailableShortcut;
import com.zwinsight.message.domain.MsgUserShortcut;
import com.zwinsight.message.dto.ShortcutBatchSaveResponse;
import com.zwinsight.message.mapper.MsgAvailableShortcutMapper;
import com.zwinsight.message.mapper.MsgUserShortcutMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 用户快捷入口服务单元测试（批量保存校验/去重/整体替换 + 默认配置）
 */
@ExtendWith(MockitoExtension.class)
class UserShortcutServiceTest {

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, MsgUserShortcut.class);
        TableInfoHelper.initTableInfo(assistant, MsgAvailableShortcut.class);
    }

    @Mock private MsgUserShortcutMapper userShortcutMapper;
    @Mock private MsgAvailableShortcutMapper availableShortcutMapper;

    @InjectMocks
    private UserShortcutService userShortcutService;

    private MsgAvailableShortcut available(Long id, String name) {
        MsgAvailableShortcut shortcut = new MsgAvailableShortcut();
        shortcut.setId(id);
        shortcut.setName(name);
        shortcut.setRoutePath("/" + name);
        shortcut.setIcon("icon-" + name);
        shortcut.setStatus("ENABLED");
        return shortcut;
    }

    @Test
    @DisplayName("批量保存：空列表抛 400")
    void testBatchSave_empty() {
        assertThatThrownBy(() -> userShortcutService.batchSave(1L, Collections.emptyList()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能为空");
        verify(userShortcutMapper, never()).delete(any(LambdaQueryWrapper.class));
    }

    @Test
    @DisplayName("批量保存：超过8个抛 400")
    void testBatchSave_overLimit() {
        List<Long> ids = new ArrayList<>(List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L));
        ids.add(9L);

        assertThatThrownBy(() -> userShortcutService.batchSave(1L, ids))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("超出上限");
    }

    @Test
    @DisplayName("批量保存：全部无效ID抛 400 且不删旧配置")
    void testBatchSave_allInvalid() {
        when(availableShortcutMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(available(1L, "a"), available(2L, "b")));

        assertThatThrownBy(() -> userShortcutService.batchSave(1L, List.of(99L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无有效功能项");
        verify(userShortcutMapper, never()).delete(any(LambdaQueryWrapper.class));
    }

    @Test
    @DisplayName("批量保存：去重+分离无效ID+整体替换并按序落库")
    void testBatchSave_okWithDedupAndInvalidSplit() {
        when(availableShortcutMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(available(1L, "a"), available(2L, "b")));
        when(availableShortcutMapper.selectById(1L)).thenReturn(available(1L, "a"));
        when(availableShortcutMapper.selectById(2L)).thenReturn(available(2L, "b"));

        // [1,2,1,99]：去重后 [1,2,99]，有效 [1,2]，无效 [99]
        ShortcutBatchSaveResponse response = userShortcutService.batchSave(1L, List.of(1L, 2L, 1L, 99L));

        assertThat(response.getSavedIds()).containsExactly(1L, 2L);
        assertThat(response.getInvalidIds()).containsExactly(99L);
        verify(userShortcutMapper).delete(any(LambdaQueryWrapper.class));

        ArgumentCaptor<MsgUserShortcut> captor = ArgumentCaptor.forClass(MsgUserShortcut.class);
        verify(userShortcutMapper, times(2)).insert(captor.capture());
        List<MsgUserShortcut> inserted = captor.getAllValues();
        assertThat(inserted.get(0).getSortOrder()).isZero();
        assertThat(inserted.get(0).getShortcutId()).isEqualTo(1L);
        assertThat(inserted.get(0).getMenuName()).isEqualTo("a");
        assertThat(inserted.get(1).getSortOrder()).isEqualTo(1);
    }

    @Test
    @DisplayName("用户配置：已配置时直接返回，不生成默认项")
    void testGetUserConfig_existing() {
        MsgUserShortcut existing = new MsgUserShortcut();
        existing.setUserId(1L);
        when(userShortcutMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(existing));

        List<MsgUserShortcut> result = userShortcutService.getUserConfig(1L);

        assertThat(result).hasSize(1);
        verify(availableShortcutMapper, never()).selectList(any(LambdaQueryWrapper.class));
    }

    @Test
    @DisplayName("用户配置：未配置时返回默认前4项并携带菜单信息")
    void testGetUserConfig_default() {
        when(userShortcutMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.emptyList());
        List<MsgAvailableShortcut> availableList = List.of(
                available(1L, "a"), available(2L, "b"), available(3L, "c"),
                available(4L, "d"), available(5L, "e"));
        when(availableShortcutMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(availableList);

        List<MsgUserShortcut> result = userShortcutService.getUserConfig(1L);

        assertThat(result).hasSize(4);
        assertThat(result.get(0).getMenuName()).isEqualTo("a");
        assertThat(result.get(3).getSortOrder()).isEqualTo(3);
        verify(userShortcutMapper, never()).insert(any());
    }

    @Test
    @DisplayName("可选列表与排序更新：透传 mapper 调用")
    void testAvailableListAndUpdateSort() {
        when(availableShortcutMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(available(1L, "a")));
        assertThat(userShortcutService.getAvailableList()).hasSize(1);

        MsgUserShortcut shortcut = new MsgUserShortcut();
        shortcut.setId(10L);
        shortcut.setSortOrder(3);
        userShortcutService.updateSort(List.of(shortcut));
        verify(userShortcutMapper).updateById(any(MsgUserShortcut.class));
    }
}
