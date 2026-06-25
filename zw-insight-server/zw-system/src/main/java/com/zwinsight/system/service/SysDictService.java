package com.zwinsight.system.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.system.domain.SysDict;
import com.zwinsight.system.domain.SysDictItem;
import com.zwinsight.system.mapper.SysDictItemMapper;
import com.zwinsight.system.mapper.SysDictMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 数据字典服务
 */
@Service
@RequiredArgsConstructor
public class SysDictService {

    private final SysDictMapper dictMapper;
    private final SysDictItemMapper dictItemMapper;

    /**
     * 分页查询字典
     */
    public PageResult<SysDict> page(int page, int size, String dictName) {
        Page<SysDict> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<SysDict> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StrUtil.isNotBlank(dictName), SysDict::getDictName, dictName)
                .orderByAsc(SysDict::getSortOrder);
        Page<SysDict> result = dictMapper.selectPage(pageParam, wrapper);
        return PageResult.of(result);
    }

    /**
     * 根据ID查询
     */
    public SysDict getById(Long id) {
        return dictMapper.selectById(id);
    }

    /**
     * 新增字典
     */
    public void save(SysDict dict) {
        // 检查编码唯一性
        long count = dictMapper.selectCount(
                new LambdaQueryWrapper<SysDict>().eq(SysDict::getDictCode, dict.getDictCode()));
        if (count > 0) {
            throw new BusinessException("字典编码已存在");
        }
        dictMapper.insert(dict);
    }

    /**
     * 更新字典
     */
    public void update(SysDict dict) {
        SysDict existing = dictMapper.selectById(dict.getId());
        if (existing == null) {
            throw new BusinessException("字典不存在");
        }
        dictMapper.updateById(dict);
    }

    /**
     * 删除字典
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        dictMapper.deleteById(id);
        // 同时删除字典值
        dictItemMapper.delete(
                new LambdaQueryWrapper<SysDictItem>().eq(SysDictItem::getDictId, id));
    }

    /**
     * 批量删除字典
     */
    @Transactional(rollbackFor = Exception.class)
    public void batchDelete(List<Long> ids) {
        dictMapper.deleteBatchIds(ids);
        dictItemMapper.delete(
                new LambdaQueryWrapper<SysDictItem>().in(SysDictItem::getDictId, ids));
    }

    /**
     * 根据字典编码获取字典值列表（树形）
     */
    public List<SysDictItem> getDictItemsByCode(String dictCode) {
        SysDict dict = dictMapper.selectOne(
                new LambdaQueryWrapper<SysDict>().eq(SysDict::getDictCode, dictCode));
        if (dict == null) {
            throw new BusinessException("字典不存在: " + dictCode);
        }
        return dictItemMapper.selectList(
                new LambdaQueryWrapper<SysDictItem>()
                        .eq(SysDictItem::getDictId, dict.getId())
                        .orderByAsc(SysDictItem::getSortOrder));
    }
}
