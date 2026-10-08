package com.zwinsight.system.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.config.SecurityContextHolder;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.security.domain.SysUser;
import com.zwinsight.security.mapper.SysUserMapper;
import com.zwinsight.system.domain.SysPost;
import com.zwinsight.system.mapper.SysPostMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 岗位管理服务
 */
@Service
@RequiredArgsConstructor
public class SysPostService {

    private final SysPostMapper postMapper;
    private final SysUserMapper userMapper;

    /** 岗位属于其他租户时按不存在处理（sys_* 免拦截器过滤，必须显式校验） */
    private SysPost requireOwned(Long id) {
        SysPost post = id == null ? null : postMapper.selectById(id);
        Long tenantId = SecurityContextHolder.getTenantId();
        if (post == null || (tenantId != null && post.getTenantId() != null && !tenantId.equals(post.getTenantId()))) {
            throw new BusinessException("岗位不存在");
        }
        return post;
    }

    private static void assertValidStatus(Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new BusinessException("状态值无效，仅支持 0（停用）或 1（启用）");
        }
    }

    /** 被人员引用的岗位不得删除，否则这些人员的岗位成为悬空引用 */
    private void assertNotReferenced(SysPost post) {
        long users = userMapper.selectCount(new LambdaQueryWrapper<SysUser>().eq(SysUser::getPostId, post.getId()));
        if (users > 0) {
            throw new BusinessException("岗位「" + post.getPostName() + "」下存在 " + users + " 名人员，无法删除");
        }
    }

    /**
     * 分页查询
     */
    public PageResult<SysPost> page(int page, int size, String postName, Integer status) {
        Page<SysPost> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<SysPost> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StrUtil.isNotBlank(postName), SysPost::getPostName, postName)
                .eq(status != null, SysPost::getStatus, status)
                // 跨租户水平越权修复（2026-08-14）：sys_* 免拦截器过滤，
                // 显式按当前租户条件化过滤（无上下文内部调用零回归）
                .eq(SecurityContextHolder.getTenantId() != null,
                        SysPost::getTenantId, SecurityContextHolder.getTenantId())
                .orderByAsc(SysPost::getSortOrder);
        Page<SysPost> result = postMapper.selectPage(pageParam, wrapper);
        return PageResult.of(result);
    }

    /**
     * 根据ID查询（含租户过滤，防跨租户 ID 枚举直查；仅 Controller 调用）
     */
    public SysPost getById(Long id) {
        return postMapper.selectOne(new LambdaQueryWrapper<SysPost>()
                .eq(SysPost::getId, id)
                .eq(SecurityContextHolder.getTenantId() != null,
                        SysPost::getTenantId, SecurityContextHolder.getTenantId()));
    }

    /**
     * 新增
     */
    public void save(SysPost post) {
        postMapper.insert(post);
    }

    /**
     * 更新
     */
    public void update(SysPost post) {
        SysPost existing = requireOwned(post.getId());
        // 白名单拷贝：tenantId/deleted/version 等系统字段不接受请求体
        if (post.getPostName() != null) existing.setPostName(post.getPostName());
        if (post.getPostCode() != null) existing.setPostCode(post.getPostCode());
        if (post.getSortOrder() != null) existing.setSortOrder(post.getSortOrder());
        if (post.getStatus() != null) {
            assertValidStatus(post.getStatus());
            existing.setStatus(post.getStatus());
        }
        postMapper.updateById(existing);
    }

    /**
     * 删除
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        assertNotReferenced(requireOwned(id));
        postMapper.deleteById(id);
    }

    /**
     * 批量删除
     */
    @Transactional(rollbackFor = Exception.class)
    public void batchDelete(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException("岗位ID列表不能为空");
        }
        // 逐个校验归属与引用，任一不满足整体回滚，避免部分删除的半成品
        for (Long id : ids) {
            assertNotReferenced(requireOwned(id));
        }
        postMapper.deleteBatchIds(ids);
    }

    /**
     * 岗位启用/停用
     */
    public void updateStatus(Long id, Integer status) {
        SysPost post = requireOwned(id);
        assertValidStatus(status);
        post.setStatus(status);
        postMapper.updateById(post);
    }
}
