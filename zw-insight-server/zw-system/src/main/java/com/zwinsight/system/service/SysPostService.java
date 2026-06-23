package com.zwinsight.system.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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
                .orderByAsc(SysPost::getSortOrder);
        Page<SysPost> result = postMapper.selectPage(pageParam, wrapper);
        return PageResult.of(result);
    }

    /**
     * 根据ID查询
     */
    public SysPost getById(Long id) {
        return postMapper.selectById(id);
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
        postMapper.deleteByIds(ids);
    }
}
