package com.zwinsight.file.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.file.domain.FileStorage;
import com.zwinsight.file.mapper.FileStorageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 存储配置服务
 */
@Service
@RequiredArgsConstructor
public class StorageService {

    private final FileStorageMapper fileStorageMapper;

    /**
     * 分页查询存储配置
     */
    public PageResult<FileStorage> page(int page, int size) {
        Page<FileStorage> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<FileStorage> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(FileStorage::getCreatedAt);
        Page<FileStorage> result = fileStorageMapper.selectPage(pageParam, wrapper);
        return PageResult.of(result);
    }

    /**
     * 根据ID查询
     */
    public FileStorage getById(Long id) {
        FileStorage storage = fileStorageMapper.selectById(id);
        if (storage == null) {
            throw new BusinessException("存储配置不存在");
        }
        return storage;
    }

    /**
     * 新增存储配置
     */
    public void save(FileStorage storage) {
        fileStorageMapper.insert(storage);
    }

    /**
     * 更新存储配置
     */
    public void update(FileStorage storage) {
        FileStorage existing = fileStorageMapper.selectById(storage.getId());
        if (existing == null) {
            throw new BusinessException("存储配置不存在");
        }
        fileStorageMapper.updateById(storage);
    }

    /**
     * 删除存储配置
     */
    public void delete(Long id) {
        fileStorageMapper.deleteById(id);
    }
}
