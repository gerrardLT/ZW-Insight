package com.zwinsight.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.system.domain.SysPost;
import com.zwinsight.system.mapper.SysPostMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SysPostServiceTest {

    @Mock private SysPostMapper postMapper;

    @InjectMocks
    private SysPostService postService;

    @Test
    @DisplayName("新增岗位：正常保存")
    void testSave() {
        SysPost post = new SysPost();
        post.setPostName("工程师");
        when(postMapper.insert(any())).thenReturn(1);

        postService.save(post);

        verify(postMapper).insert(post);
    }

    @Test
    @DisplayName("更新岗位：不存在抛异常")
    void testUpdate_notFound() {
        when(postMapper.selectById(999L)).thenReturn(null);

        SysPost update = new SysPost();
        update.setId(999L);

        assertThatThrownBy(() -> postService.update(update))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("岗位不存在");
    }

    @Test
    @DisplayName("更新岗位：存在则更新")
    void testUpdate_ok() {
        SysPost existing = new SysPost();
        existing.setId(1L);
        when(postMapper.selectById(1L)).thenReturn(existing);

        SysPost update = new SysPost();
        update.setId(1L);
        update.setPostName("高级工程师");
        postService.update(update);

        verify(postMapper).updateById(update);
    }

    @Test
    @DisplayName("批量删除：调用deleteBatchIds")
    void testBatchDelete() {
        List<Long> ids = List.of(1L, 2L);
        postService.batchDelete(ids);

        verify(postMapper).deleteBatchIds(ids);
    }

    @Test
    @DisplayName("岗位停用：岗位不存在抛异常")
    void testUpdateStatus_notFound() {
        when(postMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> postService.updateStatus(999L, 0))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("岗位不存在");
    }
}
