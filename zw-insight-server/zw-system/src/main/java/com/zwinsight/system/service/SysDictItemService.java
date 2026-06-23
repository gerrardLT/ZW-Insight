package com.zwinsight.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.system.domain.SysDictItem;
import com.zwinsight.system.mapper.SysDictItemMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 字典值服务
 */
@Service
@RequiredArgsConstructor
public class SysDictItemService {

    private final SysDictItemMapper dictItemMapper;

    /**
     * 查询字典值列表
     */
    public List<SysDictItem> list(Long dictId) {
        return dictItemMapper.selectList(
                new LambdaQueryWrapper<SysDictItem>()
                        .eq(SysDictItem::getDictId, dictId)
                        .orderByAsc(SysDictItem::getSortOrder));
    }

    /**
     * 新增字典值
     */
    public void save(SysDictItem item) {
        if (item.getParentId() == null) {
            item.setParentId(0L);
        }
        dictItemMapper.insert(item);
    }

    /**
     * 更新字典值
     */
    public void update(SysDictItem item) {
        SysDictItem existing = dictItemMapper.selectById(item.getId());
        if (existing == null) {
            throw new BusinessException("字典值不存在");
        }
        dictItemMapper.updateById(item);
    }

    /**
     * 删除字典值
     */
    public void delete(Long id) {
        // 检查是否有子项
        long childCount = dictItemMapper.selectCount(
                new LambdaQueryWrapper<SysDictItem>().eq(SysDictItem::getParentId, id));
        if (childCount > 0) {
            throw new BusinessException("存在子字典值，无法删除");
        }
        dictItemMapper.deleteById(id);
    }
}
