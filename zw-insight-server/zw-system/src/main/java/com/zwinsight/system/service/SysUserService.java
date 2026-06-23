package com.zwinsight.system.service;

import cn.hutool.core.util.StrUtil;
import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.read.listener.PageReadListener;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.security.domain.SysUser;
import com.zwinsight.security.mapper.SysUserMapper;
import com.zwinsight.system.domain.SysUserRole;
import com.zwinsight.system.dto.SysUserExcelDTO;
import com.zwinsight.system.mapper.SysUserRoleMapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 用户管理服务
 */
@Service
@RequiredArgsConstructor
public class SysUserService {

    private final SysUserMapper userMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final BCryptPasswordEncoder passwordEncoder;

    /**
     * 分页查询用户
     */
    public PageResult<SysUser> page(int page, int size, String username, String realName,
                                    Long orgId, Integer status) {
        Page<SysUser> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StrUtil.isNotBlank(username), SysUser::getUsername, username)
                .like(StrUtil.isNotBlank(realName), SysUser::getRealName, realName)
                .eq(orgId != null, SysUser::getOrgId, orgId)
                .eq(status != null, SysUser::getStatus, status)
                .orderByDesc(SysUser::getCreatedAt);
        Page<SysUser> result = userMapper.selectPage(pageParam, wrapper);
        return PageResult.of(result);
    }

    /**
     * 根据ID查询
     */
    public SysUser getById(Long id) {
        return userMapper.selectById(id);
    }

    /**
     * 新增用户
     */
    @Transactional(rollbackFor = Exception.class)
    public void save(SysUser user) {
        // 检查用户名唯一
        long count = userMapper.selectCount(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, user.getUsername()));
        if (count > 0) {
            throw new BusinessException("用户名已存在");
        }
        // 密码加密
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        userMapper.insert(user);
    }

    /**
     * 更新用户
     */
    @Transactional(rollbackFor = Exception.class)
    public void update(SysUser user) {
        SysUser existing = userMapper.selectById(user.getId());
        if (existing == null) {
            throw new BusinessException("用户不存在");
        }
        // 不允许通过此接口修改密码
        user.setPassword(null);
        userMapper.updateById(user);
    }

    /**
     * 删除用户
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        userMapper.deleteById(id);
        // 删除用户角色关联
        userRoleMapper.delete(
                new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, id));
    }

    /**
     * 批量删除
     */
    @Transactional(rollbackFor = Exception.class)
    public void batchDelete(List<Long> ids) {
        userMapper.deleteByIds(ids);
        userRoleMapper.delete(
                new LambdaQueryWrapper<SysUserRole>().in(SysUserRole::getUserId, ids));
    }

    /**
     * 批量更新状态
     */
    public void updateStatus(List<Long> ids, Integer status) {
        LambdaUpdateWrapper<SysUser> wrapper = new LambdaUpdateWrapper<>();
        wrapper.in(SysUser::getId, ids)
                .set(SysUser::getStatus, status);
        userMapper.update(null, wrapper);
    }

    /**
     * 分配角色
     */
    @Transactional(rollbackFor = Exception.class)
    public void assignRoles(Long userId, List<Long> roleIds) {
        // 先删除原有关联
        userRoleMapper.delete(
                new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId));
        // 再批量插入新关联
        if (roleIds != null && !roleIds.isEmpty()) {
            for (Long roleId : roleIds) {
                SysUserRole userRole = new SysUserRole();
                userRole.setUserId(userId);
                userRole.setRoleId(roleId);
                userRoleMapper.insert(userRole);
            }
        }
    }

    /**
     * 重置密码
     */
    public void resetPassword(Long userId, String newPassword) {
        SysUser user = new SysUser();
        user.setId(userId);
        user.setPassword(passwordEncoder.encode(newPassword));
        userMapper.updateById(user);
    }

    /**
     * 导入用户
     */
    @Transactional(rollbackFor = Exception.class)
    public int importUsers(MultipartFile file) {
        List<SysUser> users = new ArrayList<>();
        try {
            EasyExcel.read(file.getInputStream(), SysUserExcelDTO.class,
                    new PageReadListener<SysUserExcelDTO>(dataList -> {
                        for (SysUserExcelDTO dto : dataList) {
                            SysUser user = new SysUser();
                            user.setUsername(dto.getUsername());
                            user.setRealName(dto.getRealName());
                            user.setPhone(dto.getPhone());
                            user.setEmail(dto.getEmail());
                            user.setPassword(passwordEncoder.encode("123456")); // 默认密码
                            user.setStatus(1);
                            users.add(user);
                        }
                    })).sheet().doRead();
        } catch (IOException e) {
            throw new BusinessException("文件读取失败: " + e.getMessage());
        }

        for (SysUser user : users) {
            // 跳过已存在的用户名
            long count = userMapper.selectCount(
                    new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, user.getUsername()));
            if (count == 0) {
                userMapper.insert(user);
            }
        }
        return users.size();
    }

    /**
     * 导出用户
     */
    public void exportUsers(HttpServletResponse response, String username, String realName,
                            Long orgId, Integer status) throws IOException {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StrUtil.isNotBlank(username), SysUser::getUsername, username)
                .like(StrUtil.isNotBlank(realName), SysUser::getRealName, realName)
                .eq(orgId != null, SysUser::getOrgId, orgId)
                .eq(status != null, SysUser::getStatus, status);
        List<SysUser> users = userMapper.selectList(wrapper);

        List<SysUserExcelDTO> exportList = users.stream().map(user -> {
            SysUserExcelDTO dto = new SysUserExcelDTO();
            dto.setUsername(user.getUsername());
            dto.setRealName(user.getRealName());
            dto.setPhone(user.getPhone());
            dto.setEmail(user.getEmail());
            return dto;
        }).toList();

        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        String fileName = URLEncoder.encode("用户列表", StandardCharsets.UTF_8).replaceAll("\\+", "%20");
        response.setHeader("Content-disposition", "attachment;filename*=utf-8''" + fileName + ".xlsx");
        EasyExcel.write(response.getOutputStream(), SysUserExcelDTO.class).sheet("用户列表").doWrite(exportList);
    }
}
