package com.zwinsight.system.service;

import cn.hutool.core.util.StrUtil;
import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.read.listener.PageReadListener;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.config.SecurityContextHolder;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.security.domain.SysUser;
import com.zwinsight.security.mapper.SysUserMapper;
import com.zwinsight.system.domain.SysOrg;
import com.zwinsight.system.domain.SysUserRole;
import com.zwinsight.system.dto.SysUserExcelDTO;
import com.zwinsight.system.mapper.SysOrgMapper;
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
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 用户管理服务
 */
@Service
@RequiredArgsConstructor
public class SysUserService {

    private final SysUserMapper userMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysOrgMapper orgMapper;
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
                // 跨租户水平越权修复（2026-08-14，探针钉住）：sys_* 表免 TenantLine
                // 拦截器过滤，此处显式按当前租户过滤；条件化写法保证无租户上下文
                // 的内部调用零回归
                .eq(SecurityContextHolder.getTenantId() != null,
                        SysUser::getTenantId, SecurityContextHolder.getTenantId())
                .orderByDesc(SysUser::getCreatedAt);
        Page<SysUser> result = userMapper.selectPage(pageParam, wrapper);
        fillOrgName(result.getRecords());
        return PageResult.of(result);
    }

    /**
     * 批量回填 orgName：实体仅持久化 orgId，列表需展示机构名称。
     * 一次批量查询 sys_org 构建 id→name 映射（避免 N+1）；sys_* 表免 TenantLine
     * 拦截器过滤，此处显式按当前租户过滤，与 page 查询口径一致。
     */
    private void fillOrgName(List<SysUser> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        Set<Long> orgIds = records.stream()
                .map(SysUser::getOrgId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (orgIds.isEmpty()) {
            return;
        }
        List<SysOrg> orgs = orgMapper.selectList(new LambdaQueryWrapper<SysOrg>()
                .in(SysOrg::getId, orgIds)
                .eq(SecurityContextHolder.getTenantId() != null,
                        SysOrg::getTenantId, SecurityContextHolder.getTenantId()));
        Map<Long, String> nameMap = orgs.stream()
                .filter(o -> o.getOrgName() != null)
                .collect(Collectors.toMap(SysOrg::getId, SysOrg::getOrgName, (a, b) -> a));
        for (SysUser user : records) {
            if (user.getOrgId() != null) {
                user.setOrgName(nameMap.get(user.getOrgId()));
            }
        }
    }

    /**
     * 根据ID查询（含租户过滤，防跨租户 ID 枚举直查；仅 Controller 调用）
     */
    public SysUser getById(Long id) {
        return userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getId, id)
                .eq(SecurityContextHolder.getTenantId() != null,
                        SysUser::getTenantId, SecurityContextHolder.getTenantId()));
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
     * <p>
     * P1 修复（2026-08-13，批次三取证枚举）：管理员（ADMIN/SUPER_ADMIN）不可被删除，
     * 防止系统失去管理员入口。
     * </p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        assertNotAdmin(id);
        userMapper.deleteById(id);
        // 删除用户角色关联
        userRoleMapper.delete(
                new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, id));
    }

    /**
     * 批量删除（管理员保护同 delete）
     */
    @Transactional(rollbackFor = Exception.class)
    public void batchDelete(List<Long> ids) {
        for (Long id : ids) {
            assertNotAdmin(id);
        }
        userMapper.deleteBatchIds(ids);
        userRoleMapper.delete(
                new LambdaQueryWrapper<SysUserRole>().in(SysUserRole::getUserId, ids));
    }

    /**
     * 管理员保护：拥有 ADMIN/SUPER_ADMIN 角色的用户不可被删除
     */
    private void assertNotAdmin(Long userId) {
        List<String> roleCodes = userMapper.selectRoleCodesByUserId(userId);
        if (roleCodes != null && (roleCodes.contains("ADMIN") || roleCodes.contains("SUPER_ADMIN"))) {
            throw new BusinessException("管理员账号不可删除");
        }
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
     * 重置密码（P2 修复：目标用户存在性+新密码非空校验，原实现 null 密码 NPE/静默写不存在用户）
     */
    public void resetPassword(Long userId, String newPassword) {
        if (newPassword == null || newPassword.isBlank()) {
            throw new BusinessException("新密码不能为空");
        }
        SysUser existing = userMapper.selectById(userId);
        if (existing == null) {
            throw new BusinessException("用户不存在");
        }
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

        int inserted = 0;
        for (SysUser user : users) {
            // 跳过已存在的用户名（增量导入语义）
            long count = userMapper.selectCount(
                    new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, user.getUsername()));
            if (count == 0) {
                userMapper.insert(user);
                inserted++;
            }
        }
        // P2 修复：返回实际插入条数（原实现含被跳过的重复用户名，计数失真）
        return inserted;
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
                .eq(status != null, SysUser::getStatus, status)
                // 跨租户水平越权修复（2026-08-14）：导出同 page 口径按租户过滤
                .eq(SecurityContextHolder.getTenantId() != null,
                        SysUser::getTenantId, SecurityContextHolder.getTenantId());
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
