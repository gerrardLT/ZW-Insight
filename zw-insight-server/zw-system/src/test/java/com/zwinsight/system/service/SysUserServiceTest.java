package com.zwinsight.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.security.domain.SysUser;
import com.zwinsight.security.mapper.SysUserMapper;
import com.zwinsight.system.mapper.SysUserRoleMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SysUserServiceTest {

    @Mock private SysUserMapper userMapper;
    @Mock private SysUserRoleMapper userRoleMapper;
    @Mock private BCryptPasswordEncoder passwordEncoder;

    @InjectMocks
    private SysUserService userService;

    @Test
    @DisplayName("根据ID查询：返回用户")
    void testGetById() {
        SysUser user = new SysUser();
        user.setId(1L);
        user.setUsername("admin");
        when(userMapper.selectById(1L)).thenReturn(user);

        SysUser result = userService.getById(1L);

        assertThat(result).isNotNull();
        assertThat(result.getUsername()).isEqualTo("admin");
    }

    @Test
    @DisplayName("新增用户：用户名已存在抛异常")
    void testSave_duplicateUsername() {
        SysUser user = new SysUser();
        user.setUsername("admin");
        user.setPassword("123456");
        when(userMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        assertThatThrownBy(() -> userService.save(user))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("用户名已存在");
    }

    @Test
    @DisplayName("新增用户：正常保存并加密密码")
    void testSave_ok() {
        SysUser user = new SysUser();
        user.setUsername("newuser");
        user.setPassword("123456");
        when(userMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(passwordEncoder.encode("123456")).thenReturn("$2a$encoded");

        userService.save(user);

        assertThat(user.getPassword()).isEqualTo("$2a$encoded");
        verify(userMapper).insert(user);
    }

    @Test
    @DisplayName("更新用户：不存在抛异常")
    void testUpdate_notFound() {
        when(userMapper.selectById(999L)).thenReturn(null);

        SysUser update = new SysUser();
        update.setId(999L);

        assertThatThrownBy(() -> userService.update(update))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("用户不存在");
    }

    @Test
    @DisplayName("更新用户：密码字段被清空")
    void testUpdate_passwordCleared() {
        SysUser existing = new SysUser();
        existing.setId(1L);
        when(userMapper.selectById(1L)).thenReturn(existing);

        SysUser update = new SysUser();
        update.setId(1L);
        update.setPassword("shouldBeNull");
        userService.update(update);

        assertThat(update.getPassword()).isNull();
        verify(userMapper).updateById(update);
    }

    @Test
    @DisplayName("删除用户：同时删除角色关联")
    void testDelete() {
        userService.delete(1L);

        verify(userMapper).deleteById(1L);
        verify(userRoleMapper).delete(any(LambdaQueryWrapper.class));
    }

    @Test
    @DisplayName("分配角色：先删后插")
    void testAssignRoles() {
        userService.assignRoles(1L, List.of(10L, 20L));

        verify(userRoleMapper).delete(any(LambdaQueryWrapper.class));
        verify(userRoleMapper, times(2)).insert(any());
    }

    @Test
    @DisplayName("重置密码：密码被加密")
    void testResetPassword() {
        when(passwordEncoder.encode("newpwd")).thenReturn("$2a$newpwd");

        userService.resetPassword(1L, "newpwd");

        verify(userMapper).updateById(argThat(u ->
                "$2a$newpwd".equals(((SysUser) u).getPassword())));
    }
}
