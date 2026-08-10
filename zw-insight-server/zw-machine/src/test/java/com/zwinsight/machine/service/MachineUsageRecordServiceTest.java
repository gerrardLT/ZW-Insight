package com.zwinsight.machine.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.machine.domain.BizMachineUsageRecord;
import com.zwinsight.machine.mapper.BizMachineUsageRecordMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MachineUsageRecordService（机械使用记录）单元测试
 *
 * 覆盖场景:
 * - 分页查询（项目/合同筛选）
 * - 新增 / 更新的存在性校验 / 删除
 */
@ExtendWith(MockitoExtension.class)
class MachineUsageRecordServiceTest {

    @Mock
    private BizMachineUsageRecordMapper usageRecordMapper;

    @InjectMocks
    private MachineUsageRecordService machineUsageRecordService;

    @BeforeAll
    static void initTableInfo() {
        // 纯单元测试环境无 MyBatis 容器，需预初始化 Lambda 列缓存
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), BizMachineUsageRecord.class);
    }

    @Test
    @DisplayName("新增使用记录：透传 insert")
    void save_delegatesToMapper() {
        BizMachineUsageRecord record = new BizMachineUsageRecord();
        record.setProjectId(10L);

        machineUsageRecordService.save(record);

        verify(usageRecordMapper).insert(record);
    }

    @Test
    @DisplayName("更新使用记录：不存在抛异常")
    void update_notFound_throwsException() {
        BizMachineUsageRecord record = new BizMachineUsageRecord();
        record.setId(999L);
        when(usageRecordMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> machineUsageRecordService.update(record))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("机械使用记录不存在");
    }

    @Test
    @DisplayName("更新使用记录：存在则更新")
    void update_found_updates() {
        BizMachineUsageRecord existing = new BizMachineUsageRecord();
        existing.setId(1L);
        when(usageRecordMapper.selectById(1L)).thenReturn(existing);
        BizMachineUsageRecord record = new BizMachineUsageRecord();
        record.setId(1L);

        machineUsageRecordService.update(record);

        verify(usageRecordMapper).updateById(record);
    }

    @Test
    @DisplayName("删除使用记录：透传 deleteById")
    void delete_delegatesToMapper() {
        machineUsageRecordService.delete(1L);

        verify(usageRecordMapper).deleteById(1L);
    }

    @Test
    @DisplayName("分页查询：返回 PageResult")
    void page_returnsPageResult() {
        Page<BizMachineUsageRecord> stubPage = new Page<>(1, 10);
        stubPage.setTotal(2);
        when(usageRecordMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenReturn(stubPage);

        PageResult<BizMachineUsageRecord> result = machineUsageRecordService.page(1, 10, 10L, 20L);

        assertThat(result.getTotal()).isEqualTo(2);
    }
}
