package com.zwinsight.basedata.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.basedata.domain.BizSupplierBlacklist;
import com.zwinsight.basedata.mapper.BizSupplierBlacklistMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SupplierBlacklistServiceTest {

    @Mock private BizSupplierBlacklistMapper blacklistMapper;

    @InjectMocks
    private SupplierBlacklistService blacklistService;

    @Test
    @DisplayName("加入黑名单：正常添加")
    void testAdd() {
        when(blacklistMapper.insert(any(BizSupplierBlacklist.class))).thenReturn(1);

        blacklistService.add(1L, "测试供应商", "质量问题");

        verify(blacklistMapper).insert(argThat(bl ->
                bl.getSupplierId() == 1L &&
                "测试供应商".equals(bl.getSupplierName()) &&
                "质量问题".equals(bl.getReason()) &&
                bl.getStatus() == 1));
    }

    @Test
    @DisplayName("移出黑名单：存在则状态置0")
    void testRemove_found() {
        BizSupplierBlacklist bl = new BizSupplierBlacklist();
        bl.setId(1L);
        bl.setStatus(1);
        when(blacklistMapper.selectById(1L)).thenReturn(bl);

        blacklistService.remove(1L);

        verify(blacklistMapper).updateById(argThat(b -> b.getStatus() == 0));
    }

    @Test
    @DisplayName("移出黑名单：不存在无操作")
    void testRemove_notFound() {
        when(blacklistMapper.selectById(999L)).thenReturn(null);

        blacklistService.remove(999L);

        verify(blacklistMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("检查黑名单：在黑名单中返回true")
    void testIsBlacklisted_true() {
        when(blacklistMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        boolean result = blacklistService.isBlacklisted(1L);

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("检查黑名单：不在黑名单中返回false")
    void testIsBlacklisted_false() {
        when(blacklistMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        boolean result = blacklistService.isBlacklisted(2L);

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("获取黑名单原因：存在返回原因")
    void testGetBlacklistReason_found() {
        BizSupplierBlacklist bl = new BizSupplierBlacklist();
        bl.setReason("严重违约");
        when(blacklistMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(bl);

        String reason = blacklistService.getBlacklistReason(1L);

        assertThat(reason).isEqualTo("严重违约");
    }

    @Test
    @DisplayName("获取黑名单原因：不存在返回未知原因")
    void testGetBlacklistReason_notFound() {
        when(blacklistMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        String reason = blacklistService.getBlacklistReason(999L);

        assertThat(reason).isEqualTo("未知原因");
    }
}
