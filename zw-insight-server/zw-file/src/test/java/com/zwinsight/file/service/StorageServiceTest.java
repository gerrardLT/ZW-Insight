package com.zwinsight.file.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.file.domain.FileStorage;
import com.zwinsight.file.mapper.FileStorageMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 存储配置服务单元测试
 */
@ExtendWith(MockitoExtension.class)
class StorageServiceTest {

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                FileStorage.class);
    }

    @Mock private FileStorageMapper fileStorageMapper;

    @InjectMocks
    private StorageService storageService;

    @Test
    @DisplayName("分页查询：返回 PageResult 结构")
    void testPage_returnsPageResult() {
        Page<FileStorage> page = new Page<>(1, 10);
        page.setRecords(List.of(new FileStorage()));
        page.setTotal(1);
        when(fileStorageMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

        PageResult<FileStorage> result = storageService.page(1, 10);

        assertThat(result.getRecords()).hasSize(1);
    }

    @Test
    @DisplayName("根据ID查询：不存在抛异常")
    void testGetById_notFound() {
        when(fileStorageMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> storageService.getById(999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("存储配置不存在");
    }

    @Test
    @DisplayName("更新：配置不存在抛异常")
    void testUpdate_notFound() {
        when(fileStorageMapper.selectById(999L)).thenReturn(null);
        FileStorage update = new FileStorage();
        update.setId(999L);

        assertThatThrownBy(() -> storageService.update(update))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("存储配置不存在");
    }

    @Test
    @DisplayName("新增/删除：透传 mapper 调用")
    void testSaveAndDelete() {
        FileStorage storage = new FileStorage();
        storageService.save(storage);
        verify(fileStorageMapper).insert(storage);

        storageService.delete(1L);
        verify(fileStorageMapper).deleteById(1L);
    }
}
