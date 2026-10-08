package com.zwinsight.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.security.domain.SysUser;
import com.zwinsight.security.mapper.SysUserMapper;
import com.zwinsight.system.domain.SysOrg;
import com.zwinsight.system.mapper.SysOrgMapper;
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
class SysOrgServiceTest {

    @Mock private SysOrgMapper orgMapper;
    @Mock private SysUserMapper userMapper;

    @InjectMocks
    private SysOrgService orgService;

    @Test
    @DisplayName("新增机构：顶级机构设置ancestors为0")
    void testSave_topLevel() {
        SysOrg org = new SysOrg();
        org.setOrgName("总公司");
        org.setParentId(0L);

        orgService.save(org);

        assertThat(org.getAncestors()).isEqualTo("0");
        verify(orgMapper).insert(org);
    }

    @Test
    @DisplayName("新增机构：父机构不存在抛异常")
    void testSave_parentNotFound() {
        SysOrg org = new SysOrg();
        org.setOrgName("子部门");
        org.setParentId(999L);
        when(orgMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> orgService.save(org))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("父机构不存在");
    }

    @Test
    @DisplayName("删除机构：存在子机构抛异常")
    void testDelete_hasChildren() {
        when(orgMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        assertThatThrownBy(() -> orgService.delete(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("存在子机构，无法删除");
    }

    @Test
    @DisplayName("删除机构：存在关联人员抛异常")
    void testDelete_hasUsers() {
        when(orgMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(userMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        assertThatThrownBy(() -> orgService.delete(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("机构下存在人员，无法删除");
    }

    @Test
    @DisplayName("删除机构：无子机构无人员正常删除")
    void testDelete_ok() {
        when(orgMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(userMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        orgService.delete(1L);

        verify(orgMapper).deleteById(1L);
    }

    private SysOrg org(Long id, Long parentId, String ancestors) {
        SysOrg o = new SysOrg();
        o.setId(id);
        o.setParentId(parentId);
        o.setAncestors(ancestors);
        return o;
    }

    @Test
    @DisplayName("更新机构：上级不能是自己或自己的后代（防成环）")
    void testUpdate_cycleRejected() {
        SysOrg self = org(2L, 1L, "0,1");
        when(orgMapper.selectById(2L)).thenReturn(self);

        SysOrg toSelf = new SysOrg();
        toSelf.setId(2L);
        toSelf.setParentId(2L);
        assertThatThrownBy(() -> orgService.update(toSelf))
                .isInstanceOf(BusinessException.class).hasMessageContaining("不能是自己");

        // 5 是 2 的后代（ancestors 含 2）
        when(orgMapper.selectById(5L)).thenReturn(org(5L, 2L, "0,1,2"));
        SysOrg toDescendant = new SysOrg();
        toDescendant.setId(2L);
        toDescendant.setParentId(5L);
        assertThatThrownBy(() -> orgService.update(toDescendant))
                .isInstanceOf(BusinessException.class).hasMessageContaining("下级机构");
        verify(orgMapper, never()).updateById(any(SysOrg.class));
    }

    @Test
    @DisplayName("更新机构：移动子树时后代 ancestors 同步重算，且 1,2 前缀不误伤 1,20")
    void testUpdate_moveRewritesDescendantPaths() {
        // 结构：0 → 1 → 2 → 3 → 4；目标新上级 9（ancestors=0）
        SysOrg moving = org(2L, 1L, "0,1");
        SysOrg child = org(3L, 2L, "0,1,2");
        SysOrg grandchild = org(4L, 3L, "0,1,2,3");
        // 路径前缀陷阱：「0,1,20」以「0,1,2」开头但不是 2 的后代，必须原样保留
        SysOrg unrelated = org(20L, 1L, "0,1,20");
        when(orgMapper.selectById(2L)).thenReturn(moving);
        when(orgMapper.selectById(9L)).thenReturn(org(9L, 0L, "0"));
        when(orgMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(child, grandchild, unrelated));

        SysOrg body = new SysOrg();
        body.setId(2L);
        body.setParentId(9L);
        orgService.update(body);

        assertThat(moving.getParentId()).isEqualTo(9L);
        assertThat(moving.getAncestors()).isEqualTo("0,9");
        assertThat(child.getAncestors()).isEqualTo("0,9,2");
        assertThat(grandchild.getAncestors()).isEqualTo("0,9,2,3");
        assertThat(unrelated.getAncestors()).isEqualTo("0,1,20");
        verify(orgMapper).updateById(child);
        verify(orgMapper).updateById(grandchild);
        verify(orgMapper, never()).updateById(unrelated);
    }

    @Test
    @DisplayName("更新机构：跨租户机构按不存在处理；非法状态拒绝")
    void testUpdate_crossTenantAndInvalidStatus() {
        try (var sc = mockStatic(com.zwinsight.common.config.SecurityContextHolder.class)) {
            sc.when(com.zwinsight.common.config.SecurityContextHolder::getTenantId).thenReturn(1L);
            SysOrg other = org(7L, 0L, "0");
            other.setTenantId(2L);
            when(orgMapper.selectById(7L)).thenReturn(other);
            SysOrg body = new SysOrg();
            body.setId(7L);
            assertThatThrownBy(() -> orgService.update(body))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("机构不存在");
            assertThatThrownBy(() -> orgService.updateStatus(7L, 0))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("机构不存在");
            assertThatThrownBy(() -> orgService.updateStatus(7L, 9))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("状态值无效");
        }
    }

    @Test
    @DisplayName("更新机构：不存在抛异常")
    void testUpdate_notFound() {
        when(orgMapper.selectById(999L)).thenReturn(null);

        SysOrg update = new SysOrg();
        update.setId(999L);

        assertThatThrownBy(() -> orgService.update(update))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("机构不存在");
    }
}
