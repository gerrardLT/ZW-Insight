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
