package com.zwinsight.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.security.mapper.SysUserMapper;
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
    @Mock private SysUserMapper userMapper;

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

        // 白名单拷贝后落库的是既有实体，请求体里的系统字段不入库
        verify(postMapper).updateById(existing);
        assertThat(existing.getPostName()).isEqualTo("高级工程师");
    }

    @Test
    @DisplayName("更新岗位：跨租户岗位按不存在处理；非法状态值拒绝")
    void testUpdate_crossTenantAndInvalidStatus() {
        try (var sc = mockStatic(com.zwinsight.common.config.SecurityContextHolder.class)) {
            sc.when(com.zwinsight.common.config.SecurityContextHolder::getTenantId).thenReturn(1L);

            SysPost other = new SysPost();
            other.setId(2L);
            other.setTenantId(2L);
            when(postMapper.selectById(2L)).thenReturn(other);
            SysPost body = new SysPost();
            body.setId(2L);
            assertThatThrownBy(() -> postService.update(body))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("岗位不存在");

            SysPost mine = new SysPost();
            mine.setId(3L);
            mine.setTenantId(1L);
            when(postMapper.selectById(3L)).thenReturn(mine);
            SysPost bad = new SysPost();
            bad.setId(3L);
            bad.setStatus(7);
            assertThatThrownBy(() -> postService.update(bad))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("状态值无效");
            verify(postMapper, never()).updateById(any(SysPost.class));
        }
    }

    @Test
    @DisplayName("删除岗位：被人员引用时阻断；无引用才删除")
    void testDelete_referencedBlocked() {
        SysPost post = new SysPost();
        post.setId(5L);
        post.setPostName("项目经理");
        when(postMapper.selectById(5L)).thenReturn(post);
        when(userMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(3L);

        assertThatThrownBy(() -> postService.delete(5L))
                .isInstanceOf(BusinessException.class).hasMessageContaining("下存在 3 名人员");
        verify(postMapper, never()).deleteById(anyLong());

        when(userMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        postService.delete(5L);
        verify(postMapper).deleteById(5L);
    }

    @Test
    @DisplayName("批量删除：调用deleteBatchIds")
    void testBatchDelete() {
        List<Long> ids = List.of(1L, 2L);
        SysPost p1 = new SysPost();
        p1.setId(1L);
        SysPost p2 = new SysPost();
        p2.setId(2L);
        when(postMapper.selectById(1L)).thenReturn(p1);
        when(postMapper.selectById(2L)).thenReturn(p2);
        when(userMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        postService.batchDelete(ids);

        verify(postMapper).deleteBatchIds(ids);
    }

    @Test
    @DisplayName("批量删除：任一岗位被引用则整体拒绝，不删任何岗位；空列表拒绝")
    void testBatchDelete_anyReferencedRejectsAll() {
        SysPost p1 = new SysPost();
        p1.setId(1L);
        SysPost p2 = new SysPost();
        p2.setId(2L);
        p2.setPostName("会计");
        when(postMapper.selectById(1L)).thenReturn(p1);
        when(postMapper.selectById(2L)).thenReturn(p2);
        when(userMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L, 2L);

        assertThatThrownBy(() -> postService.batchDelete(List.of(1L, 2L)))
                .isInstanceOf(BusinessException.class).hasMessageContaining("会计");
        verify(postMapper, never()).deleteBatchIds(anyList());

        assertThatThrownBy(() -> postService.batchDelete(List.of()))
                .isInstanceOf(BusinessException.class).hasMessageContaining("不能为空");
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
