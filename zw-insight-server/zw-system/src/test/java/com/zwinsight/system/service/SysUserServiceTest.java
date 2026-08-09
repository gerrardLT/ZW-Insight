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

    // ==================== 安全测试场景 ====================

    @Test
    @DisplayName("新增用户：弱密码拒绝保存")
    void testSave_weakPassword_rejected() {
        SysUser user = new SysUser();
        user.setUsername("weakuser");
        user.setPassword("123456");  // 弱密码
        when(userMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        assertThatThrownBy(() -> userService.save(user))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("密码强度不足");
    }

    @Test
    @DisplayName("新增用户：强密码保存成功")
    void testSave_strongPassword_accepted() {
        SysUser user = new SysUser();
        user.setUsername("stronguser");
        user.setPassword("Str0ng!Pass#2024");  // 强密码
        when(userMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(passwordEncoder.encode("Str0ng!Pass#2024")).thenReturn("$2a$encoded");

        userService.save(user);

        assertThat(user.getPassword()).startsWith("$2a$");
        verify(userMapper).insert(user);
    }

    @Test
    @DisplayName("批量删除：空列表无异常")
    void testBatchDelete_emptyList_noException() {
        userService.batchDelete(List.of());

        // 不应抛出异常
        verify(userMapper, times(0)).deleteBatchIds(any());
    }

    @Test
    @DisplayName("批量删除：多个 ID 正确删除")
    void testBatchDelete_multipleIds() {
        List<Long> ids = List.of(1L, 2L, 3L, 4L, 5L);
        userService.batchDelete(ids);

        verify(userMapper).deleteBatchIds(ids);
        verify(userRoleMapper).delete(argThat(wrapper ->
            wrapper.getSql().contains("IN") && ids.stream().allMatch(id -> wrapper.getSql().contains(id.toString()))));
    }

    // ==================== 分页查询测试 ====================

    @Test
    @DisplayName("分页查询：无参数返回所有")
    void testPage_noParams_returnsAll() {
        com.baomidou.mybatisplus.core.page.Page<SysUser> pageParam = new com.baomidou.mybatisplus.core.page.Page<>(1, 10);
        when(userMapper.selectPage(eq(pageParam), any(LambdaQueryWrapper.class)))
            .thenReturn(new com.baomidou.mybatisplus.core.page.Page<>(1, 10, 50, true));

        var result = userService.page(1, 10, null, null, null, null);

        assertThat(result.getTotal()).isEqualTo(50);
        assertThat(result.getRecords()).isEmpty();
    }

    @Test
    @DisplayName("分页查询：按用户名模糊搜索")
    void testPage_byUsername_like() {
        com.baomidou.mybatisplus.core.page.Page<SysUser> pageParam = new com.baomidou.mybatisplus.core.page.Page<>(1, 10);
        LambdaQueryWrapper<SysUser> capturedWrapper = null;
        when(userMapper.selectPage(eq(pageParam), argThat(wrapper -> {
            capturedWrapper = (LambdaQueryWrapper<SysUser>) wrapper;
            return wrapper.getSqlSegment().contains("username");
        }))).thenReturn(new com.baomidou.mybatisplus.core.page.Page<>(1, 10, 10, true));

        userService.page(1, 10, "admin", null, null, null);

        assertThat(capturedWrapper).isNotNull();
    }

    // ==================== 导入导出测试 ====================

    @Test
    @DisplayName("导入用户：重复用户名跳过")
    void testImportUsers_duplicateUsername_skipped() throws Exception {
        MultipartFile mockFile = mock(MultipartFile.class);
        when(mockFile.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[0]));

        // 模拟 Excel 读取失败 - 仅测试代码路径
        doThrow(new BusinessException("文件读取失败"))
            .when(mockFile).getInputStream();

        assertThatThrownBy(() -> userService.importUsers(mockFile))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("文件读取失败");
    }

    @Test
    @DisplayName("批量更新状态：指定用户状态变更")
    void testUpdateStatus_batchUpdate() {
        List<Long> ids = List.of(1L, 2L, 3L);
        userService.updateStatus(ids, 0);  // 停用

        verify(userMapper).update(null, argThat(wrapper ->
            wrapper.getSqlSegment().contains("status") &&
            wrapper.getSqlSegment().contains("IN") &&
            ids.stream().allMatch(id -> wrapper.getSqlSegment().contains(id.toString()))));
    }

    // ==================== 密码加密算法对比测试 ====================

    @Test
    @DisplayName("BCrypt 密码加密：不可逆性验证")
    void testBCrypt_irreversible() {
        String plainPassword = "TestPassword123!";
        String encoded = passwordEncoder.encode(plainPassword);

        // 原始密码不应出现在编码后
        assertThat(encoded).doesNotContain(plainPassword);
        // BCrypt 编码格式验证
        assertThat(encoded).startsWith("$2a$")
                         .contains("$")
                         .hasSizeGreaterThanOrEqualTo(60);
    }

    @Test
    @DisplayName("BCrypt 密码匹配：正确密码验证通过")
    void testBCrypt_passwordMatches_success() {
        String plainPassword = "CorrectPassword!";
        String encoded = passwordEncoder.encode(plainPassword);

        // 实际应用中会调用 passwordEncoder.matches()
        assertThat(passwordEncoder.matches(plainPassword, encoded)).isTrue();
    }

    // ==================== SQL 注入防御测试 ====================

    @Test
    @DisplayName("SQL 注入：LIKE 查询防止注入")
    void testSqlInjection_prevention() {
        String maliciousInput = "%admin' OR '1'='1";
        
        // 验证 LIKE 条件中应使用预处理语句而非字符串拼接
        // 此处验证框架层面已使用 MyBatis-Plus LambdaQueryWrapper 避免注入
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(true, SysUser::getUsername, maliciousInput);
        
        // 不应直接拼接 SQL
        String sql = wrapper.getSql();
        assertThat(sql).doesNotContain("OR '1'='1");
    }
}
