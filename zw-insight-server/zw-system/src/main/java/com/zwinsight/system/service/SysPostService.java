package com.zwinsight.system.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.config.SecurityContextHolder;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
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
        SysPost existing = postMapper.selectById(post.getId());
        if (existing == null) {
            throw new BusinessException("岗位不存在");
        }
        postMapper.updateById(post);
    }

    /**
     * 删除
     */
    public void delete(Long id) {
        postMapper.deleteById(id);
    }

    /**
     * 批量删除
     */
    @Transactional(rollbackFor = Exception.class)
    public void batchDelete(List<Long> ids) {
        postMapper.deleteBatchIds(ids);
    }

    /**
     * 岗位启用/停用
     */
    public void updateStatus(Long id, Integer status) {
        SysPost post = postMapper.selectById(id);
        if (post == null) {
            throw new BusinessException("岗位不存在");
        }
        post.setStatus(status);
        postMapper.updateById(post);
    }
}
